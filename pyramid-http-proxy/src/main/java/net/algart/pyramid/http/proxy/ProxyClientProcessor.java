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
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.utils.Charsets;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.Level;

class ProxyClientProcessor extends BaseFilter {
    private final HttpRequestPacket requestToServerHeaders;
    private final Response response;
    private final String serverHost;
    private final int serverPort;
    private final NIOInputStream inputStream;
    private final NIOOutputStream outputStream;
    private TCPNIOConnectorHandler connectorHandler = null;
    // - should be set after constructing the object on the base of resulting FilterChain
    private volatile Connection connection = null;
    private volatile boolean firstReply = true;

    private final Object lock = new Object();

    ProxyClientProcessor(
        Request request,
        Response response,
        HttpProxy httpProxy)
    {
        this.response = response;
        this.inputStream = request.getNIOInputStream();
        this.outputStream = response.getNIOOutputStream();

        final HttpProxy.ServerAddress server = httpProxy.getServer(parseQueryOnly(request));
        this.serverHost = server.serverHost();
        this.serverPort = server.serverPort();

        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder.protocol(Protocol.HTTP_1_1);
        builder.method(request.getMethod());
        builder.uri(request.getRequestURI());
        builder.query(request.getQueryString());
        builder.contentType(request.getContentType());
        builder.contentLength(request.getContentLength());
        // - maybe contentType and contentLength are not necessary
        for (String headerName : request.getHeaderNames()) {
            for (String headerValue : request.getHeaders(headerName)) {
                builder.header(headerName, headerValue);
                //TODO!! check identical names
            }
        }
        builder.removeHeader("Host");
        builder.header("Host", serverHost + ":" + serverPort);
        this.requestToServerHeaders = builder.build();
    }

    public void setConnectorHandler(TCPNIOConnectorHandler connectorHandler) {
        this.connectorHandler = Objects.requireNonNull(connectorHandler);
    }

    public void requestConnectionToServer() throws InterruptedException {
        HttpProxy.LOG.config("Requesting connection to " + serverHost + ":" + serverPort + "...");
        connectorHandler.connect(
            new InetSocketAddress(serverHost, serverPort), new CompletionHandler<Connection>() {
                @Override
                public void cancelled() {
                }

                @Override
                public void failed(Throwable throwable) {
                    synchronized (lock) {
                        HttpProxy.LOG.log(Level.WARNING,
                            "Connection to server " + serverHost + ":" + serverPort + " failed", throwable);
                        closeAndReturnError("Cannot connect to the server");
                    }
                }

                @Override
                public void completed(Connection connection) {
                    HttpProxy.LOG.fine("Connected");
                }

                @Override
                public void updated(Connection connection) {
                }
            });
    }

    public void close() {
        synchronized (lock) {
            if (connection != null) {
                connection.close();
                connection = null;
            }
            try {
                outputStream.close();
            } catch (IOException e) {
                HttpProxy.LOG.log(Level.FINE, "Error while closing output stream", e);
                // only FINE: this exception can occur if the browser terminates connection;
                // no sense to duplicate messages - an attempt to write will also lead to an exception
            }
        }
    }

    public void closeAndReturnError(String message) {
        response.setStatus(500, message);
        // - must be before closing
        response.setContentType("text/plain");
        try {
            outputStream.write(Buffers.wrap(null, message));
        } catch (IOException e) {
            // ignore this problem
        }
        close();
        if (response.isSuspended()) {
            response.resume();
            HttpProxy.LOG.config("Response is resumed: " + message);
        } else {
            response.finish();
            HttpProxy.LOG.config("Response is finished: " + message);
        }
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        setConnection(ctx.getConnection());
        inputStream.notifyAvailable(new ReadHandler() {
            @Override
            public void onDataAvailable() throws Exception {
                synchronized (lock) {
                    sendData();
                }
                inputStream.notifyAvailable(this);
            }

            @Override
            public void onError(Throwable throwable) {
                HttpProxy.LOG.log(Level.WARNING, "Error while reading request", throwable);
                closeAndReturnError("Error while reading request");
            }

            @Override
            public void onAllDataRead() throws Exception {
                synchronized (lock) {
                    HttpProxy.LOG.config("All data ready");
                    sendData();
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            private void sendData() {
                final Buffer buffer = inputStream.readBuffer();
                final boolean finished = inputStream.isFinished();
                HttpProxy.LOG.config("Data ready, sending request to server: buffer " + buffer);
                final HttpContent httpContent = HttpContent.builder(requestToServerHeaders)
                    .content(buffer)
                    .last(finished)
                    .build();
                connection.write(httpContent);
                // - note: buffer will be destroyed by this call
            }
        });
        HttpProxy.LOG.config("Connected to " + serverHost + ":" + serverPort
            + "; starting sending request to server: header " + requestToServerHeaders);
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final HttpContent httpContent = ctx.getMessage();
        synchronized (lock) {
            if (firstReply) {
                final HttpResponsePacket httpHeader = (HttpResponsePacket) httpContent.getHttpHeader();
                response.setStatus(httpHeader.getHttpStatus());
                final MimeHeaders headers = httpHeader.getHeaders();
                for (String headerName : headers.names()) {
                    for (String headerValue : headers.values(headerName)) {
                        //TODO!! check identical names
                        response.addHeader(headerName, headerValue);
                    }
                }
                firstReply = false;
            }
        }

        // Events in notifyCanWrite are internally synchronized,
        // and all calls of onWritePossible will be in proper order.
        outputStream.notifyCanWrite(new WriteHandler() {
            @Override
            public void onWritePossible() throws Exception {
                synchronized (lock) {
                    outputStream.write(httpContent.getContent());
                    if (httpContent.isLast()) {
                        close();
                        response.resume();
                        HttpProxy.LOG.config("Response is resumed");
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                HttpProxy.LOG.log(Level.WARNING, t.getMessage(), t);
            }
        });
        return ctx.getStopAction();
    }

    private void setConnection(Connection connection) {
        synchronized (lock) {
            this.connection = connection;
        }
    }

    private static Map<String, List<String>> parseQueryOnly(Request request) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        final Parameters parameters = new Parameters();
        final Charset charset = lookupCharset(request.getCharacterEncoding());
        parameters.setHeaders(request.getRequest().getHeaders());
        parameters.setQuery(request.getRequest().getQueryStringDC());
        parameters.setEncoding(charset);
        parameters.setQueryStringEncoding(charset);
        parameters.handleQueryParameters();
        for (final String name : parameters.getParameterNames()) {
            final String[] values = parameters.getParameterValues(name);
            final List<String> valuesList = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    valuesList.add(value);
                }
            }
            result.put(name, valuesList);
        }
        return result;
    }


    private static Charset lookupCharset(final String enc) {
        Charset charset = Charsets.UTF8_CHARSET;
        // We don't use org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET here.
        // It is necessary to provide correct parsing GET and POST parameters, when encoding is not specified
        // (typical situation for POST, always for GET).
        // Note: it is the only place, where the proxy can need access to request parameters, so we can stay
        // the constant org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET unchanged.
        if (enc != null) {
            try {
                charset = Charsets.lookupCharset(enc);
            } catch (Exception e) {
                // ignore possible exception
            }
        }
        return charset;
    }
}
