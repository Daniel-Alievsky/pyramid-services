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

import java.io.IOException;
import java.util.logging.Logger;

abstract class JavaProcessControl {
    private static final Logger LOG = Logger.getLogger(JavaProcessControl.class.getName());

    private static final int INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS = 200;

    public abstract String processId();

    public abstract String processName();

    /**
     * <p>If this method returns <tt>true</tt>, the process should be considered as potentially <i>unstable</i>
     * (maybe due to using native code or too complex and resource-intensive algorithms).
     * In this case, if you call {@link #startOnLocalhost()} method,
     * after this you should use HTTP checks to be sure that the process is really started.</p>
     *
     * <p>For simple and stable processes, like lightweight
     * {@link net.algart.pyramid.http.proxy.HttpPyramidProxyServer HttpPyramidProxyServer},
     * this method should return <tt>false</tt>.</p>
     *
     * @return whether additional check via HTTP is recommended after starting the process.
     */
    public abstract boolean isStabilityHttpCheckAfterStartRecommended();

    public abstract boolean areAllHttpServicesAlive(boolean logWhenFails);

    public abstract boolean isAtLeastSomeHttpServiceAlive(boolean logWhenFails);

    /**
     * <p>Starts this process with all its possible services on the current computer.
     * This method is not relevant when this class works not with localhost or its alias
     * (it still can be used for checking, are services alive on some remote host).</p>
     *
     * <p>Note: this method does not perform any waiting and just calls <tt>ProcessBuilder.start()</tt>
     * method for starting an external application.</p>
     *
     * @return OS process with newly started JVM
     * @throws IOException in a case of I/O errors
     */
    public abstract Process startOnLocalhost() throws IOException;

    /**
     * <p>Initiates finishing this process on the current computer.</p>
     *
     * <p>This method is not relevant when this class works not with localhost or its alias.</p>
     *
     * <p>This method does not wait for actual finishing.
     * You should wait for actual finishing with help of {@link AsyncPyramidCommand#waitFor()} method.</p>
     *
     * @param timeoutInMilliseconds timeout in milliseconds; if the command was not accepted in this period,
     *                              {@link AsyncPyramidCommand#isAccepted()} will return <tt>false</tt>.
     * @return {@link AsyncPyramidCommand} object allowing to wait for actual finishing the process.
     * @throws IOException in a case of disk problems. Usually this method should not throw exceptions,
     *                     besides a rare case when configuration of the file system prevents creating or
     *                     deleting any files (for example, the system folder for key files does not really exist).
     */
    public abstract AsyncPyramidCommand stopOnLocalhostCommand(int timeoutInMilliseconds) throws IOException;

    //TODO!! remove with INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS
    /*
    public boolean stopOnLocalhostAndWaitForResults(int timeoutInMilliseconds) throws IOException {
        return stopOnLocalhostCommand(timeoutInMilliseconds).waitFor();
    }

    static FutureTask<Boolean> requestSystemCommand(
        final String command,
        final int port,
        final Path systemCommandsFolder,
        final int timeoutInMilliseconds)
    {
        final Path keyFile = HttpPyramidApiTools.keyFile(systemCommandsFolder, command, port);
        final FutureTask<Boolean> futureTask = new FutureTask<>(() -> {
            if (!Files.isDirectory(systemCommandsFolder)) {
                throw new FileNotFoundException("System command folder not found or not a directory: "
                    + systemCommandsFolder.toAbsolutePath());
            }
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
                for (long t = System.currentTimeMillis();
                     System.currentTimeMillis() - t < timeoutInMilliseconds; ) {
                    try {
                        Thread.sleep(INTERVAL_OF_WAITING_SYSTEM_COMMAND_IN_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (!Files.exists(keyFile)) {
                        commandAccepted = true;
                        break;
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
            LOG.info("Command " + command + " was " + (commandAccepted ? "accepted" : "IGNORED"));
            return commandAccepted;
        });
        new Thread(futureTask).start();
        return futureTask;
    }
    */

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
