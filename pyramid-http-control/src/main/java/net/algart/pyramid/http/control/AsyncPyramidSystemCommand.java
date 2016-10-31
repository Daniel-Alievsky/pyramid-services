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

    AsyncPyramidSystemCommand(String command, int port, Path systemCommandsFolder, int timeoutInMilliseconds)
        throws IOException
    {
        Objects.requireNonNull(command, "Null command");
        Objects.requireNonNull(systemCommandsFolder, "Null systemCommandsFolder");

        this.command = command;
        this.port = port;
        this.keyFile = HttpPyramidApiTools.keyFile(systemCommandsFolder, command, port);
        if (!Files.isDirectory(systemCommandsFolder)) {
            throw new FileNotFoundException("System command folder not found or not a directory: "
                + systemCommandsFolder.toAbsolutePath());
        }
        try {
            Files.deleteIfExists(keyFile);
            // - to be on the safe side; removing key file does not affect services
        } catch (IOException ignored) {
        }
        try {
            Files.createFile(keyFile);
        } catch (FileAlreadyExistsException e) {
            // it is not a problem if a parallel process also created the same file
        }
        this.timeoutStamp = System.currentTimeMillis() + timeoutInMilliseconds;
    }

    void check() {
        if (!isFinished()) {
            setAccepted(!Files.exists(keyFile));
        }
        if (isAccepted() || System.currentTimeMillis() >= timeoutStamp) {
            setFinished(true);
            if (!isAccepted()) {
                try {
                    Files.deleteIfExists(keyFile);
                    // - necessary to remove file even if the process did not react to it:
                    // in other case, this file will lead to problems while new starting that process
                } catch (IOException ignored) {
                }
            }
            LOG.info(this + " was " + (isAccepted() ? "accepted" : "IGNORED"));
        }
    }

    @Override
    public String toString() {
        return "system command " + command + ":" + port;
    }
}
