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

import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;

import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HttpPyramidServiceControl implements PyramidAccessControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServiceControl.class.getName());

    private final String host;
    private final int port;
    private final Path systemCommandsFolder;
    private final boolean https;

    public HttpPyramidServiceControl(
        String host,
        HttpPyramidConfiguration.Service serviceConfiguration)
    {
        this(host, serviceConfiguration, false);
    }

    public HttpPyramidServiceControl(
        String host,
        HttpPyramidConfiguration.Service serviceConfiguration,
        boolean https)
    {
        this(host,
            Objects.requireNonNull(serviceConfiguration, "Null serviceConfiguration").getPort(),
            serviceConfiguration.parentProcess().parentConfiguration().systemCommandsFolder(),
            https);
    }

    public HttpPyramidServiceControl(String host, int port, Path systemCommandsFolder, boolean https) {
        this.host = Objects.requireNonNull(host, "Null host");
        if (port <= 0 || port > HttpPyramidConstants.MAX_ALLOWED_PORT) {
            throw new IllegalArgumentException("Invalid port number " + port
                + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
        }
        this.port = port;
        this.systemCommandsFolder = Objects.requireNonNull(systemCommandsFolder, "Null systemCommandsFolder");
        this.https = https;
    }

    public final boolean isServiceAlive(boolean logWhenFails) {
        try {
            final HttpURLConnection connection = HttpPyramidApiTools.openConnection(
                connectionURI(HttpPyramidConstants.CommandPrefixes.ALIVE_STATUS),
                "GET",
                false);
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
            // - getResponseCode() actually waits for results
        } catch (IOException e) {
            // - For example, java.net.ConnectException is normal situation, meaning that the service is stopped
            if (logWhenFails) {
                LOG.log(Level.INFO, "Cannot connect to " + host + ":" + port + ": " + e);
            }
            return false;
        }
    }

    public final AsyncPyramidCommand stopServiceOnLocalhostRequest(
        int timeoutInMilliseconds,
        int delayAfterStopInMilliseconds)
        throws InvalidFileConfigurationException
    {
        return requestSystemCommand(
            HttpPyramidConstants.CommandPrefixes.FINISH,
            timeoutInMilliseconds,
            delayAfterStopInMilliseconds);
    }

    public final void removeFinishSystemCommandFile() throws InvalidFileConfigurationException {
        AsyncPyramidSystemCommand.removeSystemCommandFile(
            HttpPyramidConstants.CommandPrefixes.FINISH, port, systemCommandsFolder);
    }

    public final URI connectionURI(String pathAndQuery) {
        try {
            return new URL(https ? "https" : "http", host, port, pathAndQuery).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL path/query: " + pathAndQuery, e);
        }
    }

    public final AsyncPyramidCommand requestSystemCommand(
        String commandPrefix,
        int timeoutInMilliseconds,
        int delayAfterStopInMilliseconds)
        throws InvalidFileConfigurationException
    {
        return new AsyncPyramidSystemCommand(
            commandPrefix,
            port,
            systemCommandsFolder,
            timeoutInMilliseconds,
            delayAfterStopInMilliseconds);
    }

}
