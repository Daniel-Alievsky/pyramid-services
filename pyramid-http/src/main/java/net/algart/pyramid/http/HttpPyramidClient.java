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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class HttpPyramidClient {
    public static final String ALIVE_STATUS_COMMAND_PREFIX = "/pp-alive-status";
    public static final String FINISH_COMMAND_PREFIX = "/pp-finish";
    public static final String INFORMATION_COMMAND_PREFIX = "/pp-information";
    public static final String READ_RECTANGLE_COMMAND_PREFIX = "/pp-read-rectangle";
    public static final String TMS_COMMAND_PREFIX = "/pp-tms";
    public static final String ZOOMIFY_COMMAND_PREFIX = "/pp-zoomify";
    public static final String READ_SPECIAL_IMAGE_COMMAND_PREFIX = "/pp-read-special-image";
    public static final String ALIVE_RESPONSE = "Alive";

    private final String host;
    private final int port;

    public HttpPyramidClient(String host, int port) {
        this.host = Objects.requireNonNull(host, "Null host");
        if (port <= 0) {
            throw new IllegalArgumentException("Zero or negative port");
        }
        this.port = port;
    }

    public boolean isServiceAlive() throws IOException {
        //TODO!! timeout
        final HttpURLConnection connection = openCustomConnection(ALIVE_RESPONSE, "GET");
        return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
    }

    public void finishService() throws IOException {
        final HttpURLConnection connection = openCustomConnection(FINISH_COMMAND_PREFIX, "GET");
        checkHttpOk(connection);
    }

    public PlanePyramidInformation information() throws IOException {
        final HttpURLConnection connection = openCustomConnection(INFORMATION_COMMAND_PREFIX, "GET");
        checkHttpOk(connection);
        try (final InputStreamReader reader = new InputStreamReader(connection.getInputStream(),
            StandardCharsets.UTF_8))
        {
            try (final JsonReader jsonReader = Json.createReader(reader)) {
                final JsonObject json = jsonReader.readObject();
                return PlanePyramidInformation.valueOf(json);
            }
        }
    }

    public HttpURLConnection openCustomConnection(String pathAndQuery, String requestMethod) throws IOException {
        final URL url = new URL("http", host, port, pathAndQuery);
        final URLConnection connection = url.openConnection();
        //TODO!! correct timeout http://stackoverflow.com/questions/3163693/java-urlconnection-timeout
        if (!(connection instanceof HttpURLConnection)) {
            throw new AssertionError("Invalid type of openCustomConnection (not HttpURLConnection)");
        }
        final HttpURLConnection result = (HttpURLConnection) connection;
        result.setRequestMethod(requestMethod);
        return result;
    }

    public void checkHttpOk(HttpURLConnection connection) throws IOException {
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Invalid response: code " + connection.getResponseCode()
                + ", message " + connection.getResponseMessage());
        }
    }

}
