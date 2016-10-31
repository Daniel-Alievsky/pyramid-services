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
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class HttpPyramidProcessControl extends JavaProcessControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidProcessControl.class.getName());

    private final String host;
    private final HttpPyramidConfiguration.Process processConfiguration;
    private final HttpPyramidSpecificServerConfiguration specificServerConfiguration;
    private final List<HttpPyramidServiceControl> serviceControls;

    public HttpPyramidProcessControl(
        String host,
        HttpPyramidConfiguration.Process processConfiguration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.host = Objects.requireNonNull(host, "Null host");
        this.processConfiguration = Objects.requireNonNull(processConfiguration, "Null processConfiguration");
        this.specificServerConfiguration = Objects.requireNonNull(
            specificServerConfiguration, "Null specificServerConfiguration");
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

    @Override
    public String processId() {
        return processConfiguration.getGroupId();
    }

    @Override
    public String processName() {
        return "services group \"" + processId() + "\"";
    }

    @Override
    public boolean isStabilityHttpCheckAfterStartRecommended() {
        return true;
    }

    @Override
    public final boolean areAllHttpServicesAlive(boolean logWhenFails) {
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            if (!serviceControl.isServiceAlive(logWhenFails)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean isAtLeastSomeHttpServiceAlive(boolean logWhenFails) {
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            if (serviceControl.isServiceAlive(logWhenFails)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Process startOnLocalhost() throws IOException {
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
        command.add(specificServerConfiguration.getSpecificServerConfigurationFile().toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(processConfiguration.workingDirectory().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(JavaProcessControl.commandLineToString(processBuilder));
        return processBuilder.start();
    }


    @Override
    public AsyncPyramidCommand stopOnLocalhostCommand(int timeoutInMilliseconds) throws IOException {
        LOG.info("Stopping " + processName() + " on localhost...");
        final List<AsyncPyramidCommand> subTasks = new ArrayList<>();
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            subTasks.add(serviceControl.stopServiceOnLocalhostCommand(timeoutInMilliseconds));
        }
        return new MultipleAsyncPyramidCommand(subTasks);
    }
}
