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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidProxyControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidProxyControl.class.getName());

    private final String host;
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
        this(host,
            Objects.requireNonNull(proxyConfiguration, "Null proxyConfiguration").getProxyPort(),
            proxyConfiguration.parentConfiguration().systemCommandsFolder(),
            https);
    }

    public HttpPyramidProxyControl(String host, int port, Path systemCommandsFolder, boolean https) {
        this.host = Objects.requireNonNull(host, "Null host");
        if (port <= 0 || port > HttpPyramidConstants.MAX_ALLOWED_PORT) {
            throw new IllegalArgumentException("Invalid port number " + port
                + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
        }
        this.port = port;
        this.systemCommandsFolder = Objects.requireNonNull(systemCommandsFolder, "Null systemCommandsFolder");
        this.https = https;
        // TODO!! support HTTPS on server side
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

    public final boolean finishService() {
        try {
            HttpPyramidServiceControl.requestSystemCommand(HttpProxy.FINISH_COMMAND, port, systemCommandsFolder);
            return true;
        } catch (IOException e) {
            LOG.log(Level.INFO, "Cannot request finish proxy command in folder "
                + systemCommandsFolder + ": " + e);
            return false;
        }
    }
}
