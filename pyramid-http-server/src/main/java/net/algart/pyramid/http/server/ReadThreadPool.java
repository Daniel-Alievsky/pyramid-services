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

import net.algart.pyramid.PlanePyramidDataCache;
import net.algart.pyramid.PlanePyramidPool;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.util.logging.Level;
import java.util.logging.Logger;

final class ReadThreadPool {
    private static final Logger LOG = Logger.getLogger(ReadThreadPool.class.getName());
    private static final PlanePyramidDataCache PLANE_PYRAMID_IMAGE_CACHE =
        new PlanePyramidDataCache(HttpPyramidConstants.IMAGE_CACHING_MEMORY);

    private final ReadTaskQueue queue;
    private final ReadActiveTaskSet activeTaskSet;
    private final PlanePyramidDataCache imageCache;
    private final Thread[] threads;
    private final Thread cleaningThread;
    private volatile boolean shutdown = false;

    ReadThreadPool(int poolSize) {
        this.queue = new ReadTaskQueue();
        this.activeTaskSet = new ReadActiveTaskSet();
        this.imageCache = PLANE_PYRAMID_IMAGE_CACHE;
        // - using global cache for the process
        this.threads = new Thread[poolSize];
        for (int k = 0; k < threads.length; k++) {
            threads[k] = new ReadImageThread();
            threads[k].start();
            LOG.info("Starting make-image thread #" + k);
        }
        this.cleaningThread = new CleaningTasksThread();
        this.cleaningThread.start();
    }

    boolean createReadTask(
        Request request,
        Response response,
        PlanePyramidRequest pyramidRequest,
        PlanePyramidPool pyramidPool
    ) {
        final ReadTask task = new ReadTask(
            request, response, pyramidRequest, pyramidPool, activeTaskSet, imageCache);
        final boolean result = !task.is304();
        if (result) {
            queue.add(task);
        }
        return result;
    }

    void shutdown() {
        this.shutdown = true;
    }

    private class ReadImageThread extends Thread {
        @Override
        public void run() {
            while (!shutdown) {
                final ReadTask task;
                try {
                    LOG.fine("Waiting for new task...");
                    task = queue.pollOrNullAfterTimeout();
                    if (task == null) {
                        continue;
                        // Continue waiting for new task or exiting if shutdown.
                        // Timeout is necessary to allow shutdown.
                    }
                    LOG.fine("Taking " + task);
                } catch (InterruptedException e) {
                    // Ignore this: the only legal way for stopping is shutdown() method
                    continue;
                } catch (Throwable e) {
                    // In this very improbable case we need to keep this thread:
                    // errors must not lead to stopping threads, in other case
                    // the server will stop respond at all
                    LOG.log(Level.SEVERE, "Unexpected error in logic!", e);
                    continue;
                }
                try {
                    task.perform();
                } catch (Throwable t) {
                    task.cancelTaskOnException(t);
                }
            }
        }
    }

    private class CleaningTasksThread extends Thread {
        @Override
        public void run() {
            while (!shutdown) {
                try {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // nothing to do
                    }
                    activeTaskSet.cleanObsoleteTasks();
                } catch (Throwable e) {
                    // In this very improbable case we keep this thread
                    LOG.log(Level.SEVERE, "Unexpected error in logic!", e);
                }
            }
        }
    }
}
