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

import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HttpPyramidServiceControl {
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
            final HttpURLConnection connection = openCustomConnection(
                HttpPyramidConstants.CommandPrefixes.ALIVE_STATUS, "GET");
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

    public final PlanePyramidInformation information(String pyramidId) throws IOException {
        final HttpURLConnection connection = openCustomConnection(
            HttpPyramidConstants.CommandPrefixes.INFORMATION
                + "?" + HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME
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
        return openCustomConnection(pathAndQuery, requestMethod, host, port, https);
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

    private void checkHttpOk(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid response: code " + connection.getResponseCode()
                + ", message " + connection.getResponseMessage());
        }
    }

    static String protocol(boolean https) {
        return https ? "https" : "http";
    }

    static HttpURLConnection openCustomConnection(
        String pathAndQuery,
        String requestMethod,
        String host,
        int port,
        boolean https
    )
        throws IOException
    {
        final URL url = new URL(protocol(https), host, port, pathAndQuery);
        final URLConnection connection = url.openConnection();
        connection.setConnectTimeout(HttpPyramidConstants.CLIENT_CONNECTION_TIMEOUT);
        connection.setReadTimeout(HttpPyramidConstants.CLIENT_READ_TIMEOUT);
        // In future, if necessary, we will maybe provide better timeouts:
        // http://stackoverflow.com/questions/3163693/java-urlconnection-timeout
        if (!(connection instanceof HttpURLConnection)) {
            throw new AssertionError("Invalid type of URL connection (not HttpURLConnection)");
        }
        final HttpURLConnection result = (HttpURLConnection) connection;
//        if (result instanceof HttpsURLConnection) {
//            ((HttpsURLConnection)result).setHostnameVerifier(new HostnameVerifier() {
//                @Override
//                public boolean verify(String s, SSLSession sslSession) {
//                    return true;
//                }
//            });
//        }
        result.setRequestMethod(requestMethod);
        return result;
    }

}
