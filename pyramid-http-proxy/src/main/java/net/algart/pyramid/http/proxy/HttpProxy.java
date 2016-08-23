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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class HttpProxy {
    public static class ServerAddress {
        private final String serverHost;
        private final int serverPort;

        public ServerAddress(String serverHost, int serverPort) {
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
    }

    static final String LOCAL_HOST = System.getProperty(
        "net.algart.pyramid.http.localHost", "localhost");
    static final int CLIENT_READ_TIMEOUT = 30000;
    // - 5 minutes

    static final Logger LOG = Logger.getLogger(HttpProxy.class.getName());

    private final int proxyPort;
    private final TCPNIOTransport clientTransport;
    private final HttpServer proxyServer;

    public HttpProxy(int proxyPort) {
        if (proxyPort <= 0) {
            throw new IllegalArgumentException("Zero or negative port " + proxyPort);
        }
        this.proxyPort = proxyPort;
        this.clientTransport = TCPNIOTransportBuilder.newInstance().build();
        this.proxyServer = new HttpServer();
        this.proxyServer.addListener(new NetworkListener(HttpProxy.class.getName(), LOCAL_HOST, proxyPort));
        this.proxyServer.getServerConfiguration().addHttpHandler(new HttpProxyHandler());
    }

    public final void start() throws IOException {
        LOG.info("Starting " + this);
        clientTransport.start();
        proxyServer.start();
    }

    public final void finish() {
        LOG.log(Level.INFO, "Shutting down pyramid service...");
        proxyServer.shutdown();
        clientTransport.shutdown();
    }

    public final int getProxyPort() {
        return proxyPort;
    }

    public abstract ServerAddress getServer(Map<String, String> queryParameters);

    private class HttpProxyHandler extends HttpHandler {
        @Override
        public void service(Request request, Response response) throws Exception {
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

            final HttpClientFilter httpClientFilter = new HttpClientFilter();
            for (ContentEncoding encoding : httpClientFilter.getContentEncodings()) {
                httpClientFilter.removeContentEncoding(encoding);
                // - we must not decode encoded data, but need to pass them to the client
            }
            final ProxyClientProcessor clientProcessor = new ProxyClientProcessor(request, response, HttpProxy.this);
            final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(httpClientFilter);
            clientFilterChainBuilder.add(clientProcessor);
            final FilterChain filterChain = clientFilterChainBuilder.build();
            final TCPNIOConnectorHandler connectorHandler =
                TCPNIOConnectorHandler.builder(clientTransport).processor(filterChain).build();
            clientProcessor.setConnectorHandler(connectorHandler);
            response.suspend(HttpProxy.CLIENT_READ_TIMEOUT, TimeUnit.MILLISECONDS, null, new TimeoutHandler() {
                @Override
                public boolean onTimeout(Response response) {
                    LOG.info("Timeout while waiting for the server response for " + request.getRequestURL());
                    clientProcessor.closeAndReturnError("Proxy timeout");
                    response.resume();
                    return true;
                }
            });
            clientProcessor.requestConnectionToServer();
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
        // Note: we don't use org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET here.
        // It is necessary to provide correct parsing GET and POST parameters, when encoding is not specified
        // (typical situation for POST, always for GET).
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

