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

import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.Charsets;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HttpProxy {

    private static final String DEFAULT_LOCAL_HOST = System.getProperty(
        "net.algart.http.proxy.localHost", "localhost");
    private static final int DEFAULT_READING_FROM_SERVER_TIMEOUT_IN_MS = Integer.getInteger(
        "net.algart.http.proxy.timeout", 60000);

    static final Logger LOG = Logger.getLogger(HttpProxy.class.getName());

    private final int proxyPort;
    private final HttpServerResolver serverResolver;
    private final HttpServerFailureHandler serverFailureHandler;
    private final TCPNIOTransport clientTransport;
    private final HttpServer proxyServer;

    private String localHost = DEFAULT_LOCAL_HOST;
    private int readingFromServerTimeoutInMs = DEFAULT_READING_FROM_SERVER_TIMEOUT_IN_MS;
    private volatile boolean firstStart = true;

    private final Object lock = new Object();

    public HttpProxy(
        int proxyPort,
        HttpServerResolver serverResolver,
        HttpServerFailureHandler serverFailureHandler)
    {
        if (proxyPort <= 0) {
            throw new IllegalArgumentException("Zero or negative port " + proxyPort);
        }
        this.proxyPort = proxyPort;
        this.serverResolver = Objects.requireNonNull(serverResolver,
            "Server resolver must be specified to determine necessary server host and port");
        this.serverFailureHandler = Objects.requireNonNull(serverFailureHandler,
            "Handler of failures while working with server must be specified (may be trivial)");
        this.clientTransport = TCPNIOTransportBuilder.newInstance().build();
        this.proxyServer = new HttpServer();
        this.proxyServer.getServerConfiguration().addHttpHandler(new HttpProxyHandler());
    }

    public String getLocalHost() {
        return localHost;
    }

    public void setLocalHost(String localHost) {
        this.localHost = Objects.requireNonNull(localHost, "Null localHost argument");
    }

    public int getReadingFromServerTimeoutInMs() {
        return readingFromServerTimeoutInMs;
    }

    public void setReadingFromServerTimeoutInMs(int readingFromServerTimeoutInMs) {
        if (readingFromServerTimeoutInMs <= 0) {
            throw new IllegalArgumentException("Zero or negative timeout for reading from server");
        }
        this.readingFromServerTimeoutInMs = readingFromServerTimeoutInMs;
    }

    public final void start() throws IOException {
        synchronized (lock) {
            if (firstStart) {
                this.proxyServer.addListener(new NetworkListener(HttpProxy.class.getName(), localHost, proxyPort));
                firstStart = false;
            }
            LOG.info("Starting " + this);
            clientTransport.start();
            proxyServer.start();
        }
    }

    public final void finish() {
        synchronized (lock) {
            LOG.log(Level.INFO, "Shutting down pyramid service...");
            proxyServer.shutdown();
            clientTransport.shutdown();
        }
    }

    public final int getProxyPort() {
        return proxyPort;
    }

    @Override
    public String toString() {
        return "AlgART HTTP Proxy at " + localHost + ":" + proxyPort + " (server detector: " + serverResolver + ")";
    }

    private class HttpProxyHandler extends HttpHandler {
        @Override
        public void service(Request request, Response response) {
//            System.out.println("Proxying " + request.getRequestURI() + " - " + request.getRequestURL());
//            System.out.println("  port: " + request.getServerPort());
//            System.out.println("  remote port: " + request.getRemotePort());
//            System.out.println("  path info: " + request.getPathInfo());
//            System.out.println("  context: " + request.getContextPath());
//            System.out.println("  query: " + request.getQueryString());
//            System.out.println("  headers:");
//            for (String headerName : request.getHeaderNames()) {
//                for (String headerValue : request.getHeaders(headerName)) {
//                    System.out.printf("    %s=%s%n", headerName, headerValue);
//                }
//            }

            try {
                final String requestURI = request.getRequestURI();
                final HttpClientFilter httpClientFilter = new HttpClientFilter();
                for (ContentEncoding encoding : httpClientFilter.getContentEncodings()) {
                    httpClientFilter.removeContentEncoding(encoding);
                    // - we must not decode encoded data, but need to pass them to the client
                }
                final Parameters queryParameters = parseQueryOnly(request);
                final HttpServerAddress server = serverResolver.findServer(requestURI, queryParameters);
                final ProxyClientProcessor clientProcessor = new ProxyClientProcessor(
                    request, response, server, serverFailureHandler);
                LOG.config("Proxying " + requestURI + " to " + server);
//            System.out.println("    Parameters: " + HttpServerDetector.BasedOnMap.toMap(queryParameters));
//              - note: this call requires some resources for new Map and must be commented usually
                final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
                clientFilterChainBuilder.add(new TransportFilter());
                clientFilterChainBuilder.add(httpClientFilter);
                clientFilterChainBuilder.add(clientProcessor);
                final FilterChain filterChain = clientFilterChainBuilder.build();
                final TCPNIOConnectorHandler connectorHandler =
                    TCPNIOConnectorHandler.builder(clientTransport).processor(filterChain).build();
                response.suspend(readingFromServerTimeoutInMs, TimeUnit.MILLISECONDS, null, new TimeoutHandler() {
                    @Override
                    public boolean onTimeout(Response responseInTimeout) {
                        clientProcessor.closeAndReturnError("Timeout while waiting for the server response");
                        try {
                            serverFailureHandler.onServerTimeout(server, requestURI);
                        } catch (Throwable t) {
                            LOG.log(Level.SEVERE, "Problem in onServerTimeout (" + this + ")", t);
                        }
                        return true;
                    }
                });
                clientProcessor.requestConnectionToServer(connectorHandler);
            } catch (Throwable t) {
                response.setStatus(500, "AlgART Proxy request error");
                response.setContentType("text/plain");
                try {
                    response.getWriter().write(String.format("AlgART Proxy request error%n"));
                } catch (IOException ignored) {
                }
                // Minimizing information about an exception
                LOG.log(Level.SEVERE, "Problem in " + HttpProxy.this, t);
            }
        }
    }

    private static Parameters parseQueryOnly(Request request) {
        final Parameters parameters = new Parameters();
        final Charset charset = lookupCharset(request.getCharacterEncoding());
        parameters.setHeaders(request.getRequest().getHeaders());
        parameters.setQuery(request.getRequest().getQueryStringDC());
        parameters.setEncoding(charset);
        parameters.setQueryStringEncoding(charset);
        parameters.handleQueryParameters();
        return parameters;
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

