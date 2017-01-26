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

package net.algart.pyramid.http.control;

public abstract class AsyncPyramidCommand {
    public interface FinishHandler {
        void onFinish();
    }

    private static final int INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS = 200;

    private volatile boolean finished;
    private volatile boolean accepted;

    volatile FinishHandler finishHandler = null;

    AsyncPyramidCommand() {
    }

    abstract void check() throws InvalidFileConfigurationException;

    final boolean isAccepted() {
        return accepted;
    };

    final boolean isFinished() {
        return finished;
    }

    final void setFinished(boolean finished) {
        this.finished = finished;
    }

    final void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public final boolean waitFor() throws InvalidFileConfigurationException {
        while (!isFinished()) {
            try {
                Thread.sleep(INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS);
                if (isFinished()) {
                    throw new AssertionError("Concurrent change of \"finished\" state of the command " + this);
                }
                check();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (finishHandler != null) {
            finishHandler.onFinish();
        }
        return isAccepted();
    }

    public final AsyncPyramidCommand setFinishHandler(FinishHandler finishHandler) {
        this.finishHandler = finishHandler;
        return this;
    }
}
