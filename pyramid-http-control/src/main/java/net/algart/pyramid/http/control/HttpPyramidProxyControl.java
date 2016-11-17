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

import net.algart.http.proxy.HttpProxy;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;
import net.algart.pyramid.http.proxy.HttpPyramidProxyServer;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidProxyControl extends JavaProcessControl {
    public static final String PROXY_PROCESS_ID = "ProcessId~~~~." + HttpPyramidProxyServer.class.getName();

    private static final Logger LOG = Logger.getLogger(HttpPyramidProxyControl.class.getName());

    private final String proxyHost;
    private final int proxyPort;
    private final HttpPyramidConfiguration configuration;
    private final HttpPyramidSpecificServerConfiguration specificServerConfiguration;
    private final Path systemCommandsFolder;

    public HttpPyramidProxyControl(
        String proxyHost,
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.proxyHost = Objects.requireNonNull(proxyHost, "Null proxyHost");
        this.configuration = Objects.requireNonNull(configuration, "Null configuration");
        this.specificServerConfiguration = Objects.requireNonNull(specificServerConfiguration,
            "Null specificServerConfiguration");
        if (!specificServerConfiguration.hasProxy()) {
            throw new IllegalArgumentException("Proxy is not used in this configuration");
        }
        this.proxyPort = specificServerConfiguration.getProxySettings().getProxyPort();
        this.systemCommandsFolder = configuration.systemCommandsFolder();
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
    public Process startOnLocalhost() {
        final Path javaPath = PyramidApiTools.getCurrentJREJavaExecutable();
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        command.addAll(configuration.getCommonVmOptions());
        final String xmxOption = configuration.commonXmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        StringBuilder cp = new StringBuilder();
        for (String p : configuration.classPath(false)) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        command.add("-cp");
        command.add(cp.toString());
        command.add(HttpPyramidProxyServer.class.getName());
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG);
        command.add(configuration.getProjectRoot().toAbsolutePath().toString());
        command.add(specificServerConfiguration.getSpecificServerConfigurationFile().toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(configuration.getPyramidServicesFolder().toAbsolutePath().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(JavaProcessControl.commandLineToString(processBuilder));
        try {
            return processBuilder.start();
        } catch (IOException e) {
            // Impossibility to start current Java (java.exe) is a serious system problem!
            throw new IOError(e);
        }
    }

    @Override
    public final AsyncPyramidCommand stopOnLocalhost(int timeoutInMilliseconds)
        throws InvalidFileConfigurationException
    {
        LOG.info("Stopping " + processName() + " on localhost...");
        return new AsyncPyramidSystemCommand(
            HttpProxy.FINISH_COMMAND, proxyPort, systemCommandsFolder, timeoutInMilliseconds);
    }

    public final boolean isProxyAlive(boolean logWhenFails) {
        final boolean useSSL = specificServerConfiguration.getProxySettings().isSsl();
        try {
            final HttpURLConnection connection = HttpPyramidServiceControl.openCustomConnection(
                HttpProxy.ALIVE_STATUS_COMMAND, "GET", proxyHost, proxyPort, useSSL);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
            // - getResponseCode() actually waits for results
        } catch (IOException e) {
            // - For example, java.net.ConnectException is normal situation, meaning that the service is stopped.
            // Unfortunately, some other exceptions like javax.net.ssl.SSLHandshakeException can lead to the same.
            if (logWhenFails) {
                LOG.log(Level.INFO, "Cannot connect to proxy " +
                    HttpPyramidServiceControl.protocol(useSSL) + "://" + proxyHost + ":" + proxyPort + ": " + e);
            }
            return false;
        }
    }
}