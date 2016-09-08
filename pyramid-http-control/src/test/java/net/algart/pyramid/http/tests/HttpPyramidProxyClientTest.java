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

package net.algart.pyramid.http.tests;

import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.HttpPyramidProxyControl;

import java.io.IOException;
import java.nio.file.Paths;

public class HttpPyramidProxyClientTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.printf("Usage: %s host port CHECK|FINISH%n", HttpPyramidProxyClientTest.class.getName());
            return;
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String command = args[2];

        HttpPyramidProxyControl client = new HttpPyramidProxyControl(
            host,
            port,
            Paths.get(HttpPyramidConstants.DEFAULT_SYSTEM_COMMANDS_FOLDER),
            false);
        long t1 = System.nanoTime();
        try {
            switch (command.toLowerCase()) {
                case "check": {
                    final boolean alive = client.isProxyAlive(true);
                    System.out.println("Proxy is " + (alive ? "" : "not ") + "active");
                    break;
                }
                case "finish": {
                    client.finishService();
                    break;
                }
                default: {
                    throw new UnsupportedOperationException("Unknown command");
                }
            }
        } finally {
            long t2 = System.nanoTime();
            System.out.printf("Operation performed in %.5f seconds%n", (t2 - t1) * 1e-9);
        }
    }
}
