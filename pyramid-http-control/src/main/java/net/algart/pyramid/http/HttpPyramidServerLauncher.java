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

import net.algart.pyramid.http.api.HttpPyramidConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HttpPyramidServerLauncher {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServerLauncher.class.getName());

    private static final int SUCCESS_DELAY_IN_MS = 1000;
    private static final int PROBLEM_DELAY_IN_MS = 3000;
    private static final int PROBLEM_NUMBER_OF_ATTEMPTS = 3;
    private static final int SLOW_START_NUMBER_OF_ATTEMPTS = 10;

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

    public HttpPyramidConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Starts all processes.
     * Good for starting all service at the very beginning, for example, as OS services.
     *
     * @param skipAlreadyAlive if <tt>true</tt>, the processes that are already alive are skipped;
     *                         if not, alive processes will probably lead to exception  "cannot start process".
     */
    public void start(boolean skipAlreadyAlive) throws IOException {
        synchronized (lock) {
            for (String groupId : configuration.getProcesses().keySet()) {
                startProcess(groupId, skipAlreadyAlive);
            }
        }
    }

    public synchronized void restart(boolean restartAliveServices) {
        //TODO!! check if a service is alive (if not, repeat 3 times)
        //TODO!! if alive and restartAliveServices, call restartProcess, else startProcess
        //TODO!! start each service by a separate thread (don't delay others due to one bad services)
        synchronized (lock) {

        }
    }

    public synchronized void stop(boolean skipNotAlive) throws IOException {
        synchronized (lock) {
            for (String groupId : configuration.getProcesses().keySet()) {
                stopProcess(groupId, skipNotAlive);
            }
        }
    }

    public synchronized void restartProcess(String groupId) throws IOException {
        synchronized (lock) {
            stopProcess(groupId, false);
            startProcess(groupId, false);
        }
    }

    public synchronized void startProcess(String groupId, boolean skipIfAlive) throws IOException {
        synchronized (lock) {
            final HttpPyramidConfiguration.Process processConfiguration = configuration.getProcess(groupId);
            if (processConfiguration == null) {
                throw new IllegalArgumentException("Service group " + groupId + " not found");
            }
            final HttpPyramidProcessControl control = new HttpPyramidProcessControl("localhost", processConfiguration);
            if (skipIfAlive && control.areAllServicesAlive()) {
                return;
            }
            if (runningProcesses.get(processConfiguration.getGroupId()) != null) {
                throw new IllegalStateException("The process with groupId="
                    + processConfiguration.getGroupId() + " is already started");
            }
            Process javaProcess = null;
            boolean exited = false;
            for (int attempt = 0; ; attempt++) {
                if (attempt == 0 || exited) {
                    javaProcess = control.startAllServicesOnLocalhost();
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
                if (control.areAllServicesAlive()) {
                    // All O'k
                    runningProcesses.put(groupId, javaProcess);
                    return;
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

    public synchronized void stopProcess(String groupId, boolean skipIfNotAlive) throws IOException {
        synchronized (lock) {
            final HttpPyramidConfiguration.Process processConfiguration = configuration.getProcess(groupId);
            if (processConfiguration == null) {
                throw new IllegalArgumentException("Service group " + groupId + " not found");
            }
            final HttpPyramidProcessControl control = new HttpPyramidProcessControl("localhost", processConfiguration);
            if (skipIfNotAlive && !control.isAtLeastOneServiceAlive()) {
                return;
            }
            final Process javaProcess = runningProcesses.get(processConfiguration.getGroupId());
            for (int attempt = 0; attempt < PROBLEM_NUMBER_OF_ATTEMPTS; attempt++) {
                control.finishAllServices();
                sleep(SUCCESS_DELAY_IN_MS);
                if (javaProcess != null ?
                    !javaProcess.isAlive() :
                    !control.isAtLeastOneServiceAlive())
                {
                    return;
                }
                sleep(PROBLEM_DELAY_IN_MS);
                // waiting, maybe there was not enough time to finish all services
            }
            if (javaProcess == null) {
                throw new IOException("Cannot stop process for group " + groupId + " by HTTP");
            } else {
                if (javaProcess.isAlive()) {
                    LOG.warning("Cannot finish process for group \"" + groupId + "\" by HTTP, killing it forcibly");
                    javaProcess.destroyForcibly();
                    sleep(SUCCESS_DELAY_IN_MS);
                    // - waiting for better guarantee
                }
            }
        }
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

    private static void printWelcomeAndWaitForEnterKey() {
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

    private static void printExceptionWaitForEnterKeyAndExit(Throwable exception) {
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
            System.out.printf("    %s [--checkAlive] [--serviceMode] start|stop configurationFolder%n",
                HttpPyramidServerLauncher.class.getName());
            return;
        }
        final String command = args[startArgIndex].toLowerCase();
        final Path configurationFolder = Paths.get(args[startArgIndex + 1]);
        final HttpPyramidServerLauncher launcher = new HttpPyramidServerLauncher(
            HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder));
        try {
            switch (command) {
                case "start":
                    launcher.start(checkAlive);
                    System.out.printf("%n%d services in %d processes are running successfully%n",
                        launcher.configuration.numberOfServices(), launcher.configuration.numberOfServices());
                    break;
                case "stop":
                    launcher.stop(checkAlive);
                    System.out.printf("%n%d services in %d processes are stopped successfully%n",
                        launcher.configuration.numberOfServices(), launcher.configuration.numberOfServices());
                    break;
                default:
                    System.err.printf("Unknown command \"%s\"%n", command);
                    return;
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
