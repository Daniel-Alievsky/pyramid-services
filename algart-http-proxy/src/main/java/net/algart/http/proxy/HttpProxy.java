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
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.grizzly.utils.Charsets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HttpProxy {

    /**
     * This URI requires the proxy service to ignore the usual work and just to return {@link #ALIVE_RESPONSE}
     * with status code 200 (OK).
     */
    public static final String ALIVE_STATUS_COMMAND = "/~~~~.net.algart.http.proxy.alive-status";
    public static final String ALIVE_RESPONSE = "Proxy-Alive";

    /**
     * The code string for command, requiring the proxy to finish. However, this implementation of proxy
     * does not process this command in any way. We recommend specific application to use this string
     * to inform the proxy process that it should be finished, but not through the usual HTTP protocol
     * on the given {@link #getProxyPort() proxy port} (in is unsafe) - for example, we recommend
     * to use some system-specific protocol based on the key files.
     */
    public static final String FINISH_COMMAND = "/~~~~.net.algart.http.proxy.finish";

    private static final String DEFAULT_PROXY_HOST = System.getProperty(
        "net.algart.http.proxy.proxyHost", "localhost");
    private static final int DEFAULT_READING_FROM_SERVER_TIMEOUT_IN_MS = Integer.getInteger(
        "net.algart.http.proxy.timeout", 120000);
    // - 2 minutes: some complex services can require essential time for first access to the data
    private static final boolean DEFAULT_CORRECT_MOVED_LOCATIONS = true;

    static final Logger LOG = Logger.getLogger(HttpProxy.class.getName());

    private final int proxyPort;
    private final HttpServerResolver serverResolver;
    private final HttpServerFailureHandler serverFailureHandler;
    private final TCPNIOTransport clientTransport;
    private final HttpServer proxyServer;

    private String proxyHost = DEFAULT_PROXY_HOST;
    private boolean ssl = false;
    private Path keystoreFile;
    private String keystorePassword;
    private String keyPassword;
    private boolean correctMovedLocations = DEFAULT_CORRECT_MOVED_LOCATIONS;
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

    public final int getProxyPort() {
        return proxyPort;
    }

    public final int getDefaultProxyPort() {
        return ssl ? 443 : 80;
    }

    public HttpServerResolver getServerResolver() {
        return serverResolver;
    }

    public HttpServerFailureHandler getServerFailureHandler() {
        return serverFailureHandler;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public HttpProxy setProxyHost(String proxyHost) {
        this.proxyHost = Objects.requireNonNull(proxyHost, "Null localHost argument");
        return this;
    }

    public boolean isSsl() {
        return ssl;
    }

    public Path getKeystoreFile() {
        return keystoreFile;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public HttpProxy enableSsl(Path keystoreFile, String keystorePassword) {
        return enableSsl(keystoreFile, keystorePassword, keystorePassword);
    }

    public HttpProxy enableSsl(Path keystoreFile, String keystorePassword, String keyPassword) {
        Objects.requireNonNull(keystoreFile);
        Objects.requireNonNull(keystorePassword);
        Objects.requireNonNull(keyPassword);
        this.keystoreFile = keystoreFile;
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
        this.ssl = true;
        return this;
    }

    public HttpProxy disableSsl() {
        this.ssl = false;
        return this;
    }

    public boolean isCorrectMovedLocations() {
        return correctMovedLocations;
    }

    public HttpProxy setCorrectMovedLocations(boolean correctMovedLocations) {
        this.correctMovedLocations = correctMovedLocations;
        return this;
    }

    public int getReadingFromServerTimeoutInMs() {
        return readingFromServerTimeoutInMs;
    }

    public HttpProxy setReadingFromServerTimeoutInMs(int readingFromServerTimeoutInMs) {
        if (readingFromServerTimeoutInMs <= 0) {
            throw new IllegalArgumentException("Zero or negative timeout for reading from server");
        }
        this.readingFromServerTimeoutInMs = readingFromServerTimeoutInMs;
        return this;
    }

    public final void start() throws IOException {
        synchronized (lock) {
            if (firstStart) {
                final NetworkListener listener = new NetworkListener(HttpProxy.class.getName(), proxyHost, proxyPort);
                if (ssl) {
                    final SSLContextConfigurator sslContextConfigurator = new SSLContextConfigurator();
                    sslContextConfigurator.setKeyStoreFile(keystoreFile.toString());
                    sslContextConfigurator.setKeyStorePass(keystorePassword);
                    sslContextConfigurator.setKeyPass(keyPassword);
                    final SSLEngineConfigurator sslEngineConfig = new SSLEngineConfigurator(
                        sslContextConfigurator.createSSLContext(true), false, false, false);
                    listener.setSecure(true);
                    listener.setSSLEngineConfig(sslEngineConfig);
                }
                this.proxyServer.addListener(listener);
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

    public String correctLocationFor3XXResponse(
        HttpServerAddress serverAddress,
        String location)
    {
        final URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            HttpProxy.LOG.log(Level.INFO, "Invalid location \"" + location + "\"in 301/302 server response", e);
            return location;
        }
        if (uri.getScheme() == null || uri.getHost()  == null) {
            return location;
            // - relative address
        }
        if (!uri.getScheme().equalsIgnoreCase("http")) {
            return location;
            // - this proxy cannot connect to server via other protocols
        }
        if (uri.getHost().equals(serverAddress.serverHost())
            && (uri.getPort() == -1 ? 80 : uri.getPort()) == serverAddress.serverPort())
        {
            try {
                return new URI(
                    ssl ? "https" : "http",
                    uri.getUserInfo(),
                    proxyHost,
                    proxyPort == getDefaultProxyPort() ? -1 : proxyPort,
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment()).toASCIIString();
            } catch (URISyntaxException e) {
                LOG.log(Level.SEVERE, "Strange exception while constructing new URI for \"" + location
                    + "\" in proxy object " + this, e);
                return location;
            }
        }
        return uri.toString();
    }

    @Override
    public String toString() {
        return "AlgART HTTP Proxy" + (ssl ? " (SSL)" : " (not SSL)")
            + " at " + proxyHost + ":" + proxyPort + " (server detector: " + serverResolver + ")";
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
                if (ALIVE_STATUS_COMMAND.equals(requestURI)) {
                    response.setContentType("text/plain; charset=utf-8");
                    response.setStatus(200, "OK");
                    response.getWriter().write(ALIVE_RESPONSE);
                    response.finish();
                    return;
                }
                final HttpClientFilter httpClientFilter = new HttpClientFilter();
                for (ContentEncoding encoding : httpClientFilter.getContentEncodings()) {
                    httpClientFilter.removeContentEncoding(encoding);
                    // - we must not decode encoded data, but need to pass them to the client
                }
                final Parameters queryParameters = parseQueryOnly(request);
                final HttpServerAddress server = serverResolver.findServer(requestURI, queryParameters);
                if (server.serverHost().equals(proxyHost) && server.serverPort() == proxyPort) {
                    throw new IllegalStateException("Infinite loop: server resolver returns host:port, "
                        + "identical to the proxy host:port " + server);
                }
                final ProxyClientProcessor clientProcessor = new ProxyClientProcessor(
                    HttpProxy.this, request, response, server);
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
                clientProcessor.suspendResponse();
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

