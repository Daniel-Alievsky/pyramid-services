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

import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.http.api.HttpPyramidApiTools;
import net.algart.pyramid.http.api.HttpPyramidConfiguration;
import net.algart.pyramid.http.api.HttpPyramidConstants;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidServiceControl {
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
        // TODO!! support HTTPS on server side
    }

    public final boolean isServiceAlive(boolean logWhenFails) {
        try {
            final HttpURLConnection connection = openCustomConnection(
                HttpPyramidConstants.CommandPrefixes.ALIVE_STATUS, "GET");
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            if (logWhenFails) {
                LOG.log(Level.INFO, "Cannot connect to " + host + ":" + port + ": " + e.getMessage());
            }
            return false;
        }
    }

    public final boolean finishService() {
        try {
            requestSystemCommand(HttpPyramidConstants.CommandPrefixes.FINISH);
            return true;
        } catch (IOException e) {
            LOG.log(Level.INFO, "Cannot request finish command in folder "
                + systemCommandsFolder + ": " + e.getMessage());
            return false;
        }
    }

    public final PlanePyramidInformation information(String pyramidId) throws IOException {
        final HttpURLConnection connection = openCustomConnection(
            HttpPyramidConstants.CommandPrefixes.INFORMATION + "?" + HttpPyramidConstants.PYRAMID_ID_ARGUMENT_NAME
                + "=" + URLEncoder.encode(pyramidId, StandardCharsets.UTF_8.name()),
            "GET");
        checkHttpOk(connection);
        try (final InputStreamReader reader = new InputStreamReader(connection.getInputStream(),
            StandardCharsets.UTF_8))
        {
            return PlanePyramidInformation.valueOf(reader);
        }
    }

    public final HttpURLConnection openCustomConnection(
        String pathAndQuery,
        String requestMethod)
        throws IOException
    {
        final URL url = new URL(https ? "https" : "http", host, port, pathAndQuery);
        final URLConnection connection = url.openConnection();
        connection.setConnectTimeout(HttpPyramidConstants.CLIENT_CONNECTION_TIMEOUT);
        connection.setReadTimeout(HttpPyramidConstants.CLIENT_READ_TIMEOUT);
        // In future, if necessary, we will maybe provide better timeouts:
        // http://stackoverflow.com/questions/3163693/java-urlconnection-timeout
        if (!(connection instanceof HttpURLConnection)) {
            throw new AssertionError("Invalid type of openCustomConnection (not HttpURLConnection)");
        }
        final HttpURLConnection result = (HttpURLConnection) connection;
        result.setRequestMethod(requestMethod);
        return result;
    }

    public final void requestSystemCommand(String commandPrefix) throws IOException {
        final Path keyFile = HttpPyramidApiTools.keyFile(systemCommandsFolder, commandPrefix, port);
        try {
            Files.delete(keyFile);
            // - to be on the safe side; removing key file does not affect services
        } catch (IOException e) {
            // it is not a problem
        }
        try {
            Files.createFile(keyFile);
        } catch (FileAlreadyExistsException e) {
            // it is not a problem if a parallel process also created the same file
        }
    }

    private void checkHttpOk(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid response: code " + connection.getResponseCode()
                + ", message " + connection.getResponseMessage());
        }
    }
}
