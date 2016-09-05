/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Daniel Alievsky, AlgART Laboratory (http://algart.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.algart.pyramid.http.server;

import net.algart.pyramid.*;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ReadTask implements Comparable<ReadTask> {
    private static final int CHUNK_SIZE = 8192;
    private static final Logger LOG = Logger.getLogger(ReadTask.class.getName());

    private static final Lock GLOBAL_LOCK = new ReentrantLock();
    private static final AtomicLong GLOBAL_TIME_STAMP = new AtomicLong(0);

    private final Response response;
    private final PlanePyramidRequest pyramidRequest;
    private final PlanePyramidPool pyramidPool;
    private final ReadActiveTaskSet activeTaskSet;
    private final PlanePyramidDataCache cache;
    private final PlanePyramidData previousCachedData;
    private final boolean alreadyInClientCache;
    private byte[] responseBytes;
    private int responseBytesCurrentOffset;

    private final long taskCreationTimeStamp = GLOBAL_TIME_STAMP.getAndIncrement();

    private volatile boolean sendingDataStarted = false;
    private volatile boolean cancelled = false;
    private volatile boolean closed = false;
    private volatile long lastAccessTime;

    ReadTask(
        Request request,
        Response response,
        PlanePyramidRequest pyramidRequest,
        PlanePyramidPool pyramidPool,
        ReadActiveTaskSet activeTaskSet,
        PlanePyramidDataCache cache)
    {
        this.response = Objects.requireNonNull(response);
        this.pyramidRequest = Objects.requireNonNull(pyramidRequest);
        this.pyramidPool = Objects.requireNonNull(pyramidPool);
        this.activeTaskSet = Objects.requireNonNull(activeTaskSet);
        this.cache = Objects.requireNonNull(cache);
        this.previousCachedData = cache.get(pyramidRequest);
        final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
        this.alreadyInClientCache =
            previousCachedData != null
                && ifModifiedSince != -1
                && previousCachedData.getCreationTime() < ifModifiedSince + 1000;
        // the 2nd check is to be on the safe side: usually these data are never modified
        if (alreadyInClientCache) {
            LOG.info("Not modified (304): " + this);
            response.setStatus(304, "Not modified");
        } else {
            refreshTimeout();
            this.response.suspend();
            this.activeTaskSet.addTask(this);
        }
    }

    public boolean is304() {
        return alreadyInClientCache;
    }

    @Override
    public String toString() {
        return "ReadTask for request " + pyramidRequest
            + ", elapsed time=" + (System.currentTimeMillis() - lastAccessTime) + "ms"
            + " (priority " + pyramidRequest.priority() + ", timestamp " + taskCreationTimeStamp + ")";
    }

    // Equals and hashCode must be standard, for correct work of the set of active tasks
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public int compareTo(ReadTask o) {
        final int p1 = pyramidRequest.priority();
        final int p2 = o.pyramidRequest.priority();
        if (p1 != p2) {
            return p1 > p2 ? -1 : 1;
        }
        return taskCreationTimeStamp < o.taskCreationTimeStamp ? -1 :
            taskCreationTimeStamp > o.taskCreationTimeStamp ? 1 : 0;
    }

    void perform() throws Exception {
        if (closed) {
            throw new IllegalStateException("Perform method must not be called after closing task");
        }
        if (checkCancellingTask("Task cancelled because of too slow waiting in queue")) {
            return;
        }
        LOG.info("Starting " + this);
        PlanePyramidData data = previousCachedData;
        final boolean cacheable;
        if (data == null) {
            final boolean savingMemoryMode = pyramidRequest.isSavingMemoryMode();
            if (savingMemoryMode) {
                GLOBAL_LOCK.lock();
            }
            try {
                final String pyramidUniqueId = pyramidRequest.getPyramidUniqueId();
//                try {Thread.sleep(5000);} catch (InterruptedException e) {}
                final PlanePyramid pyramid = pyramidPool.getHttpPlanePyramid(pyramidUniqueId, savingMemoryMode);
                cacheable = pyramid.isCacheable();
                data = pyramid.read(pyramidRequest);
                if (cacheable) {
                    cache.put(pyramidRequest, data);
                }
            } finally {
                if (savingMemoryMode) {
                    GLOBAL_LOCK.unlock();
                }
            }
        } else {
            // Note: we don't access pyramid at all if the data are already in cache
            LOG.info("Data loaded from cache: " + this);
            cacheable = true;
            // Obviously, if the data appeared in cache, the pyramid was cacheable
        }
        if (checkCancellingTask("Task cancelled because of too slow reading pyramid")) {
            return;
        }
        response.setContentType(data.getContentMIMEType());
        if (cacheable) {
            response.setDateHeader("Last-Modified", data.getCreationTime());
        } else {
            response.setHeader("Cache-Control", "no-cache");
        }
        if (data.isShortString()) {
            response.setStatus(200, "OK");
            response.getWriter().write(data.getShortString());
            // - note that the correct charset here is specified by data.getContentMIMEType()
            closeTask(false);
            return;
        }
        this.responseBytes = data.getBytes();
        this.sendingDataStarted = true;
        this.refreshTimeout();
        LOG.fine("Sending " + responseBytes.length + " bytes...");
        this.responseBytesCurrentOffset = 0;
        response.setContentLength(responseBytes.length);
        response.getNIOOutputStream().notifyCanWrite(new ReadTaskWriteHandler(response.getNIOOutputStream()));
    }

    private boolean checkCancellingTask(String msg) {
        if (cancelled) {
            LOG.log(Level.WARNING, msg);
            response.setStatus(500, msg);
            closeTask(true);
            return true;
        }
        return false;
    }

    void cancelIfObsolete() {
//        System.out.println("Task " + this + " time " + (System.currentTimeMillis() - startTimeInMilliseconds));
        if (System.currentTimeMillis() - lastAccessTime >
            (sendingDataStarted ?
                HttpPyramidConstants.SERVER_SENDING_TIMEOUT :
                HttpPyramidConstants.SERVER_WAITING_IN_QUEUE_AND_READING_TIMEOUT))
        {
            activeTaskSet.removeTask(this);
            // the same action is done while closeTask, but we have no strict guarantees
            // that closeTask will be called
            this.cancelled = true;
        }
    }

    void cancelTaskOnException(Throwable t) {
        LOG.log(Level.WARNING, "Error while reading image", t);
        response.setStatus(500, "Error while reading image");
        closeTask(cancelled);
    }

    private void closeTask(boolean cancelled) {
        activeTaskSet.removeTask(this);
        if (cancelled) {
            LOG.info("Cancelling response");
        }
        if (response.isSuspended()) {
            response.resume();
            LOG.info("Response is resumed: " + this);
        } else {
            response.finish();
            LOG.info("Response is finished: " + this);
        }
        closed = true;
    }

    private void refreshTimeout() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    private class ReadTaskWriteHandler implements WriteHandler {
        private final NIOOutputStream outputStream;

        public ReadTaskWriteHandler(NIOOutputStream outputStream) {
            this.outputStream = Objects.requireNonNull(outputStream);
        }

        @Override
        public void onWritePossible() throws IOException {
            if (checkCancellingDueToTimeout() || checkCancellingDueToResponseState()) {
                return;
            }
            refreshTimeout();
//                    try {Thread.sleep(1000);} catch (InterruptedException e) {}
            final int len = Math.min(responseBytes.length - responseBytesCurrentOffset, CHUNK_SIZE);
//                    System.out.printf("%d/%d bytes sending...%n", responseBytesCurrentOffset, responseBytes.length);
            if (len > 0) {
                outputStream.write(responseBytes, responseBytesCurrentOffset, len);
                responseBytesCurrentOffset += len;
            }
            if (responseBytesCurrentOffset >= responseBytes.length) {
                closeHandler(false);
            } else {
                outputStream.notifyCanWrite(this);
            }
        }

        @Override
        public void onError(Throwable e) {
            LOG.log(Level.FINE, "Some problems occur while writing output stream", e);
            // - it is not too serious problem: maybe connection was lost or terminated by browser
            response.setStatus(500, "Error while writing output stream");
            try {
                outputStream.close();
            } catch (IOException ex) {
                LOG.log(Level.FINE, "Error while closing output stream", ex);
                // only FINE: this exception can occur if the browser terminates connection;
                // no sense to duplicate messages - an attempt to write will also lead to an exception
            }
            closeTask(false);
        }

        private boolean checkCancellingDueToTimeout() throws IOException {
            if (cancelled) {
                LOG.info("Timeout: response is cancelled while sending data");
                response.setStatus(500, "Task cancelled while sending data");
                closeHandler(true);
                return true;
            } else {
                return false;
            }
        }

        private boolean checkCancellingDueToResponseState() throws IOException {
            if (!response.isSuspended()) {
                // to be on the safe side: should not occur
                LOG.warning("Timeout: response is resumed/cancelled while sending data");
                response.setStatus(500, "Task cancelled while sending data");
                closeHandler(true);
                return true;
            }
            return false;
        }

        private void closeHandler(boolean cancelled) throws IOException {
            outputStream.close();
            // - stream must be closed before closeTask to avoid exceptions
            closeTask(cancelled);
        }
    }
}
