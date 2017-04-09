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
import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.common.PyramidServicesConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpServerConfiguration;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidProxyControl extends JavaProcessControl implements PyramidAccessControl {
    // Note: the following 2 constats must be identical to the same constants in HttpProxy class.
    public static final String ALIVE_STATUS_COMMAND = "/~~~~.net.algart.http.proxy.alive-status";
    public static final String FINISH_COMMAND = "/~~~~.net.algart.http.proxy.finish";

    public static final String PROXY_PROCESS_ID = "ProcessId~~~~." + HttpPyramidProxyControl.class.getName();

    private static final Logger LOG = Logger.getLogger(HttpPyramidProxyControl.class.getName());

    private final String proxyHost;
    private final int proxyPort;
    private final PyramidServicesConfiguration servicesConfiguration;
    private final HttpServerConfiguration serverConfiguration;
    private final Path systemCommandsFolder;

    public HttpPyramidProxyControl(
        String proxyHost,
        PyramidServicesConfiguration servicesConfiguration,
        HttpServerConfiguration serverConfiguration)
    {
        this.proxyHost = Objects.requireNonNull(proxyHost, "Null proxyHost");
        this.servicesConfiguration = Objects.requireNonNull(
            servicesConfiguration, "Null servicesConfiguration");
        this.serverConfiguration = Objects.requireNonNull(serverConfiguration,
            "Null serverConfiguration");
        if (!serverConfiguration.hasProxy()) {
            throw new IllegalArgumentException("Proxy is not used in this configuration");
        }
        this.proxyPort = serverConfiguration.getProxySettings().getProxyPort();
        this.systemCommandsFolder = HttpPyramidApiTools.systemCommandsFolder(servicesConfiguration.getProjectRoot());
    }

    public String getProxyHost() {
        return proxyHost;
    }

    @Override
    public String processId() {
        return PROXY_PROCESS_ID;
    }

    @Override
    public String processName() {
        return "\"Pyramid Proxy\"";
    }

    @Override
    public boolean isStabilityHttpCheckAfterStartRecommended() {
        return false;
    }

    @Override
    public boolean areAllHttpServicesAlive(boolean logWhenFails) {
        return isProxyAlive(logWhenFails);
    }

    @Override
    public boolean isAtLeastSomeHttpServiceAlive(boolean logWhenFails) {
        return isProxyAlive(logWhenFails);
    }

    @Override
    public Process startOnLocalhost() throws InvalidFileConfigurationException {
        final Path javaPath;
        try {
            javaPath = serverConfiguration.javaExecutable();
        } catch (IllegalJREException e) {
            throw new InvalidFileConfigurationException(e);
        }
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        final String xmxOption = servicesConfiguration.commonXmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        command.addAll(servicesConfiguration.getCommonVmOptions());
        StringBuilder cp = new StringBuilder();
        for (String p : servicesConfiguration.classPath(false)) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        command.add("-cp");
        command.add(cp.toString());
        command.add(HttpPyramidConstants.HTTP_PYRAMID_PROXY_SERVER_CLASS_NAME);
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVICE_MODE_FLAG);
        command.add(servicesConfiguration.getProjectRoot().toAbsolutePath().toString());
        command.add(serverConfiguration.getServerConfigurationFile().toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(servicesConfiguration.getPyramidServicesFolder().toAbsolutePath().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(JavaProcessControl.commandLineToString(processBuilder));
        AsyncPyramidSystemCommand.removeSystemCommandFile(FINISH_COMMAND, proxyPort, systemCommandsFolder);
        // - the key file must be removed BEFORE attempt to start new process,
        // for a case if it was kept from the previous failed attemt to stop the process
        try {
            return processBuilder.start();
        } catch (IOException e) {
            // Impossibility to start current Java (java.exe) is a serious system problem!
            throw new IOError(e);
        }
    }

    @Override
    public final AsyncPyramidCommand stopOnLocalhostRequest(int timeoutInMilliseconds, int delayAfterStopInMilliseconds)
        throws InvalidFileConfigurationException
    {
        LOG.info("Stopping " + processName() + " on localhost...");
        return new AsyncPyramidSystemCommand(
            FINISH_COMMAND,
            proxyPort,
            systemCommandsFolder,
            timeoutInMilliseconds,
            delayAfterStopInMilliseconds);
    }

    public final boolean isProxyAlive(boolean logWhenFails) {
        try {
            final HttpURLConnection connection = HttpPyramidApiTools.openConnection(
                connectionURI(ALIVE_STATUS_COMMAND),
                "GET",
                false);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
            // - getResponseCode() actually waits for results
        } catch (IOException e) {
            // - For example, java.net.ConnectException is normal situation, meaning that the service is stopped.
            // Unfortunately, some other exceptions like javax.net.ssl.SSLHandshakeException can lead to the same.
            if (logWhenFails) {
                LOG.log(Level.INFO, "Cannot connect to proxy " +
                    (serverConfiguration.getProxySettings().isSsl() ? "https" : "http")
                    + "://" + proxyHost + ":" + proxyPort + ": " + e);
            }
            return false;
        }
    }

    public final URI connectionURI(String pathAndQuery) {
        final boolean useSSL = serverConfiguration.getProxySettings().isSsl();
        try {
            return new URL(useSSL ? "https" : "http", proxyHost, proxyPort, pathAndQuery).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL path/query: " + pathAndQuery, e);
        }
    }
}
