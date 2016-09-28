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

package net.algart.pyramid.http;

import net.algart.pyramid.api.http.HttpPyramidApiTools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

interface JavaProcessControlWithHttpCheckingAliveStatus {
    int INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS = 200;

    String processId();

    String processName();

    boolean areAllHttpServicesAlive(boolean logWhenFails);

    boolean isAtLeastSomeHttpServiceAlive(boolean logWhenFails);

    /**
     * Starts this process with all its possible services on the current computer.
     * This method is not relevant when this class works not with localhost or its alias
     * (it still can be used for checking, are services alive on some remote host).
     *
     * @return OS process with newly started JVM
     * @throws IOException in a case of I/O errors
     */
    Process startOnLocalhost() throws IOException;

    /**
     * Finishes this process on the current computer.
     * This method is not relevant when this class works not with localhost or its alias.
     *
     * @param timeoutInMilliseconds timeout in milliseconds; if the command was not accepted in this period,
     *                              the method returns <tt>false</tt>.
     * @return <tt>true</tt> if no problems were detected.
     */
    boolean stopOnLocalhost(int timeoutInMilliseconds) throws IOException;

    static boolean requestSystemCommand(
        String commandPrefix,
        int port,
        Path systemCommandsFolder,
        int timeoutInMilliseconds)
        throws IOException
    {
        if (!Files.isDirectory(systemCommandsFolder)) {
            throw new FileNotFoundException("System command folder not found or not a directory: "
                + systemCommandsFolder.toAbsolutePath());
        }
        final Path keyFile = HttpPyramidApiTools.keyFile(systemCommandsFolder, commandPrefix, port);
        try {
            Files.deleteIfExists(keyFile);
            // - to be on the safe side; removing key file does not affect services
        } catch (IOException ignored) {
        }
        boolean commandAccepted = false;
        try {
            Files.createFile(keyFile);
        } catch (FileAlreadyExistsException e) {
            // it is not a problem if a parallel process also created the same file
        }
        try {
            for (long t = System.currentTimeMillis(); System.currentTimeMillis() - t < timeoutInMilliseconds; ) {
                try {
                    Thread.sleep(INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (!Files.exists(keyFile)) {
                    commandAccepted = true;
                }
            }
        } finally {
            if (!commandAccepted) {
                try {
                    Files.deleteIfExists(keyFile);
                    // - necessary to remove file even if the process did not react to it:
                    // in other case, this file will lead to problems while new starting that process
                } catch (IOException ignored) {
                }
            }
        }
        return commandAccepted;
    }

    static String commandLineToString(ProcessBuilder processBuilder) {
        final StringBuilder sb = new StringBuilder();
        sb.append(processBuilder.directory() + "> ");
        for (final String command : processBuilder.command()) {
            if (command.contains(" ") || command.length() == 0) {
                sb.append("\"" + command + "\" ");
            } else {
                sb.append(command + " ");
            }
        }
        return sb.toString();
    }
}
