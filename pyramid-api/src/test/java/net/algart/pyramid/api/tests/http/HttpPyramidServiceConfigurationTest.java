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

import net.algart.pyramid.api.http.HttpPyramidServicesConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidServiceConfigurationTest {
    public static void main(String args[]) throws IOException {
        if (args.length == 0) {
            System.out.printf("Usage: %s projectRoot%n", HttpPyramidServiceConfigurationTest.class.getName());
            return;
        }
        final Path projectRoot = Paths.get(args[0]);
        final HttpPyramidServicesConfiguration configuration =
            HttpPyramidServicesConfiguration.readFromRootFolder(projectRoot);
        System.out.println(configuration);
        for (HttpPyramidServicesConfiguration.Process process : configuration.getProcesses().values()) {
            System.out.printf("%nInformation about process \"%s\"%n", process.getGroupId());
            System.out.printf("    ports: %s%n", process.allPorts());
            System.out.printf("    jreName: %s%n", process.jreName());
            System.out.printf("    workingDirectory: %s%n", process.workingDirectory());
            System.out.printf("    classPath: %s%n", process.classPath(false));
            System.out.printf("    vmOptions: %s%n", process.vmOptions());
            System.out.printf("    xmxOption: %s%n", process.xmxOption());
        }
    }
}
