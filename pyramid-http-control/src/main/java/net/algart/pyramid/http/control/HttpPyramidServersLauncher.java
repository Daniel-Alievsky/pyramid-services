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

import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class HttpPyramidServersLauncher {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServersLauncher.class.getName());

    private static final int SUCCESS_START_DELAY_IN_MS = 1000;
    private static final int SUCCESS_STOP_TIMEOUT_IN_MS =
        HttpPyramidConstants.SYSTEM_COMMANDS_DELAY +
            HttpPyramidConstants.SYSTEM_COMMANDS_DELAY_AFTER_FINISH +
            200;
    // - note that services and the proxy don't stop immediately, but may delay exiting during
    // someTime + SYSTEM_COMMANDS_DELAY_AFTER_FINISH ms, where someTime <= SYSTEM_COMMANDS_DELAY
    private static final int FORCIBLE_STOP_DELAY_IN_MS = 500;

    private static final int PROBLEM_DELAY_IN_MS = 3000;
    private static final int PROBLEM_NUMBER_OF_ATTEMPTS = 3;
    private static final int SLOW_START_NUMBER_OF_ATTEMPTS = 10;

    private final HttpPyramidConfiguration configuration;
    private final HttpPyramidSpecificServerConfiguration specificServerConfiguration;
    private final Map<String, Process> runningProcesses;

    public HttpPyramidServersLauncher(
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.configuration = Objects.requireNonNull(configuration, "Null configuration");
        this.specificServerConfiguration = Objects.requireNonNull(specificServerConfiguration,
            "Null specificServerConfiguration");
        this.runningProcesses = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    public HttpPyramidConfiguration getConfiguration() {
        return configuration;
    }

    public HttpPyramidSpecificServerConfiguration getSpecificServerConfiguration() {
        return specificServerConfiguration;
    }

    /**
     * Starts all pyramid processes and proxy.
     * Good for starting all service at the very beginning, for example, as OS services.
     *
     * @param skipAlreadyAlive if <tt>true</tt>, the processes, which are already alive, are skipped;
     *                         if not, alive processes will probably lead to exception  "cannot start process".
     * @return the number of processes that were actually started (can be variable if <tt>skipAlreadyAlive</tt>).
     * @throws IOException in a case of problems while starting process.
     */
    public void startAll(boolean skipAlreadyAlive) throws IOException {
        int serviceCount = 0, processCount = 0;
        for (String groupId : configuration.allGroupId()) {
            if (startPyramidServicesGroup(groupId, skipAlreadyAlive)) {
                processCount++;
                serviceCount += configuration.numberOfProcessServices(groupId);
            }
        }
        boolean proxy = false;
        if (specificServerConfiguration.hasProxy()) {
            proxy = startPyramidProxy(skipAlreadyAlive);
        }
        LOG.info(String.format("%n%d services in %d processes started, %s",
            serviceCount, processCount, proxy ? "1 proxy started" :
                specificServerConfiguration.hasProxy() ? "1 proxy FAILED" : "proxy absent"));
    }

    public AsyncPyramidCommand stopAll(boolean skipNotAlive) throws InvalidFileConfigurationException {
        final List<AsyncPyramidCommand> allSubCommands = new ArrayList<>();
        final List<AsyncPyramidCommand> serviceCommands = new ArrayList<>();
        final List<String> allGroupId = new ArrayList<>(configuration.allGroupId());
        for (String groupId : allGroupId) {
            serviceCommands.add(stopPyramidServicesGroup(groupId, skipNotAlive));
        }
        allSubCommands.addAll(serviceCommands);
        final AsyncPyramidCommand proxyCommand;
        if (specificServerConfiguration.hasProxy()) {
            proxyCommand = stopPyramidProxy(skipNotAlive);
            allSubCommands.add(proxyCommand);
        } else {
            proxyCommand = null;
        }
        return new MultipleAsyncPyramidCommand(allSubCommands).setFinishHandler(() -> {
            int serviceCount = 0, processCount = 0;
            for (int i = 0; i < serviceCommands.size(); i++) {
                if (serviceCommands.get(i).isAccepted()) {
                    processCount++;
                    serviceCount += configuration.numberOfProcessServices(allGroupId.get(i));
                }
            }
            LOG.info(String.format("%n%d services in %d processes stopped, %s",
                serviceCount, processCount, proxyCommand != null && proxyCommand.isAccepted() ?
                    "1 proxy stopped" :
                    specificServerConfiguration.hasProxy() ? "1 proxy FAILED to stop" : "proxy absent"));
        });
    }

    public AsyncPyramidCommand restartAll(boolean skipAlreadyAlive) throws IOException {
        final List<AsyncPyramidCommand> allSubCommands = new ArrayList<>();
        final List<AsyncPyramidCommand> serviceCommands = new ArrayList<>();
        final List<String> allGroupId = new ArrayList<>(configuration.allGroupId());
        for (String groupId : allGroupId) {
            serviceCommands.add(restartPyramidServicesGroup(groupId, skipAlreadyAlive));
        }
        allSubCommands.addAll(serviceCommands);
        final AsyncPyramidCommand proxyCommand;
        if (specificServerConfiguration.hasProxy()) {
            proxyCommand = restartPyramidProxy(skipAlreadyAlive);
            allSubCommands.add(proxyCommand);
        } else {
            proxyCommand = null;
        }
        return new MultipleAsyncPyramidCommand(allSubCommands).setFinishHandler(() -> {
            int serviceCount = 0, processCount = 0;
            for (int i = 0; i < serviceCommands.size(); i++) {
                if (serviceCommands.get(i).isAccepted()) {
                    processCount++;
                    serviceCount += configuration.numberOfProcessServices(allGroupId.get(i));
                }
            }
            LOG.info(String.format("%n%d services in %d processes restarted, %s",
                serviceCount, processCount, proxyCommand != null && proxyCommand.isAccepted() ?
                    "1 proxy restarted" :
                    specificServerConfiguration.hasProxy() ? "1 proxy not restarted" : "proxy absent"));
        });
    }

    public boolean startPyramidServicesGroup(String groupId, boolean skipIfAlive)
        throws IOException
    {
        final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
        return startProcess(new HttpPyramidProcessControl(
            HttpPyramidConstants.LOCAL_HOST, processConfiguration, specificServerConfiguration), skipIfAlive);
    }

    public AsyncPyramidCommand stopPyramidServicesGroup(String groupId, boolean skipIfNotAlive)
        throws InvalidFileConfigurationException
    {
        final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
        return stopProcess(new HttpPyramidProcessControl(
            HttpPyramidConstants.LOCAL_HOST, processConfiguration, specificServerConfiguration), skipIfNotAlive);
    }

    public AsyncPyramidCommand restartPyramidServicesGroup(String groupId, boolean skipIfAlive)
        throws InvalidFileConfigurationException
    {
        final HttpPyramidConfiguration.Process processConfiguration = getProcessConfiguration(groupId);
        return restartProcess(new HttpPyramidProcessControl(
            HttpPyramidConstants.LOCAL_HOST, processConfiguration, specificServerConfiguration), skipIfAlive);
    }

    public boolean startPyramidProxy(boolean skipIfAlive)
        throws IOException
    {
        return startProcess(new HttpPyramidProxyControl(
            HttpPyramidConstants.LOCAL_HOST, configuration, specificServerConfiguration), skipIfAlive);
    }

    public AsyncPyramidCommand stopPyramidProxy(boolean skipIfNotAlive)
        throws InvalidFileConfigurationException
    {
        return stopProcess(new HttpPyramidProxyControl(
            HttpPyramidConstants.LOCAL_HOST, configuration, specificServerConfiguration), skipIfNotAlive);
    }

    public AsyncPyramidCommand restartPyramidProxy(boolean skipIfAlive)
        throws InvalidFileConfigurationException
    {
        return restartProcess(new HttpPyramidProxyControl(
            HttpPyramidConstants.LOCAL_HOST, configuration, specificServerConfiguration), skipIfAlive);
    }

    private boolean startProcess(JavaProcessControl control, boolean skipIfAlive)
        throws IOException
    {
        if (skipIfAlive && control.areAllHttpServicesAlive(true)) {
            return false;
        }
        if (runningProcesses.get(control.processId()) != null) {
            throw new IllegalStateException("The process " + control.processName() + " is already started");
        }
        Process javaProcess = null;
        boolean exited = false;
        for (int attempt = 0; ; attempt++) {
            if (attempt == 0 || exited) {
                javaProcess = control.startOnLocalhost();
                // - try to start again if exited; maybe, the port was not released quickly enough
                exited = waitFor(javaProcess, SUCCESS_START_DELAY_IN_MS);
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
            if (!control.isStabilityHttpCheckAfterStartRecommended()
                || control.areAllHttpServicesAlive(true))
            {
                // All O'k
                assert javaProcess != null;
                runningProcesses.put(control.processId(), javaProcess);
                return true;
            }
            LOG.info("Cannot start process " + control.processName() + " in " + SUCCESS_START_DELAY_IN_MS / 1000.0
                + " seconds (attempt #" + (attempt + 1)
                + "); making " + PROBLEM_DELAY_IN_MS / 1000.0 + " seconds delay...");
            exited = waitFor(javaProcess, PROBLEM_DELAY_IN_MS);
            // waiting, maybe there was not enough time to start all services
        }
        final int exitValue = exited ? javaProcess.exitValue() : -1;
        javaProcess.destroy();
        if (exited) {
            throw new IOException("Cannot start process " + control.processName() + ", exit code " + exitValue);
        } else {
            throw new IOException("Process " + control.processName()
                + " launched, but services were note started; new process was killed forcibly");
        }
    }

    private AsyncPyramidCommand stopProcess(JavaProcessControl control, boolean skipIfNotAlive)
        throws InvalidFileConfigurationException
    {
        if (skipIfNotAlive && !control.isAtLeastSomeHttpServiceAlive(true)) {
            return new ImmediatePyramidCommand(false);
        }
//            System.out.println("!!!" + runningProcesses);
        final Process javaProcess = runningProcesses.remove(control.processId());
//            System.out.println(">>>" + runningProcesses);
        // - Removing is necessary for correct behaviour of the daemon thread in printWelcomeAndWaitForEnterKey
        // method. Note that we need to remove it BEFORE attempts to stop it.
        return new AsyncPyramidCommand() {
            int remainingAttemptsCount = javaProcess == null ? PROBLEM_NUMBER_OF_ATTEMPTS : 1;
            AsyncPyramidCommand subCommand = control.stopOnLocalhost(SUCCESS_STOP_TIMEOUT_IN_MS);
            volatile long sleepEndTimeStamp;
            volatile boolean delayAfterFailure = false;
            volatile boolean delayAfterDestroyForcibly = false;

            @Override
            public void check() throws InvalidFileConfigurationException {
                if (isFinished()) {
                    return;
                }
                assert !isAccepted();
                // - if accepted==true, then finished==true, but finished was checked in the beginning of the method
                if (delayAfterFailure || delayAfterDestroyForcibly) {
                    // delaying stage after failure
                    if (System.currentTimeMillis() > sleepEndTimeStamp) {
                        if (delayAfterFailure) {
                            subCommand = control.stopOnLocalhost(SUCCESS_STOP_TIMEOUT_IN_MS);
                            delayAfterFailure = false;
                            // new attempt
                        } else {
                            delayAfterDestroyForcibly = false;
                            setFinished(true);
                        }
                    }
                    return;
                }
                assert subCommand != null;
                subCommand.check();
                if (!subCommand.isFinished()) {
                    // waiting for command results
                    return;
                }
                boolean subCommandAccepted = subCommand.isAccepted();
                if (javaProcess != null ? !javaProcess.isAlive() : subCommandAccepted) {
                    setAccepted(subCommandAccepted);
                    setFinished(true);
                    return;
                }
                subCommand = null;
                remainingAttemptsCount--;
                if (remainingAttemptsCount > 0) {
                    sleepEndTimeStamp = System.currentTimeMillis() + PROBLEM_DELAY_IN_MS;
                    delayAfterFailure = true;
                    LOG.info("Cannot stop process " + control.processName() + " in "
                        + SUCCESS_STOP_TIMEOUT_IN_MS / 1000.0 + " seconds; "
                        + "making " + PROBLEM_DELAY_IN_MS / 1000.0 + " seconds delay (remaining attempts: "
                        + remainingAttemptsCount + ")...");
                    return;
                    // starting delaying stage
                }
                assert !isAccepted();
                if (javaProcess == null) {
                    LOG.warning("Cannot stop process " + control.processName() + " by system command");
                    setFinished(true);
                } else {
                    if (javaProcess.isAlive()) {
                        LOG.warning("Cannot finish process " + control.processName()
                            + " by system command, killing it FORCIBLY");
                        javaProcess.destroyForcibly();
                        sleepEndTimeStamp = System.currentTimeMillis() + FORCIBLE_STOP_DELAY_IN_MS;
                        delayAfterDestroyForcibly = true;
                        // - waiting for better guarantee
                    }
                }
            }
        };
    }

    private AsyncPyramidCommand restartProcess(JavaProcessControl control, boolean skipIfAlive)
        throws InvalidFileConfigurationException
    {
        if (skipIfAlive && control.areAllHttpServicesAlive(true)) {
            return new ImmediatePyramidCommand(false);
        }
        return new AsyncPyramidCommand() {
            AsyncPyramidCommand subCommand = stopProcess(control, false);

            @Override
            public void check() throws InvalidFileConfigurationException {
                subCommand.check();
                if (subCommand.isFinished()) {
                    if (isFinished()) {
                        throw new AssertionError("Illegal state: command not finished!");
                    }
                    final boolean successfullyStarted;
                    try {
                        successfullyStarted = startProcess(control, false);
                    } catch (IOException e) {
                        // Usually we restart processes, that were previously started;
                        // the failure of RE-starting signals about some serious problems.
                        throw new IOError(e);
                    }
                    setAccepted(successfullyStarted);
                    setFinished(true);
                }
            }
        };
    }

    private HttpPyramidConfiguration.Process getProcessConfiguration(String groupId) {
        final HttpPyramidConfiguration.Process result = configuration.getProcess(groupId);
        if (result == null) {
            throw new IllegalArgumentException("Service group " + groupId + " not found");
        }
        return result;
    }

    private void printWelcomeAndWaitForEnterKey() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // should not occur
        }
        // - to be on the side: little pause to allow services to show their logging messages
        System.out.printf("%nThe servers successfully started%n");
        System.out.printf("Press \"ENTER\" to kill the server, "
            + "or wait when it will be finished normally by system command...%n%n");
        final Thread thread = new Thread() {
            @Override
            public void run() {
                for (; ; ) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                    boolean hasAliveProcesses;
                    synchronized (runningProcesses) {
//                        System.out.println(runningProcesses);
                        hasAliveProcesses = runningProcesses.isEmpty();
                        // runningProcess can become empty as a result of direct call of stopAll
                        for (Process process : runningProcesses.values()) {
                            hasAliveProcesses |= process.isAlive();
                        }
                    }
                    if (!hasAliveProcesses) {
                        System.out.printf("%nAll servers ware stopped by external means%n");
                        System.exit(0);
                    }
                }
            }
        };
        thread.setDaemon(true);
        // - this thred must not prevent normal exiting
        thread.start();
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

    private static boolean waitFor(Process javaProcess, int timeoutInMilliseconds) {
        try {
            return javaProcess.waitFor(timeoutInMilliseconds, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        int startArgIndex = 0;
        boolean checkAlive = false, serviceMode = false, consoleWaiting = false;
        if (args.length > startArgIndex && args[startArgIndex].equals("--checkAlive")) {
            checkAlive = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].equals("--serviceMode")) {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].equals("--consoleWaiting")) {
            // for debugging needs only
            consoleWaiting = true;
            startArgIndex++;
        }
        if (args.length < startArgIndex + 3) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s [--checkAlive] [--serviceMode] [--consoleWaiting] start|stop|restart "
                    + "projectRoot specificServerConfigurationFile%n",
                HttpPyramidServersLauncher.class.getName());
            return;
        }
        final String command = args[startArgIndex].toLowerCase();
        final Path projectRoot = Paths.get(args[startArgIndex + 1]);
        final Path specificServerConfigurationFile = Paths.get(args[startArgIndex + 2]);
        final HttpPyramidServersLauncher launcher = new HttpPyramidServersLauncher(
            HttpPyramidConfiguration.readFromRootFolder(projectRoot),
            HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile));
        try {
            long t1 = System.nanoTime();
            switch (command) {
                case "start": {
                    launcher.startAll(checkAlive);
                    break;
                }
                case "stop": {
                    launcher.stopAll(checkAlive).waitFor();
                    // get() method allows to print welcome message after ACTUAL performing the command
                    break;
                }
                case "restart": {
                    launcher.restartAll(checkAlive).waitFor();
                    break;
                }
                default: {
                    System.err.printf("Unknown command \"%s\"%n", command);
                    return;
                }
            }
            long t2 = System.nanoTime();
            LOG.info(String.format(Locale.US,
                "Command \"%s\" executed in %.3f seconds%n", command, (t2 - t1) * 1e-9));
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
        if (consoleWaiting && !command.equals("stop")) {
            launcher.printWelcomeAndWaitForEnterKey();
            launcher.stopAll(false);
        }
    }
}