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

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.http.HttpClientFilter;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    static final int CLIENT_CONNECTION_TIMEOUT = 30000;
    static final int CLIENT_READ_TIMEOUT = 30000;

    private static final Logger LOG = Logger.getLogger(HttpProxy.class.getName());

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

    public abstract ServerAddress getServer(Request request);

    private class HttpProxyHandler extends HttpHandler {
        @Override
        public void service(Request request, Response response) throws Exception {
            final ServerAddress server = getServer(request);
            final HttpRequestPacket httpRequestPacket = request.getRequest();
            final HttpClientFilter httpClientFilter = new HttpClientFilter();
            final ProxyClientProcessor clientProcessor = new ProxyClientProcessor(
                clientTransport, request, response, server.serverHost, server.serverPort);
            final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(httpClientFilter);
            clientFilterChainBuilder.add(clientProcessor);
            clientTransport.setProcessor(clientFilterChainBuilder.build());
            try {
                clientProcessor.connect();

            } catch (TimeoutException e) {
                //TODO!! return 500
                System.err.println("Timeout while reading target resource");
            } catch (ExecutionException e) {
                System.err.println("Error downloading the resource");
                e.getCause().printStackTrace();
            }
            response.suspend(7, TimeUnit.SECONDS, null, new TimeoutHandler() {
                @Override
                public boolean onTimeout(Response response) {
                    LOG.info("Timeout");
                    response.finish();
                    try {
                        clientProcessor.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //TODO!! corrent response; better timeout, corresponding ReadTask login
                    return true;
                }
            });
        }
    }
}

