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

package net.algart.pyramid.http.control;

import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidServersManager {
    private final HttpPyramidServersLauncher launcher;

    private HttpPyramidServersManager(
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.launcher = new HttpPyramidServersLauncher(configuration, specificServerConfiguration);
    }

    public static HttpPyramidServersManager newInstance(
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        return new HttpPyramidServersManager(configuration, specificServerConfiguration);
    }

    public static HttpPyramidServersManager newInstance(
        Path configurationFolder,
        Path specificServerConfigurationFile) throws IOException
    {
        return new HttpPyramidServersManager(
            HttpPyramidConfiguration.readFromFolder(configurationFolder),
            HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile));
    }

    public void startAll() throws IOException {
        launcher.startAll(false);
        //TODO!! start parallel thread for reviving sercices
    }

    public void stopAll() throws IOException {
        //TODO!! stop that parallel thread
        launcher.stopAll(false).waitFor();
        //TODO!! common timeout for all services
    }


    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s configurationFolder specificServerConfigurationFile%n",
                HttpPyramidServersManager.class.getName());
            return;
        }
        final Path configurationFolder = Paths.get(args[0]);
        final Path specificServerConfigurationFile = Paths.get(args[1]);
        final HttpPyramidServersManager manager = newInstance(configurationFolder, specificServerConfigurationFile);
        manager.startAll();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        System.out.printf("Press \"Ctrl+C\" or \"ENTER\" to stop all started servers...%n%n");
        try {
            System.in.read();
        } catch (IOException ignored) {
        }
        manager.stopAll();
    }
}
