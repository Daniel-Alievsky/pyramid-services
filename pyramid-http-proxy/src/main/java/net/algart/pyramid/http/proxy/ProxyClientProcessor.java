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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

class ProxyClientProcessor extends BaseFilter {
    private final TCPNIOTransport clientTransport;
    private final HttpRequestPacket requestToServerHeaders;
    private final ByteBuffer requestToServerBody;
    private final Response response;
    private final String serverHost;
    private final int serverPort;
    private final NIOOutputStream outputStream;
    private volatile Connection connection = null;
    private volatile boolean firstReply = true;

    private final Object lock = new Object();

    ProxyClientProcessor(
        TCPNIOTransport clientTransport,
        Request request,
        Response response,
        HttpProxy httpProxy)
    {
        this.clientTransport = clientTransport;
        this.response = response;
        this.outputStream = response.getNIOOutputStream();

        InputBuffer inputBuffer = request.getInputBuffer();
        this.requestToServerBody = inputBuffer.readBuffer().toByteBuffer();
        request.replayPayload(Buffers.wrap(null, cloneByteBuffer(requestToServerBody)));
        // - clone necessary, because probable reading parameters from request in getServer
        // method below damages the content of the buffer passed to replayPayload
        final HttpProxy.ServerAddress server = httpProxy.getServer(request);
        this.serverHost = server.serverHost();
        this.serverPort = server.serverPort();

        final HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
        builder.protocol(Protocol.HTTP_1_1);
        builder.method(request.getMethod());
        builder.uri(request.getRequestURI());
        builder.query(request.getQueryString());
        builder.contentType(request.getContentType());
        builder.contentLength(request.getContentLength());
        builder.chunked(false); //TODO!! - Q: is it correct?
        builder.removeHeader("Host");
        builder.header("Host", serverHost + ":" + serverPort);
        this.requestToServerHeaders = builder.build();
    }

    public void connect() throws InterruptedException, ExecutionException, TimeoutException {
        Future<Connection> connectFuture = clientTransport.connect(serverHost, serverPort);
        this.connection = connectFuture.get(HttpProxy.CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    public void close() throws IOException {
        synchronized (lock) {
            //TODO!! synchronize also access to connection/outputStream
            if (connection != null) {
                connection.close();
                connection = null;
            }
            outputStream.close();
        }
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        System.out.println("Connected to " + serverHost + ":" + serverPort);

        synchronized (lock) {
            final HttpContent.Builder contentBuilder = HttpContent.builder(requestToServerHeaders);
            Buffer buffer = Buffers.wrap(null, requestToServerBody);
            contentBuilder.content(buffer);
            contentBuilder.last(true);
            //TODO!! Q - is it necessary?
            HttpContent content = contentBuilder.build();
//            System.out.println("Sending request to server: header " + content.getHttpHeader());
//            System.out.println("Sending request to server: buffer " + buffer);
            ctx.write(content);
            return ctx.getStopAction();
        }
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final HttpContent httpContent = ctx.getMessage();
        final byte[] bytes;
        synchronized (lock) {
            bytes = byteBufferToArray(httpContent.getContent().toByteBuffer());
//            System.out.printf("first: %s, isHeader: %s, isLast: %s, counter: %d%n",
//                firstReply, httpContent.isHeader(), httpContent.isLast(), currentCounter);
            if (firstReply) { // TODO!! Q: is it correct?
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
//            System.out.printf("ByteBuffer limit %d, position %d, remaining %d%n",
//                byteBuffer.limit(), byteBuffer.position(), byteBuffer.remaining());
        }

        // It seems that events in notifyCanWrite are internally synchronized,
        // and all calls of onWritePossible will be in proper order.
        outputStream.notifyCanWrite(new WriteHandler() {
            @Override
            public void onWritePossible() throws Exception {
//                    System.out.printf("Sending %d bytes (counter=%d): starting...%n", bytesToClient.length, currentCounter);
//                    Thread.sleep(new java.util.Random().nextInt(1000));
                synchronized (lock) {
                    // System.out.printf("Sending %d bytes (counter=%d)...%n", bytes.length, currentCounter);
                    outputStream.write(bytes);
                    if (httpContent.isLast()) {
                        //TODO!! Q: is it correct?
                        outputStream.close();
                        connection.close();
                        if (response.isSuspended()) {
                            response.resume();
                        } else {
                            response.finish();
                        }
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


    private static ByteBuffer cloneByteBuffer(ByteBuffer byteBuffer) {
        byteBuffer = byteBuffer.duplicate();
        ByteBuffer result = ByteBuffer.allocate(byteBuffer.remaining());
        result.duplicate().put(byteBuffer);
        return result;
    }

    private static byte[] byteBufferToArray(ByteBuffer byteBuffer) {
        byteBuffer = byteBuffer.duplicate();
        final byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

}
