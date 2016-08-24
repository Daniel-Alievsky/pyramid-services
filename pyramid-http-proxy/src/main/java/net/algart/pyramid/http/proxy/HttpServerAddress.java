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

package net.algart.pyramid.http.proxy;

import java.util.Objects;

public final class HttpServerAddress {
    private final String serverHost;
    private final int serverPort;

    public HttpServerAddress(String serverHost, int serverPort) {
        Objects.requireNonNull(serverHost);
        if (serverPort <= 0) {
            throw new IllegalArgumentException("Zero or negative port " + serverPort);
        }
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public String serverHost() {
        return serverHost;
    }

    public int serverPort() {
        return serverPort;
    }

    public String canonicalHTTPHostHeader() {
        return serverHost + (serverPort == 80 ? "" : ":" + serverPort);
        // - Important: to be congruent with popular browsers, the port should be stripped from
        // the 'Host' field when the port is 80. In other case, it is possible that the server
        // will return "moved" response (instead of correct page) to remove port number from URL.
    }

    @Override
    public String toString() {
        return serverHost + ":" + serverPort;
    }
}
