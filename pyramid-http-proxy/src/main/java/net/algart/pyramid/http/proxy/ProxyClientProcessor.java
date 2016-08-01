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

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class ProxyClientProcessor {
    private final TCPNIOTransport clientTransport;
    private final Request request;
    private final Response response;
    private final String serverHost;
    private final int serverPort;
    private final NIOOutputStream outputStream;
    private volatile Connection connection = null;
    private boolean firstReply = true;

    ProxyClientProcessor(
        TCPNIOTransport clientTransport,
        Request request,
        Response response,
        String serverHost,
        int serverPort)
    {
        this.clientTransport = clientTransport;
        this.request = request;
        this.response = response;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.outputStream = response.getNIOOutputStream();
    }

    public void connect() throws InterruptedException, ExecutionException, TimeoutException {
        Future<Connection> connectFuture = clientTransport.connect(serverHost, serverPort);
        this.connection = connectFuture.get(10, TimeUnit.SECONDS);
    }

}
