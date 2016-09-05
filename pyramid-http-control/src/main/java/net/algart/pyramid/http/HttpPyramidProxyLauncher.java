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

package net.algart.pyramid.http;

import net.algart.http.proxy.HttpProxy;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.proxy.HttpPyramidProxyFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidProxyLauncher {
    public static void main(String[] args) throws InterruptedException {
        int startArgIndex = 0;
        boolean serviceMode = false;
        if (args.length > startArgIndex && args[startArgIndex].equals(
            HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG))
        {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length < startArgIndex + 1) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s configurationFolder%n", HttpPyramidProxyLauncher.class.getName());
            return;
        }
        final Path configurationFolder = Paths.get(args[startArgIndex]);
        final HttpProxy proxy;
        try {
            final HttpPyramidConfiguration configuration =
                HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder);
            final HttpPyramidProxyFactory proxyFactory = new HttpPyramidProxyFactory(configuration);
            proxy = proxyFactory.newProxy();
            proxy.start();
        } catch (Exception e) {
            if (serviceMode) {
                e.printStackTrace();
                System.exit(1);
            } else {
                HttpPyramidServerLauncher.printExceptionWaitForEnterKeyAndExit(e);
            }
            return;
            // - this operator will never executed
        }

        HttpPyramidServerLauncher.printWelcomeAndWaitForEnterKey();
        proxy.finish();
    }
}
