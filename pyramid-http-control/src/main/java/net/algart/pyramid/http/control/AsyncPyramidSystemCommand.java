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

import net.algart.pyramid.api.http.HttpPyramidApiTools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

class AsyncPyramidSystemCommand extends AsyncPyramidCommand {
    private static final Logger LOG = Logger.getLogger(AsyncPyramidSystemCommand.class.getName());

    private final String command;
    private final int port;
    private final Path keyFile;
    private final long timeoutStamp;
    private final long delayAfterStopInMilliseconds;
    private volatile long finishStamp;
    private volatile boolean waitingBeforeFinish = false;

    AsyncPyramidSystemCommand(
        String command,
        int port,
        Path systemCommandsFolder,
        int timeoutInMilliseconds,
        int delayAfterStopInMilliseconds
    )
        throws InvalidFileConfigurationException
    {
        Objects.requireNonNull(command, "Null command");
        Objects.requireNonNull(systemCommandsFolder, "Null systemCommandsFolder");

        this.command = command;
        this.port = port;
        this.keyFile = removeSystemCommandFile(command, port, systemCommandsFolder);
        // - remove file to be on the safe side; removing key file does not affect services
        try {
            Files.createFile(keyFile);
        } catch (FileAlreadyExistsException e) {
            // it is not a problem if a parallel process also created the same file
        } catch (IOException e) {
            throw new InvalidFileConfigurationException(e);
        }
        this.timeoutStamp = System.currentTimeMillis() + timeoutInMilliseconds;
        this.delayAfterStopInMilliseconds = delayAfterStopInMilliseconds;
    }

    void check() {
        if (isFinished()) {
            return;
        }
        if (waitingBeforeFinish) {
            if (System.currentTimeMillis() > finishStamp) {
                waitingBeforeFinish = false;
                setFinished(true);
                LOG.info(this + " was finished (command was " + (isAccepted() ? "accepted" : "ignored") + ")");
            }
            return;
        }
        if (System.currentTimeMillis() > timeoutStamp) {
            try {
                Files.deleteIfExists(keyFile);
                // - necessary to remove file even if the process did not react to it:
                // in other case, this file will lead to problems while new starting the process
            } catch (IOException ignored) {
            }
            setFinished(true);
            LOG.info(this + " was IGNORED");
            return;
        }
        final boolean accepted = !Files.exists(keyFile);
        if (accepted) {
            waitingBeforeFinish = true;
            setAccepted(true);
            this.finishStamp = System.currentTimeMillis() + delayAfterStopInMilliseconds;
            LOG.info(this + " was accepted");
            return;
        }
    }

    @Override
    public String toString() {
        return "system command " + command + ":" + port;
    }

    public static Path removeSystemCommandFile(String command, int port, Path systemCommandsFolder)
        throws InvalidFileConfigurationException
    {
        if (!Files.isDirectory(systemCommandsFolder)) {
            throw new InvalidFileConfigurationException(
                new FileNotFoundException("System command folder not found or not a directory: "
                    + systemCommandsFolder.toAbsolutePath()));
        }
        final Path keyFile = HttpPyramidApiTools.keyFile(command, port, systemCommandsFolder);
        try {
            Files.deleteIfExists(keyFile);
            // - to be on the safe side; removing key file does not affect services
        } catch (IOException ignored) {
        }
        return keyFile;
    }
}
