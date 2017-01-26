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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MultipleAsyncPyramidCommand extends AsyncPyramidCommand {
    private List<AsyncPyramidCommand> commands;

    public MultipleAsyncPyramidCommand(Collection<AsyncPyramidCommand> commands) {
        this.commands = new ArrayList<>(commands);
    }

    @Override
    void check() throws InvalidFileConfigurationException {
        for (AsyncPyramidCommand command : commands) {
            if (!command.isFinished()) {
                // It is possible that some sub-commands are finished and some are not finished;
                // in this case, this mechod check() is permitted to be called (in waitFor() method).
                // But we must not call check() for already finished commands: it can lead to errors.
                command.check();
            }
        }
        boolean allAccepted = true;
        boolean allFinished = true;
        for (AsyncPyramidCommand command : commands) {
            allAccepted &= command.isAccepted();
            allFinished &= command.isFinished();
        }
        setAccepted(allAccepted);
        setFinished(allFinished);
    }

    @Override
    public String toString() {
        return "multiple command " + commands;
    }
}
