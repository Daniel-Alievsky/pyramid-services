/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

final class ReadTaskQueue {
    private static final int QUEUE_MAX_SIZE = 256;
    private static final int POLL_TIMEOUT_IN_MILLISECONDS = 1000;

    private BlockingQueue<ReadTask> queue = new PriorityBlockingQueue<>(QUEUE_MAX_SIZE);

    void add(ReadTask task) {
//        System.out.println("!!!Offering " + task);
        if (!queue.offer(task)) {
            throw new IllegalStateException("Task queue is full");
        }
    }

    ReadTask pollOrNullAfterTimeout() throws InterruptedException {
        final ReadTask result = queue.poll(POLL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
//        if (result != null) System.out.println("???Polling " + result);
        return result;
        // - timeout is necessary to allow shutdown: this method should never work infinitely
    }
}