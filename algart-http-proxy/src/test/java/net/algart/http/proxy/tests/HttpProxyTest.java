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

package net.algart.http.proxy.tests;

import net.algart.http.proxy.HttpProxy;
import net.algart.http.proxy.HttpServerAddress;
import net.algart.http.proxy.HttpServerDetector;
import net.algart.http.proxy.HttpServerFailureHandler;
import org.glassfish.grizzly.http.util.Parameters;

import java.io.IOException;

public class HttpProxyTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: " + HttpProxyTest.class.getName()
                + " server-host server-port proxy-port [timeout [localhost]]");
            return;
        }
        final String serverHost = args[0];
        final int serverPort = Integer.parseInt(args[1]);
        final int proxyPort = Integer.parseInt(args[2]);
        final Integer timeout = args.length > 3 ? Integer.valueOf(args[3]) : null;
        final String localhost = args.length > 4 ? args[4] : null;
        final HttpServerAddress serverAddress = new HttpServerAddress(serverHost, serverPort);

        final HttpProxy proxy = new HttpProxy(proxyPort,
            new HttpServerDetector() {
                @Override
                public HttpServerAddress getServer(String requestURI, Parameters queryParameters) {
                    System.out.printf("Test proxy forwards %s to %s%n", requestURI, serverAddress);
                    return serverAddress;
                }

                @Override
                public String toString() {
                    return "debugging detector for fixed address " + serverAddress;
                }
            },
            new HttpServerFailureHandler() {
            });
        if (timeout != null) {
            proxy.setReadingFromServerTimeoutInMs(timeout);
        }
        if (localhost != null) {
            proxy.setLocalHost(localhost);
        }
        proxy.start();
        System.out.println("Press ENTER to stop the proxy server...");
        System.in.read();
        proxy.finish();
        System.out.println("Proxy server finished");

    }
}
