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
import net.algart.pyramid.http.api.HttpPyramidLimits;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.algart.pyramid.http.api.HttpPyramidKeywords.*;

public class HttpPyramidControl {
    private static final Logger LOG = Logger.getLogger(HttpPyramidControl.class.getName());

    private final String host;
    private final int port;

    public HttpPyramidControl(String host, int port) {
        this.host = Objects.requireNonNull(host, "Null host");
        if (port <= 0) {
            throw new IllegalArgumentException("Zero or negative port");
        }
        this.port = port;
    }

    public final boolean isServiceAlive() {
        try {
            final HttpURLConnection connection = openCustomConnection(ALIVE_STATUS_COMMAND_PREFIX, "GET");
            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            LOG.log(Level.INFO, "Cannot connect to " + host + ":" + port + ": " + e.getMessage());
            return false;
        }
    }

    public final void finishService() throws IOException {
        final HttpURLConnection connection = openCustomConnection(FINISH_COMMAND_PREFIX, "GET");
        checkHttpOk(connection);
    }

    public final PlanePyramidInformation information(String pyramidId) throws IOException {
        final HttpURLConnection connection = openCustomConnection(INFORMATION_COMMAND_PREFIX + "?"
            + PYRAMID_ID_ARGUMENT_NAME + "=" + URLEncoder.encode(pyramidId, StandardCharsets.UTF_8.name()),
            "GET");
        checkHttpOk(connection);
        try (final InputStreamReader reader = new InputStreamReader(connection.getInputStream(),
            StandardCharsets.UTF_8))
        {
            return PlanePyramidInformation.valueOf(reader);
        }
    }

    public final HttpURLConnection openCustomConnection(String pathAndQuery, String requestMethod) throws IOException {
        final URL url = new URL("http", host, port, pathAndQuery);
        final URLConnection connection = url.openConnection();
        connection.setConnectTimeout(HttpPyramidLimits.CLIENT_CONNECTION_TIMEOUT);
        connection.setReadTimeout(HttpPyramidLimits.CLIENT_READ_TIMEOUT);
        // In future, if necessary, we will maybe provide better timeouts:
        // http://stackoverflow.com/questions/3163693/java-urlconnection-timeout
        if (!(connection instanceof HttpURLConnection)) {
            throw new AssertionError("Invalid type of openCustomConnection (not HttpURLConnection)");
        }
        final HttpURLConnection result = (HttpURLConnection) connection;
        result.setRequestMethod(requestMethod);
        return result;
    }

    private void checkHttpOk(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid response: code " + connection.getResponseCode()
                + ", message " + connection.getResponseMessage());
        }
    }
}
