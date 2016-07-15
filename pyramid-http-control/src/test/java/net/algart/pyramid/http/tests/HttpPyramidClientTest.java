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

import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.http.HttpPyramidServiceControl;
import net.algart.pyramid.http.api.HttpPyramidConstants;

import java.io.IOException;
import java.nio.file.Paths;

public class HttpPyramidClientTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.printf("Usage: %s host port [pyramidId]%n", HttpPyramidClientTest.class.getName());
            return;
        }
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String pyramidId = args.length >= 3 ? args[2] : null;

        HttpPyramidServiceControl client = new HttpPyramidServiceControl(
            host,
            port,
            Paths.get(HttpPyramidConstants.DEFAULT_SYSTEM_COMMANDS_FOLDER),
            false);
        final boolean alive;
        long t1 = System.nanoTime();
        try {
            alive = client.isServiceAlive(true);
        } finally {
            long t2 = System.nanoTime();
            System.out.printf("Status checked in %.5f seconds%n", (t2 - t1) * 1e-9);
        }
        if (alive) {
            System.out.printf("Service is alive; checking pyramid %s...%n", pyramidId);
            if (pyramidId != null) {
                t1 = System.nanoTime();
                try {
                    final PlanePyramidInformation information = client.information(pyramidId);
                    System.out.printf("Pyramid information:%n%s%n", information);
                } finally {
                    long t2 = System.nanoTime();
                    System.out.printf("Information request performed in %.5f seconds%n", (t2 - t1) * 1e-9);
                }
            }
        } else {
            System.out.println("Service is not active");
        }
    }
}
