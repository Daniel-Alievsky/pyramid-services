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

package net.algart.pyramid.http.proxy.tests;

import net.algart.pyramid.http.proxy.HttpProxy;

import java.io.IOException;
import java.util.Map;

public class HttpProxyTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: " + HttpProxyTest.class.getName() + " server-host server-port proxy-port");
            return;
        }
        final String serverHost = args[0];
        final int serverPort = Integer.parseInt(args[1]);
        final int proxyPort = Integer.parseInt(args[2]);

        final HttpProxy proxy = new HttpProxy(proxyPort) {
            @Override
            public ServerAddress getServer(Map<String, String> queryParameters) {
                return new ServerAddress(serverHost, serverPort);
            }
        };
        proxy.start();
        System.out.println("Press ENTER to stop the proxy server...");
        System.in.read();
        proxy.finish();
        System.out.println("Proxy server finished");

    }
}
