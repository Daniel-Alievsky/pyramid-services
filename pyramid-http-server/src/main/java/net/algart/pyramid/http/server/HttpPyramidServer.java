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

package net.algart.pyramid.http.server;

import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.http.api.HttpPyramidServiceConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HttpPyramidServer {
    private final HttpPyramidServiceConfiguration.Process processConfiguration;
    private volatile List<HttpPyramidService> services = null;

    public HttpPyramidServer(HttpPyramidServiceConfiguration.Process processConfiguration) {
        this.processConfiguration = Objects.requireNonNull(processConfiguration);
    }

    public void start() throws Exception {
        final List<HttpPyramidService> services = new ArrayList<>();
        try {
            for (HttpPyramidServiceConfiguration.Service serviceConfiguration : processConfiguration.getServices()) {
                final String planePyramidFactory = serviceConfiguration.getPlanePyramidFactory();
                final String planePyramidFactoryConfiguration = serviceConfiguration.getPlanePyramidFactoryConfiguration();
                final int port = serviceConfiguration.getPort();
                final Class<?> factoryClass = Class.forName(planePyramidFactory);
                final PlanePyramidFactory factory = (PlanePyramidFactory) factoryClass.newInstance();
                if (planePyramidFactoryConfiguration != null) {
                    factory.initializeConfiguration(planePyramidFactoryConfiguration);
                }
                final HttpPyramidService service = newService(factory, port);
                addHandlers(service);
                services.add(service);
                service.start();
            }
        } catch (Exception | Error e) {
            for (final HttpPyramidService service : services) {
                service.finish();
            }
            throw e;
        }
        this.services = services;
    }

    public void waitForFinish() {
        for (final HttpPyramidService service : services) {
            new Thread() {
                @Override
                public void run() {
                    service.waitForFinish();
                }
            }.start();
        }
    }

    protected HttpPyramidService newService(PlanePyramidFactory factory, int port) throws IOException {
        return new HttpPyramidService(factory, port);
    }

    protected void addHandlers(HttpPyramidService service) {
        service.addStandardHandlers();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s groupId configurationFolder%n", HttpPyramidServer.class.getName());
            System.out.printf("or%n");
            System.out.printf("    %s groupId somePath/.global-configuration.json somePath/.format1.json "
                + "somePath/.format2.json ...%n", HttpPyramidServer.class.getName());
            return;
        }
        final String groupId = args[0];
        final Path folderOrFile = Paths.get(args[1]);
        final HttpPyramidServiceConfiguration configuration;
        if (Files.isRegularFile(folderOrFile)) {
            final List<Path> files = new ArrayList<>();
            for (int index = 2; index < args.length; index++) {
                files.add(Paths.get(args[index]));
            }
            configuration = HttpPyramidServiceConfiguration.readConfigurationFromFiles(folderOrFile, files);
        } else {
            configuration = HttpPyramidServiceConfiguration.readConfigurationFromFolder(folderOrFile);
        }
        final HttpPyramidServiceConfiguration.Process process = configuration.getProcess(groupId);
        if (process == null) {
            throw new IllegalArgumentException("Process with groupId \"" + groupId + "\" is not found");
        }
        final HttpPyramidServer server = new HttpPyramidServer(process);
//        System.out.println(configuration);
        server.start();
        server.waitForFinish();
    }
}
