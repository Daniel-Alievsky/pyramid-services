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
import net.algart.pyramid.http.api.HttpPyramidConstants;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

public class HttpPyramidServerLauncher {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServerLauncher.class.getName());

    private final HttpPyramidConfiguration configuration;
    private final Map<String, Process> runningProcesses;

    public HttpPyramidServerLauncher(HttpPyramidConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
        this.runningProcesses = new LinkedHashMap<>();
        for (String groupId : configuration.getProcesses().keySet()) {
            runningProcesses.put(groupId, null);
        }
    }

    /**
     * Starts processes without check whether they are alive.
     * Good for starting all service at the very beginning, for example, as OS services.
     */
    public synchronized void startServices() throws IOException {
        for (String groupId : configuration.getProcesses().keySet()) {
            startProcess(groupId);
        }
    }

    public synchronized void restartServices(boolean restartAliveServices) {
        //TODO!! check if a service is alive (if not, repeat 3 times)
        //TODO!! if alive and restartAliveServices, call restartProcess, else startProcess
        //TODO!! start each service by a separate thread (don't delay others due to one bad services)
    }

    public synchronized void finishServices() {
        //TODO!! try to finish normally; if we have references to processes, also kill processes
    }

    public synchronized void restartProcess(String groupId) throws IOException {
        finishProcess(groupId);
        startProcess(groupId);
    }

    public synchronized void finishProcess(String groupId) {

        //TODO!! finish it via HTTP command, litle wait
        //TODO!! if we have reference to the process in Map<String groupId, java.lang.Process), kill it and little wait
    }

    public synchronized void startProcess(String groupId) throws IOException {
        final HttpPyramidConfiguration.Process process = configuration.getProcess(groupId);
        if (process == null) {
            throw new IllegalArgumentException("Service group " + groupId + " not found");
        }
        final Process javaProcess = launchProcess(process);
        //TODO!! try to start process 3 times (in a case of non-zero exit code) with delays and checking alive
        runningProcesses.put(groupId, javaProcess);
    }

    private synchronized Process launchProcess(HttpPyramidConfiguration.Process process) throws IOException {
        Objects.requireNonNull(process);
        if (runningProcesses.get(process.getGroupId()) != null) {
            throw new IllegalStateException("The process with groupId="
                + process.getGroupId() + " is already registered");
        }
        final Path javaPath = HttpPyramidConfiguration.getJavaExecutable(HttpPyramidConfiguration.getCurrentJREHome());
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        command.addAll(process.vmOptions());
        final String xmxOption = process.xmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        StringBuilder cp = new StringBuilder();
        for (String p : process.classPath(process.hasWorkingDirectory())) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        command.add("-cp");
        command.add(cp.toString());
        command.add(HttpPyramidConstants.DEFAULT_HTTP_PYRAMID_SERVER_CLASS_NAME);
        command.add(HttpPyramidConstants.DEFAULT_HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG);
        command.add("--groupId=" + process.getGroupId());
        command.add(configuration.getRootFolder().toAbsolutePath().toString());
        command.add(configuration.getGlobalConfigurationFile().toAbsolutePath().toString());
        for (HttpPyramidConfiguration.Service service : process.getServices()) {
            command.add(service.getConfigurationFile().toAbsolutePath().toString());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(process.workingDirectory().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(commandLineToString(processBuilder));
        return processBuilder.start();
    }

    private static String commandLineToString(ProcessBuilder processBuilder) {
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


    private static void printWelcomeAndWaitForEnterKey() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // should not occur
        }
        // - to be on the side: little pause to allow services to show their logging messages
        System.out.printf("%nThe servers successfully started");
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
        System.err.printf("%nSome problems occured while starting services! Error message:%n%s%n%nStack trace:%n",
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
        if (args.length < 1) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s [-wait] configurationFolder%n", HttpPyramidServerLauncher.class.getName());
            return;
        }
        final boolean wait = args[0].equals("-wait");
        final Path configurationFolder = Paths.get(args[wait ? 1 : 0]);
        final HttpPyramidServerLauncher launcher;
        try {
            launcher = new HttpPyramidServerLauncher(
                HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder));
            launcher.startServices();
        } catch (Exception e) {
            printExceptionWaitForEnterKeyAndExit(e);
            return;
            // - this operator will never executed
        }
        if (wait) {
            printWelcomeAndWaitForEnterKey();
            launcher.finishServices();
        }
    }
}
