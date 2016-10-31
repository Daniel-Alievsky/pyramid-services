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

import java.io.IOException;

abstract class JavaProcessControl {
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
    public abstract AsyncPyramidCommand stopOnLocalhost(int timeoutInMilliseconds) throws IOException;

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
