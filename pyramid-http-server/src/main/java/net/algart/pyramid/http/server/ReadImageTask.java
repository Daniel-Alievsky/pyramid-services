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

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidImageCache;
import net.algart.pyramid.PlanePyramidData;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ReadImageTask {
    private static final boolean USE_GRIZZLY_TIMEOUT_FOR_RESPONSE = false;
    // - I recommend to stay it false for better control over timeouts and possible points of cancelling
    private static final long GRIZZLY_TIMEOUT = 30000;

    private static final long TIMEOUT_WAITING_IN_QUEUE_AND_READING = 30000;
    private static final long TIMEOUT_SENDING = 120000;
    // all timeouts are in milliseconds
    private static final int CHUNK_SIZE = 8192;
    private static final Logger LOG = Logger.getLogger(ReadImageTask.class.getName());

    private final Response response;
    private volatile PlanePyramid pyramid;
    private final PlanePyramidRequest pyramidRequest;
    private final ReadImageActiveTaskSet activeTaskSet;
    private final PlanePyramidImageCache cache;
    private final PlanePyramidData previousCachedData;
    private final boolean alreadyInClientCache;
    private byte[] responseBytes;
    private int responseBytesCurrentOffset;

    private volatile boolean sendingDataStarted = false;
    private volatile boolean cancelled = false;
    private volatile boolean cancelledByGrizzly = false;
    private volatile boolean closed = false;
    private volatile long lastAccessTime;

    ReadImageTask(
        Request request,
        Response response,
        PlanePyramid pyramid,
        PlanePyramidRequest pyramidRequest,
        ReadImageActiveTaskSet activeTaskSet,
        PlanePyramidImageCache cache)
    {
        this.response = Objects.requireNonNull(response);
        this.pyramid = Objects.requireNonNull(pyramid);
        this.pyramidRequest = Objects.requireNonNull(pyramidRequest);
        this.activeTaskSet = Objects.requireNonNull(activeTaskSet);
        this.cache = Objects.requireNonNull(cache);
        this.previousCachedData = pyramid.isCacheable() ? cache.get(pyramidRequest) : null;
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
            response.setContentType(
                pyramid.isRawBytes() ? "application/octet-stream" : "image/" + pyramid.format());
            refreshTimeout();
            suspendResponse();
            this.activeTaskSet.addTask(this);
        }
    }

    public boolean is304() {
        return alreadyInClientCache;
    }

    @Override
    public String toString() {
        return "ReadImageTask for request " + pyramidRequest
            + ", elapsed time=" + (System.currentTimeMillis() - lastAccessTime) + "ms";
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

    void perform() throws IOException {
        if (closed) {
            throw new IllegalStateException("Perform method must not be called after closing task");
        }
        if (checkCancellingTask("Task cancelled because of too slow waiting in queue")) {
            return;
        }
//        try {Thread.sleep(5000);} catch (InterruptedException e) {}
        final boolean cacheable = pyramid.isCacheable();
        PlanePyramidData data = previousCachedData;
        if (data == null) {
            data = pyramid.read(pyramidRequest);
            if (cacheable) {
                cache.put(pyramidRequest, data);
            }
        } else {
            LOG.info("Image loaded from cache: " + this);
            assert cacheable;
            // - checked in the constructor
        }
        this.responseBytes = data.getBytes();
        this.pyramid = null;
        // - since this moment the garbage collector can remove this pyramid, if other tasks do not use it
        if (USE_GRIZZLY_TIMEOUT_FOR_RESPONSE && cancelledByGrizzly) {
            return;
        }
        if (checkCancellingTask("Task cancelled because of too slow reading pyramid")) {
            return;
        }
        this.sendingDataStarted = true;
        this.refreshTimeout();
        LOG.fine("Sending " + responseBytes.length + " bytes...");
        this.responseBytesCurrentOffset = 0;
        response.setContentLength(responseBytes.length);
        if (cacheable) {
            response.setDateHeader("Last-Modified", data.getCreationTime());
        } else {
            response.setHeader("Cache-Control", "no-cache");
        }
        final NIOOutputStream outputStream = response.getNIOOutputStream();
        outputStream.notifyCanWrite(
            new WriteHandler() {
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
            });
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
            (sendingDataStarted ? TIMEOUT_SENDING : TIMEOUT_WAITING_IN_QUEUE_AND_READING))
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

    private void suspendResponse() {
        if (USE_GRIZZLY_TIMEOUT_FOR_RESPONSE) {
            response.suspend(GRIZZLY_TIMEOUT, TimeUnit.MILLISECONDS, new EmptyCompletionHandler<Response>() {
                @Override
                public void cancelled() {
                    cancelledByGrizzly = true;
                    LOG.log(Level.WARNING, "Response is cancelled");
                    response.setStatus(500, "Response is cancelled");
                    closeTask(true);
                }

                @Override
                public void failed(Throwable throwable) {
                    LOG.log(Level.SEVERE, "Response is failed");
                    closeTask(false);
                }
            });
        } else {
            response.suspend();
        }
    }

    private void refreshTimeout() {
        this.lastAccessTime = System.currentTimeMillis();
    }
}
