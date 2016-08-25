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

package net.algart.http.proxy;

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.logging.Level;

class ProxyClientProcessor extends BaseFilter {
    private final String requestURL;
    // - for logging needs
    private final HttpServerFailureHandler serverFailureHandler;
    private final HttpRequestPacket requestToServerHeaders;
    private final Response response;
    private final HttpServerAddress server;
    private final NIOInputStream inputStreamFromClient;
    // - usually for POST requests
    private final NIOOutputStream outputStreamToClient;
    private TCPNIOConnectorHandler connectorToServer = null;
    // - should be set after constructing the object on the base of resulting FilterChain
    private volatile Connection connectionToServer = null;
    private volatile boolean firstReply = true;
    private volatile boolean connectionToServerClosed = false;

    private final Object lock = new Object();

    ProxyClientProcessor(
        Request request,
        Response response,
        HttpServerAddress server,
        HttpServerFailureHandler serverFailureHandler)
    {
        assert server != null;
        assert serverFailureHandler != null;
        this.requestURL = String.valueOf(request.getRequestURL());
        this.response = response;
        this.inputStreamFromClient = request.getNIOInputStream();
        this.outputStreamToClient = response.getNIOOutputStream();
        this.serverFailureHandler = serverFailureHandler;
        this.server = server;

        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder.protocol(Protocol.HTTP_1_1);
        builder.method(request.getMethod());
        builder.uri(request.getRequestURI());
        builder.query(request.getQueryString());
        builder.contentType(request.getContentType());
        builder.contentLength(request.getContentLength());
        for (String headerName : request.getHeaderNames()) {
            for (String headerValue : request.getHeaders(headerName)) {
                builder.header(headerName, headerValue);
            }
        }
        builder.removeHeader("Host");
        builder.header("Host", server.canonicalHTTPHostHeader());
        this.requestToServerHeaders = builder.build();
    }

    public void setConnectorToServer(TCPNIOConnectorHandler connectorToServer) {
        this.connectorToServer = Objects.requireNonNull(connectorToServer);
    }

    public void requestConnectionToServer() throws InterruptedException {
        HttpProxy.LOG.config("Requesting connection to " + server + "...");
        connectorToServer.connect(
            new InetSocketAddress(server.serverHost(), server.serverPort()), new CompletionHandler<Connection>() {
                @Override
                public void cancelled() {
                }

                @Override
                public void failed(Throwable throwable) {
                    synchronized (lock) {
                        HttpProxy.LOG.log(Level.WARNING,
                            "Connection to server " + server + " failed (" + requestURL + ")", throwable);
                        closeAndReturnError("Cannot connect to the server");
                        serverFailureHandler.onConnectionFailed(server, throwable);
                    }
                }

                @Override
                public void completed(Connection connection) {
                    HttpProxy.LOG.fine("Connected");
                    // actually will be processed in handleConnect method
                }

                @Override
                public void updated(Connection connection) {
                }
            });
    }

    public void closeServerAndClientConnections() {
        synchronized (lock) {
            connectionToServerClosed = true;
            // - closeSilently will invoke handleClose, but it will do nothing
            if (connectionToServer != null) {
                connectionToServer.closeSilently();
                connectionToServer = null;
            }
            try {
                outputStreamToClient.close();
            } catch (IOException e) {
                HttpProxy.LOG.log(Level.FINE, "Error while closing output stream", e);
                // only FINE: this exception can occur if the browser terminates connection;
                // no sense to duplicate messages - an attempt to write will also lead to an exception
            }
        }
    }

    public void closeAndReturnError(String message) {
        synchronized (lock) {
            response.setStatus(500, "AlgART Proxy: " + message);
            response.setContentType("text/plain; charset=utf-8");
            final Buffer contentBuffer = Buffers.wrap(null, "AlgART Proxy: " + message);
            outputStreamToClient.notifyCanWrite(new ProxyWriteHandler(contentBuffer, true));
            HttpProxy.LOG.warning("Error: " + message + " (" + requestURL + ")");
//            try {
//                outputStreamToClient.write(contentBuffer);
//                outputStreamToClient.flush();
//            } catch (IOException ignored) {
//            }
//            closeServerAndClientConnections();
//            if (response.isSuspended()) {
//                response.resume();
//                HttpProxy.LOG.warning("Response is resumed due to error: " + message + " (" + requestURL + ")");
//            } else {
//                response.finish();
//                HttpProxy.LOG.warning("Response is finished due to error: " + message + " (" + requestURL + ")");
//            }
        }
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        setConnectionToServer(ctx.getConnection());
        inputStreamFromClient.notifyAvailable(new ReadHandler() {
            @Override
            public void onDataAvailable() throws Exception {
                synchronized (lock) {
                    sendData();
                }
                inputStreamFromClient.notifyAvailable(this);
            }

            @Override
            public void onError(Throwable error) {
                HttpProxy.LOG.log(Level.WARNING, "Error while reading request (" + requestURL + ")", error);
                closeAndReturnError("Error while reading request");
            }

            @Override
            public void onAllDataRead() throws Exception {
                synchronized (lock) {
                    HttpProxy.LOG.config("All data ready");
                    sendData();
                    try {
                        inputStreamFromClient.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            private void sendData() {
                final Buffer buffer = inputStreamFromClient.readBuffer();
                final boolean finished = inputStreamFromClient.isFinished();
                HttpProxy.LOG.config("Data ready, sending request to server: buffer " + buffer);
                final HttpContent httpContent = HttpContent.builder(requestToServerHeaders)
                    .content(buffer)
                    .last(finished)
                    .build();
                connectionToServer.write(httpContent);
                // - note: buffer will be destroyed by this call
            }
        });
        HttpProxy.LOG.config("Connected to " + server
            + "; starting sending request to server: header " + requestToServerHeaders);
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final HttpContent httpContent = ctx.getMessage();
        final Buffer contentBuffer = httpContent.getContent();
        final boolean last = httpContent.isLast();
        synchronized (lock) {
            if (connectionToServerClosed) {
                // - to be on the safe side (should not occur)
                return ctx.getStopAction();
            }
            if (firstReply) {
                final HttpResponsePacket httpHeader = (HttpResponsePacket) httpContent.getHttpHeader();
                response.setStatus(httpHeader.getHttpStatus());
                final MimeHeaders headers = httpHeader.getHeaders();
                for (String headerName : headers.names()) {
                    for (String headerValue : headers.values(headerName)) {
                        response.addHeader(headerName, headerValue);
                    }
                }
                firstReply = false;
            }
        }

        HttpProxy.LOG.config("Notifying about sending " + contentBuffer + (last ? " (LAST)" : ""));
        // Events in notifyCanWrite are internally synchronized,
        // and all calls of onWritePossible will be in proper order.
        outputStreamToClient.notifyCanWrite(new ProxyWriteHandler(contentBuffer, last));
        return ctx.getStopAction();
    }

    @Override
    public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
        HttpProxy.LOG.log(Level.SEVERE, "Error while reading data from " + server + " (" + requestURL + ")", error);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        // getMessage will be null
        if (connectionToServerClosed) {
            // Nothing to do: maybe we already called closeServerAndClientConnections
            return ctx.getStopAction();
        }

        HttpProxy.LOG.log(Level.INFO, "Unexpected connection close while reading from "
            + server + " (" + requestURL + ")");
        closeConnectionsAndResponse();
        return ctx.getStopAction();
    }

    private void closeConnectionsAndResponse() {
        synchronized (lock) {
            closeServerAndClientConnections();
            response.resume();
            HttpProxy.LOG.config("Response is resumed");
        }
    }

    private void setConnectionToServer(Connection connectionToServer) {
        synchronized (lock) {
            this.connectionToServer = connectionToServer;
        }
    }

    private class ProxyWriteHandler implements WriteHandler {
        private final Buffer contentBuffer;
        private final boolean last;

        ProxyWriteHandler(Buffer contentBuffer, boolean last) {
            assert contentBuffer != null;
            this.contentBuffer = contentBuffer;
            this.last = last;
        }

        @Override
        public void onWritePossible() throws Exception {
            synchronized (lock) {
                outputStreamToClient.write(contentBuffer);
                outputStreamToClient.flush(); // - necessary to avoid a bug in Grizzly 2.3.22!
                if (last) {
                    closeConnectionsAndResponse();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            HttpProxy.LOG.log(Level.WARNING, "Error while sending data to client (" + requestURL + ")", t);
        }
    }
}
