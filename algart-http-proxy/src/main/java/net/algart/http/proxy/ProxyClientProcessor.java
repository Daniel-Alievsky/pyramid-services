/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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
import org.glassfish.grizzly.http.server.SuspendContext;
import org.glassfish.grizzly.http.server.TimeoutHandler;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

class ProxyClientProcessor extends BaseFilter {
    private static final boolean DEBUG_MODE = false;

    private final HttpProxy proxy;
    private final Request request;
    // - for logging needs
    private final HttpRequestPacket requestToServerHeaders;
    private final Response response;
    private final HttpServerAddress serverAddress;
    private final NIOInputStream inputStreamFromClient;
    // - usually for POST requests
    private final NIOOutputStream outputStreamToClient;
    private final Connection connectionToClient;
    private final SuspendContext suspendContext;
    private volatile Connection connectionToServer = null;
    private volatile boolean firstReply = true;
    private volatile boolean connectionToServerClosed = false;
    private volatile boolean allClosed = false;
    private volatile boolean timeoutOccurred = false;

    private final Object lock = new Object();
    private final StringBuilder debugStringBuilder = DEBUG_MODE ? new StringBuilder() : null;

    ProxyClientProcessor(
        HttpProxy proxy,
        Request request,
        Response response,
        HttpServerAddress serverAddress)
    {
        assert proxy != null;
        assert request != null;
        assert response != null;
        assert serverAddress != null;
        this.proxy = proxy;
        this.request = request;
        this.response = response;
        this.inputStreamFromClient = request.getNIOInputStream();
        this.connectionToClient = request.getRequest().getConnection();
        this.outputStreamToClient = response.getNIOOutputStream();
        this.serverAddress = serverAddress;
        this.suspendContext = response.getSuspendContext();

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
        builder.header("Host", serverAddress.canonicalHTTPHostHeader());
        if (proxy.isAddingXForwardedFor()) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor == null || xForwardedFor.trim().isEmpty()) {
                xForwardedFor = request.getRemoteAddr();
            } else {
                xForwardedFor += ", " + request.getRemoteAddr();
            }
            builder.removeHeader("X-Forwarded-For");
            builder.header("X-Forwarded-For", xForwardedFor);
        }
        this.requestToServerHeaders = builder.build();
    }

    public void suspendResponse() {
        response.suspend(proxy.getReadingFromServerTimeoutInMs(), TimeUnit.MILLISECONDS, null, new TimeoutHandler() {
            @Override
            public boolean onTimeout(Response responseInTimeout) {
                closeAndReturnError("Timeout while waiting for the server response: waiting more than "
                    + proxy.getReadingFromServerTimeoutInMs() / 1000 + " seconds");
                timeoutOccurred = true;
                try {
                    proxy.getServerFailureHandler().onServerTimeout(
                        serverAddress,
                        String.valueOf(request.getRequestURL()));
                } catch (Throwable t) {
                    HttpProxy.LOG.log(Level.SEVERE, "Problem in onServerTimeout (" + proxy + ")", t);
                }
                return true;
            }
        });

    }

    public void requestConnectionToServer(TCPNIOConnectorHandler connectorToServer) throws InterruptedException {
        HttpProxy.LOG.config("Requesting connection to " + serverAddress + "...");
        connectorToServer.connect(
            new InetSocketAddress(serverAddress.serverHost(), serverAddress.serverPort()),
            new CompletionHandler<Connection>() {
                @Override
                public void cancelled() {
                }

                @Override
                public void failed(Throwable throwable) {
                    synchronized (lock) {
                        HttpProxy.LOG.log(Level.WARNING,
                            "Connection to server " + serverAddress + " failed ("
                                + request.getRequestURL() + "): " + throwable);
                        // - possible situation, no sense to print stack trace
                        closeAndReturnError("Cannot connect to the server");
                        try {
                            proxy.getServerFailureHandler().onConnectionFailed(serverAddress, throwable);
                        } catch (Throwable t) {
                            HttpProxy.LOG.log(Level.SEVERE, "Problem in onConnectionFailed ("
                                + request.getRequestURL() + ")", t);
                        }
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
            public void onError(Throwable t) {
                HttpProxy.LOG.log(Level.WARNING, "Error while reading request (" + request.getRequestURL() + ")", t);
                closeAndReturnError("Error while reading request");
            }

            @Override
            public void onAllDataRead() throws Exception {
                synchronized (lock) {
                    HttpProxy.LOG.config("All client data ready");
                    sendData();
                    try {
                        inputStreamFromClient.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            private void sendData() {
                resetTimeout();
                final Buffer buffer = inputStreamFromClient.readBuffer();
                final boolean finished = inputStreamFromClient.isFinished();
                HttpProxy.LOG.config("Client data ready, sending request to server: buffer " + buffer);
                final HttpContent httpContent = HttpContent.builder(requestToServerHeaders)
                    .content(buffer)
                    .last(finished)
                    .build();
                connectionToServer.write(httpContent);
                // - note: buffer will be destroyed by this call
            }
        });
        HttpProxy.LOG.config("Connected to " + serverAddress
            + "; sending request to " + requestToServerHeaders.getRequestURI());
        HttpProxy.LOG.fine("Full request header: " + requestToServerHeaders);
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
                final HttpStatus httpStatus = httpHeader.getHttpStatus();
                response.setStatus(httpStatus);
                final boolean correctMoved = proxy.isCorrectMovedLocations() &&
                    (httpStatus.getStatusCode() == 301 || httpStatus.getStatusCode() == 302);
                final MimeHeaders headers = httpHeader.getHeaders();
                for (String headerName : headers.names()) {
                    for (String headerValue : headers.values(headerName)) {
                        if (correctMoved && "location".equalsIgnoreCase(headerName) && headerValue != null) {
                            // - null check to be on the safe side (should not occur)
                            String newLocation = HttpProxy.correctLocationFor3XXResponse(headerValue,
                                request, serverAddress);
                            HttpProxy.LOG.info("Redirection to " + headerValue + " catched: "
                                + (newLocation.equals(headerValue) ?
                                "not changed" :
                                "changed to " + newLocation)
                                + " (proxying " + request.getRequestURL() + " to " + serverAddress + ")");
                            headerValue = newLocation;
                        }
                        response.addHeader(headerName, headerValue);
                    }
                }
                firstReply = false;
            }
        }

        HttpProxy.LOG.fine("Notifying about sending " + contentBuffer + (last ? " (LAST)" : ""));
        if (debugStringBuilder != null) {
            synchronized (lock) {
                debugStringBuilder.append("N");
            }
        }
        final ProxyWriteHandler handler = new ProxyWriteHandler(contentBuffer, ctx, last);
        final NextAction suspendAction = ctx.getSuspendAction();
        outputStreamToClient.notifyCanWrite(handler);
        return suspendAction;
        /*
        // Obsolete solution...
        final long timestamp = System.currentTimeMillis();
        synchronized (lock) {
            while (!handler.writingStarted) {
                if (!connectionToClient.isOpen()) {
                    HttpProxy.LOG.config("Waiting for sending data stopped: connection to the client is closed ("
                        + request.getRequestURL() + ")");
                    break;
                }
                if (timeoutOccurred) {
                    // To be on the safe side: no sense to wait after timeout (should not occur:
                    // connection with the client should be closed and the previous check should be true)
                    HttpProxy.LOG.config("Waiting for sending data stopped: timeout ("
                        + request.getRequestURL() + ")");
                    break;
                }
                if (System.currentTimeMillis() - timestamp > 3000 + proxy.getReadingFromServerTimeoutInMs()) {
                    // Also to be on the safe side: guaranteedly avoiding infinite loop
                    HttpProxy.LOG.warning("Waiting for sending data is too long ("
                        + request.getRequestURL() + ")");
                    break;
                }
                try {
                    lock.wait(100);
                    // We need to wait until onWritePossible() method will enter to synchronized section
                    // and really start sending data. After this, we can be sure that the next call
                    // of this handleRead method will be delayed at the "synchronized" operator
                    // in the beginning of this method. In other case, there is a risk that the next call
                    // of this method will occur BEFORE starting onWritePossible(), and notifyCanWrite
                    // method will throw an exception
                    // "Illegal attempt to set a new handler before the existing handler has been notified"
                } catch (InterruptedException e) {
                    // just exiting with STOP_ACTION
                }
            }
        }
        return ctx.getStopAction();
        */
    }

    @Override
    public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
        HttpProxy.LOG.log(Level.SEVERE, "Error while reading data from "
            + serverAddress + " (" + request.getRequestURL() + ")", error);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        // getMessage will be null
        if (connectionToServerClosed) {
            // Nothing to do: maybe we already called closeServerAndClientConnections
            return ctx.getStopAction();
        }

        HttpProxy.LOG.log(Level.INFO, "Unexpected connection close while reading from "
            + serverAddress + " (" + request.getRequestURL() + ")");
        closeConnectionsAndResponse();
        return ctx.getStopAction();
    }

    public void closeAndReturnError(String message) {
        synchronized (lock) {
            if (response.isCommitted()) {
                closeConnectionsAndResponse();
            } else {
                response.setStatus(500, "AlgART Proxy: " + message);
                response.setContentType("text/plain; charset=utf-8");
                final Buffer contentBuffer = Buffers.wrap(null, "AlgART Proxy: " + message);
                // Connections will be closed by the following ProxyWriteHandler
                outputStreamToClient.notifyCanWrite(new ProxyWriteHandler(contentBuffer, null, true));
            }
            HttpProxy.LOG.warning("Error: " + message + " ("
                + request.getRequestURL() + ", forwarded to " + serverAddress + ")");
        }
    }

    private void resetTimeout() {
        suspendContext.setTimeout(proxy.getReadingFromServerTimeoutInMs(), TimeUnit.MILLISECONDS);
    }

    private void closeServerAndClientConnections() {
        synchronized (lock) {
            connectionToServerClosed = true;
            // - closeSilently will invoke handleClose, but it will do nothing
            if (connectionToServer != null) {
                connectionToServer.closeSilently();
                connectionToServer = null;
            }
            try {
                outputStreamToClient.close();
                connectionToClient.closeSilently();
                // - closing connection is necessary in a case of some problem like timeout;
                // in other case, the browser will wait for all Content-Length bytes
            } catch (IOException e) {
                HttpProxy.LOG.log(Level.FINE, "Error while closing output stream", e);
                // only FINE: this exception can occur if the browser terminates connection;
                // no sense to duplicate messages - an attempt to write will also lead to an exception
            }
        }
    }

    private void closeConnectionsAndResponse() {
        synchronized (lock) {
            if (!allClosed) {
                // This method can be called twice, for example, in the following scenario:
                // 1) timeout occurs and HttpProxy class calls closeAndReturnError
                // 2) AFTER this connection fails in requestConnectionToServer() method,
                // and it's failed() method is launched
                closeServerAndClientConnections();
                response.resume();
                HttpProxy.LOG.config("Response is resumed");
                allClosed = true;
                if (DEBUG_MODE) {
                    System.out.println(debugStringBuilder);
                }
            }
        }
    }

    private void setConnectionToServer(Connection connectionToServer) {
        synchronized (lock) {
            this.connectionToServer = connectionToServer;
        }
    }

    private class ProxyWriteHandler implements WriteHandler {
        private final FilterChainContext ctx;
        private final Buffer contentBuffer;
        private final boolean last;
        private volatile boolean writingStarted = false;

        ProxyWriteHandler(Buffer contentBuffer, FilterChainContext ctx, boolean last) {
            assert contentBuffer != null && ctx != null;
            this.contentBuffer = contentBuffer;
            this.ctx = ctx;
            this.last = last;
        }

        @Override
        public void onWritePossible() throws Exception {
            synchronized (lock) {
                ctx.resumeNext();
                writingStarted = true;
                // lock.notifyAll(); - obsolete solution
                if (debugStringBuilder != null) {
                    debugStringBuilder.append("b");
                }
                resetTimeout();
                outputStreamToClient.write(contentBuffer);
                if (debugStringBuilder != null) {
                    debugStringBuilder.append("e\n");
                }
//                for (long t = System.currentTimeMillis(); System.currentTimeMillis() - t < 5500; ) ;
//                System.out.println("!!!DELAY!! " + contentBuffer + "; " + last + "; " + new java.util.Date());
                if (last) {
                    closeConnectionsAndResponse();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            HttpProxy.LOG.config("Error while sending data to client (" + request.getRequestURL() + "): " + t);
            // - this is not a serious problem, just the client cannot receive data too quickly (internet is slow)
            synchronized (lock) {
                if (!writingStarted) {
                    ctx.completeAndRecycle();
                }
            }
        }
    }
}
