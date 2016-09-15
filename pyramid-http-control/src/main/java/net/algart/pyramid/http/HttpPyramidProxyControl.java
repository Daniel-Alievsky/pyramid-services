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

import net.algart.http.proxy.HttpProxy;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.proxy.HttpPyramidProxyServer;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidProxyControl implements JavaProcessControlWithHttpCheckingAliveStatus {
    public static final String PROXY_PROCESS_ID = "ProcessId~~~~." + HttpPyramidProxyServer.class.getName();

    private static final Logger LOG = Logger.getLogger(HttpPyramidProxyControl.class.getName());

    private final String host;
    private final HttpPyramidConfiguration.Proxy proxyConfiguration;
    private final int port;
    private final Path systemCommandsFolder;
    private final boolean https;

    public HttpPyramidProxyControl(
        String host,
        HttpPyramidConfiguration.Proxy proxyConfiguration)
    {
        this(host, proxyConfiguration, false);
    }

    public HttpPyramidProxyControl(
        String host,
        HttpPyramidConfiguration.Proxy proxyConfiguration,
        boolean https)
    {
        this.host = Objects.requireNonNull(host, "Null host");
        this.proxyConfiguration = Objects.requireNonNull(proxyConfiguration, "Null proxyConfiguration");
        this.port = proxyConfiguration.getProxyPort();
        this.systemCommandsFolder = proxyConfiguration.parentConfiguration().systemCommandsFolder();
        this.https = https;
    }

    public String getHost() {
        return host;
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
    public boolean areAllHttpServicesAlive(boolean logWhenFails) {
        return isProxyAlive(logWhenFails);
    }

    @Override
    public boolean isAtLeastSomeHttpServiceAlive(boolean logWhenFails) {
        return isProxyAlive(logWhenFails);
    }

    @Override
    public Process startOnLocalhost() throws IOException {
        final HttpPyramidConfiguration configuration = proxyConfiguration.parentConfiguration();
        final Path javaPath = HttpPyramidConfiguration.getJavaExecutable(HttpPyramidConfiguration.getCurrentJREHome());
        List<String> command = new ArrayList<>();
        command.add(javaPath.toAbsolutePath().toString());
        command.addAll(proxyConfiguration.vmOptions());
        final String xmxOption = proxyConfiguration.xmxOption();
        if (xmxOption != null) {
            command.add(xmxOption);
        }
        StringBuilder cp = new StringBuilder();
        for (String p : proxyConfiguration.classPath(proxyConfiguration.hasWorkingDirectory())) {
            if (cp.length() > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(p);
        }
        command.add("-cp");
        command.add(cp.toString());
        command.add(HttpPyramidProxyServer.class.getName());
        command.add(HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG);
        command.add(configuration.getRootFolder().toAbsolutePath().toString());
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(proxyConfiguration.workingDirectory().toFile());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        LOG.info(JavaProcessControlWithHttpCheckingAliveStatus.commandLineToString(processBuilder));
        return processBuilder.start();
    }

    @Override
    public final boolean stopOnLocalhost() {
        try {
            HttpPyramidServiceControl.requestSystemCommand(HttpProxy.FINISH_COMMAND, port, systemCommandsFolder);
            LOG.info("Stopping " + processName() + " on localhost");
            return true;
        } catch (IOException e) {
            LOG.log(Level.INFO, "Cannot request finish proxy command in folder "
                + systemCommandsFolder + ": " + e);
            return false;
        }
    }

    public final boolean isProxyAlive(boolean logWhenFails) {
        try {
            final HttpURLConnection connection = HttpPyramidServiceControl.openCustomConnection(
                HttpProxy.ALIVE_STATUS_COMMAND, "GET", host, port, https);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            if (logWhenFails) {
                LOG.log(Level.INFO, "Cannot connect to proxy " + host + ":" + port + ": " + e);
            }
            return false;
        }
    }
}
