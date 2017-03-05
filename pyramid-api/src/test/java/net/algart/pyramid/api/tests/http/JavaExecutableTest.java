/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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

package net.algart.pyramid.api.tests.http;

import net.algart.pyramid.api.common.IllegalJREException;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaExecutableTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.printf("Usage: %s jreName specificServerConfigurationFile%n",
                JavaExecutableTest.class.getName());
            return;
        }
        final String jreName = args[0];
        final Path configurationFile = Paths.get(args[1]);
        final HttpPyramidSpecificServerConfiguration configuration =
            HttpPyramidSpecificServerConfiguration.readFromFile(configurationFile);
        System.out.printf("jreHome():%n  %s%n%n", configuration.jreHome());
        try {
            System.out.printf("javaExecutable() (current Java executable):%n");
//            System.setProperty("java.home", "/illegal-path");
            final Path currentJavaExecutable = configuration.javaExecutable();
            System.out.printf("  %s%n%n", currentJavaExecutable);
        } catch (IllegalJREException e) {
            e.printStackTrace(System.out);
        }
        try {
            System.out.printf("jreHome(jreName):%n");
            System.out.printf("  %s%n%n", configuration.jreHome(jreName));
        } catch (IllegalJREException e) {
            e.printStackTrace(System.out);
        }
        try {
            System.out.printf("javaExecutable(jreName):%n");
            System.out.printf("  %s%n%n", configuration.javaExecutable(jreName));
        } catch (IllegalJREException e) {
            e.printStackTrace(System.out);
        }
    }
}