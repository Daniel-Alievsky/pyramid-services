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

import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class HttpPyramidServerLauncher {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServerLauncher.class.getName());

    private static final int SUCCESS_DELAY_IN_MS = 1000;
    private static final int PROBLEM_DELAY_IN_MS = 3000;
    private static final int PROBLEM_NUMBER_OF_ATTEMPTS = 3;
    private static final int SLOW_START_NUMBER_OF_ATTEMPTS = 15;

    private final HttpPyramidConfiguration configuration;
    private final Map<String, Process> runningProcesses;

    private final Object lock = new Object();

    public HttpPyramidServerLauncher(HttpPyramidConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
        this.runningProcesses = new LinkedHashMap<>();
        for (String groupId : configuration.getProcesses().keySet()) {
            runningProcesses.put(groupId, null);
        }
    }

    public HttpPyramidConfiguration getProcessConfiguration() {
        return configuration;
    }

    /**
     * Starts all processes.
     * Good for starting all service at the very beginning, for example, as OS services.
     *
     * @param skipAlreadyAlive if <tt>true</tt>, the processes that are already alive are skipped;
     *                         if not, alive processes will probably lead to exception  "cannot start process".
     * @return the number of processes that were actually started (can be variable if <tt>skipAlreadyAlive</tt>)
     * @throws IOException in a case of problems while starting process
     */
    public void start(boolean skipAlreadyAlive) throws IOException {
        int serviceCount = 0, processCount = 0;
        synchronized (lock) {
            for (String groupId : configuration.allGroupId()) {
                if (startProcess(groupId, skipAlreadyAlive)) {
                    processCount++;
                    serviceCount += configuration.numberOfProcessServices(groupId);
                }
            }
        }
        LOG.info(String.format("%n%d services in %d processes started", serviceCount, processCount));
    }

    public void restart(boolean skipAliveServices) throws IOException {
        int serviceCount = 0, processCount = 0;
        synchronized (lock) {
            for (String groupId : configuration.allGroupId()) {
                if (restartProcess(groupId, skipAliveServices)) {
                    processCount++;
                    serviceCount += configuration.numberOfProcessServices(groupId);
                }
            }
        }
        LOG.info(String.format("%n%d services in %d processes restarted", serviceCount, processCount));
    }

    public void stop(boolean skipNotAlive) throws IOException {
        int serviceCount = 0, processCount = 0;
        synchronized (lock) {
            for (String groupId : configuration.allGroupId()) {
                if (stopProcess(groupId, skipNotAlive)) {
                    processCount++;
                    serviceCount += configuration.numberOfProcessServices(groupId);
                }
            }
        }
        LOG.info(String.format("%n%d services in %d processes stopped", serviceCount, processCount));
    }

    public boolean startProcess(String groupId, boolean skipIfAlive) throws IOException {
        synchronized (lock) {
            final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
            final HttpPyramidProcessControl control = new HttpPyramidProcessControl(
                HttpPyramidConstants.LOCAL_HOST, processConfiguration);
            if (skipIfAlive && control.areAllHttpServicesAlive(true)) {
                return false;
            }
            if (runningProcesses.get(control.getProcessId()) != null) {
                throw new IllegalStateException("The process with groupId="
                    + control.getProcessId() + " is already started");
            }
            Process javaProcess = null;
            boolean exited = false;
            for (int attempt = 0; ; attempt++) {
                if (attempt == 0 || exited) {
                    javaProcess = control.startOnLocalhost();
                    // - try to start again if exited; maybe, the port was not released quickly enough
                    exited = waitFor(javaProcess, SUCCESS_DELAY_IN_MS);
                    // - waiting to allow the process to really start Web servers
                }
                if (exited) {
                    LOG.warning("Unexpected exit of the process with exit code " + javaProcess.exitValue());
                    javaProcess.destroy();
                    if (attempt >= PROBLEM_NUMBER_OF_ATTEMPTS) {
                        break;
                    } else {
                        continue;
                    }
                }
                if (attempt >= SLOW_START_NUMBER_OF_ATTEMPTS) {
                    break;
                }
                if (control.areAllHttpServicesAlive(true)) {
                    // All O'k
                    runningProcesses.put(groupId, javaProcess);
                    return true;
                }
                exited = waitFor(javaProcess, PROBLEM_DELAY_IN_MS);
                // waiting, maybe there was not enough time to start all services
            }
            final int exitValue = exited ? javaProcess.exitValue() : -1;
            javaProcess.destroy();
            if (exited) {
                throw new IOException("Cannot start process for group " + groupId + ", exit code " + exitValue);
            } else {
                throw new IOException("Process for group " + groupId
                    + " launched, but services were note started");
            }
        }
    }

    public boolean stopProcess(String groupId, boolean skipIfNotAlive) throws IOException {
        synchronized (lock) {
            final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
            final HttpPyramidProcessControl control = new HttpPyramidProcessControl(
                HttpPyramidConstants.LOCAL_HOST, processConfiguration);
            if (skipIfNotAlive && !control.isAtLeastSomeHttpServiceAlive(true)) {
                return false;
            }
            final Process javaProcess = runningProcesses.get(control.getProcessId());
            for (int attempt = 0; attempt < PROBLEM_NUMBER_OF_ATTEMPTS; attempt++) {
                control.stopOnLocalhost();
                sleep(SUCCESS_DELAY_IN_MS);
                if (javaProcess != null ?
                    !javaProcess.isAlive() :
                    !control.isAtLeastSomeHttpServiceAlive(false))
                {
                    return true;
                }
                sleep(PROBLEM_DELAY_IN_MS);
                // waiting, maybe there was not enough time to finish all services
            }
            if (javaProcess == null) {
                throw new IOException("Cannot stop process for group " + groupId + " by system command");
            } else {
                if (javaProcess.isAlive()) {
                    LOG.warning("Cannot finish process for group \"" + groupId
                        + "\" by system command, killing it forcibly");
                    javaProcess.destroyForcibly();
                    sleep(SUCCESS_DELAY_IN_MS);
                    // - waiting for better guarantee
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    public boolean restartProcess(String groupId, boolean skipIfAlive) throws IOException {
        synchronized (lock) {
            final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
            final HttpPyramidProcessControl control = new HttpPyramidProcessControl(
                HttpPyramidConstants.LOCAL_HOST, processConfiguration);
            if (skipIfAlive && control.areAllHttpServicesAlive(true)) {
                return false;
            }
            stopProcess(groupId, false);
            startProcess(groupId, false);
            return true;
        }
    }

    private HttpPyramidConfiguration.Process getProcessConfiguration(String groupId) {
        final HttpPyramidConfiguration.Process resyult = configuration.getProcess(groupId);
        if (resyult == null) {
            throw new IllegalArgumentException("Service group " + groupId + " not found");
        }
        return resyult;
    }

    private static boolean waitFor(Process javaProcess, int timeoutInMilliseconds) {
        try {
            return javaProcess.waitFor(timeoutInMilliseconds, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleep(int timeoutInMilliseconds) {
        try {
            Thread.sleep(timeoutInMilliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void printWelcomeAndWaitForEnterKey() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // should not occur
        }
        // - to be on the side: little pause to allow services to show their logging messages
        System.out.printf("%nThe servers successfully started%n");
        System.out.printf("Press \"Ctrl+C\" or \"ENTER\" to kill the server, "
            + "or wait when it will be finished normally by HTTP command...%n%n");
        try {
            System.in.read();
        } catch (IOException e) {
            // should not occur
            e.printStackTrace();
        }
    }

    static void printExceptionWaitForEnterKeyAndExit(Throwable exception) {
        System.err.printf("%nSome problems occured! Error message:%n%s%n%nStack trace:%n",
            exception.getMessage());
        exception.printStackTrace();
        System.err.printf("%nPress \"ENTER\" to exit...%n");
        try {
            System.in.read();
        } catch (IOException e) {
            // should not occur
            e.printStackTrace();
        }

        System.exit(1);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        int startArgIndex = 0;
        boolean checkAlive = false, serviceMode = false, debuggingWait = false;
        if (args.length > startArgIndex && args[startArgIndex].equals("--checkAlive")) {
            checkAlive = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].equals("--serviceMode")) {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].equals("--debuggingWait")) {
            // for debugging needs only
            debuggingWait = true;
            startArgIndex++;
        }
        if (args.length < startArgIndex + 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s [--checkAlive] [--serviceMode] start|stop|restart configurationFolder%n",
                HttpPyramidServerLauncher.class.getName());
            return;
        }
        final String command = args[startArgIndex].toLowerCase();
        final Path configurationFolder = Paths.get(args[startArgIndex + 1]);
        final HttpPyramidServerLauncher launcher = new HttpPyramidServerLauncher(
            HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder));
        try {
            switch (command) {
                case "start": {
                    launcher.start(checkAlive);
                    break;
                }
                case "stop": {
                    launcher.stop(checkAlive);
                    break;
                }
                case "restart": {
                    launcher.restart(checkAlive);
                    break;
                }
                default: {
                    System.err.printf("Unknown command \"%s\"%n", command);
                    return;
                }
            }
        } catch (Exception e) {
            if (serviceMode) {
                e.printStackTrace();
                System.exit(1);
            } else {
                printExceptionWaitForEnterKeyAndExit(e);
            }
            return;
            // - this operator will never executed
        }
        if (debuggingWait) {
            printWelcomeAndWaitForEnterKey();
            launcher.stop(false);
        }
    }
}
