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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class HttpPyramidProcessControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidProcessControl.class.getName());

    private final String host;
    private final HttpPyramidConfiguration.Process processConfiguration;
    private final List<HttpPyramidServiceControl> serviceControls;

    public HttpPyramidProcessControl(String host, HttpPyramidConfiguration.Process processConfiguration) {
        this.host = Objects.requireNonNull(host, "Null host");
        this.processConfiguration = Objects.requireNonNull(processConfiguration, "Null processConfiguration");
        this.serviceControls = new ArrayList<>();
        for (HttpPyramidConfiguration.Service service : processConfiguration.getServices()) {
            this.serviceControls.add(new HttpPyramidServiceControl(host, service));
        }
    }

    public String getHost() {
        return host;
    }

    public HttpPyramidConfiguration.Process getProcessConfiguration() {
        return processConfiguration;
    }

    public final boolean areAllServicesAlive(boolean logWhenFails) {
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            if (!serviceControl.isServiceAlive(logWhenFails)) {
                return false;
            }
        }
        return true;
    }

    public final boolean isAtLeastOneServiceAlive(boolean logWhenFails) {
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            if (serviceControl.isServiceAlive(logWhenFails)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Starts this process with all its services on the current computer.
     * This method is not relevant when {@link #getHost()} is not a localhost or its alias.
     *
     * @return OS process with newly started JVM
     * @throws IOException in a case of I/O errors
     */
    public Process startAllServicesOnLocalhost() throws IOException {
        final HttpPyramidConfiguration configuration = processConfiguration.parentConfiguration();
        final Path javaPath = HttpPyramidConfiguration.getJavaExecutable(HttpPyramidConfiguration.getCurrentJREHome());
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        command.addAll(processConfiguration.vmOptions());
        final String xmxOption = processConfiguration.xmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        StringBuilder cp = new StringBuilder();
        for (String p : processConfiguration.classPath(processConfiguration.hasWorkingDirectory())) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        command.add("-cp");
        command.add(cp.toString());
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVER_CLASS_NAME);
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG);
        command.add("--groupId=" + processConfiguration.getGroupId());
        command.add(configuration.getRootFolder().toAbsolutePath().toString());
        command.add(configuration.getGlobalConfigurationFile().toAbsolutePath().toString());
        for (HttpPyramidConfiguration.Service service : processConfiguration.getServices()) {
            command.add(service.getConfigurationFile().toAbsolutePath().toString());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(processConfiguration.workingDirectory().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(commandLineToString(processBuilder));
        return processBuilder.start();
    }

    public void finishAllServices() throws IOException {
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            serviceControl.finishService();
        }
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
}
