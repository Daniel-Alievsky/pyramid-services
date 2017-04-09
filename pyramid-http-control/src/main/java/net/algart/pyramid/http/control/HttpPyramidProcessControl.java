/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

import net.algart.pyramid.api.common.IllegalJREException;
import net.algart.pyramid.api.common.PyramidServicesConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpServerConfiguration;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public final class HttpPyramidProcessControl extends JavaProcessControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidProcessControl.class.getName());

    private final String host;
    private final PyramidServicesConfiguration.Process processConfiguration;
    private final HttpServerConfiguration serverConfiguration;
    private final List<HttpPyramidServiceControl> serviceControls;

    public HttpPyramidProcessControl(
        String host,
        PyramidServicesConfiguration.Process processConfiguration,
        HttpServerConfiguration serverConfiguration)
    {
        this.host = Objects.requireNonNull(host, "Null host");
        this.processConfiguration = Objects.requireNonNull(processConfiguration, "Null processConfiguration");
        this.serverConfiguration = Objects.requireNonNull(serverConfiguration, "Null serverConfiguration");
        this.serviceControls = new ArrayList<>();
        for (PyramidServicesConfiguration.Service service : processConfiguration.getServices()) {
            this.serviceControls.add(new HttpPyramidServiceControl(host, service));
        }
    }

    public String getHost() {
        return host;
    }

    public PyramidServicesConfiguration.Process getProcessConfiguration() {
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
    public Process startOnLocalhost() throws InvalidFileConfigurationException {
        final PyramidServicesConfiguration servicesConfiguration = processConfiguration.parentConfiguration();
        final Path javaPath;
        try {
            javaPath = serverConfiguration.javaExecutable(processConfiguration.jreName());
        } catch (IllegalJREException e) {
            throw new InvalidFileConfigurationException(e);
        }
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        final String xmxOption = processConfiguration.xmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        command.addAll(processConfiguration.vmOptions());
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
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVICE_MODE_FLAG);
        command.add("--groupId=" + processConfiguration.getGroupId());
        command.add(servicesConfiguration.getProjectRoot().toAbsolutePath().toString());
        command.add(servicesConfiguration.getGlobalConfigurationFile().toAbsolutePath().toString());
        for (PyramidServicesConfiguration.Service service : processConfiguration.getServices()) {
            command.add(service.getConfigurationFile().toAbsolutePath().toString());
        }
        command.add(serverConfiguration.getServerConfigurationFile().toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(processConfiguration.workingDirectory().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(JavaProcessControl.commandLineToString(processBuilder));
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            serviceControl.removeFinishSystemCommandFile();
            // - the key files must be removed BEFORE attempt to start new process,
            // for a case if it was kept from the previous failed attemt to stop the process
        }
        try {
            return processBuilder.start();
        } catch (IOException e) {
            // Impossibility to start current Java (java.exe) is a serious system problem!
            throw new IOError(e);
        }
    }


    @Override
    public AsyncPyramidCommand stopOnLocalhostRequest(int timeoutInMilliseconds, int delayAfterStopInMilliseconds)
        throws InvalidFileConfigurationException
    {
        LOG.info("Stopping " + processName() + " on localhost...");
        final List<AsyncPyramidCommand> subTasks = new ArrayList<>();
        for (HttpPyramidServiceControl serviceControl : serviceControls) {
            subTasks.add(serviceControl.stopServiceOnLocalhostRequest(timeoutInMilliseconds, delayAfterStopInMilliseconds));
        }
        return new MultipleAsyncPyramidCommand(subTasks);
    }
}
