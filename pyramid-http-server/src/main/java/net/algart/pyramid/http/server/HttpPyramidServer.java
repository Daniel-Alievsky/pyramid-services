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
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HttpPyramidServer {
    static {
        if (!HttpPyramidServer.class.getName().equals(HttpPyramidConstants.HTTP_PYRAMID_SERVER_CLASS_NAME)) {
            throw new AssertionError("Invalid constant DEFAULT_HTTP_PYRAMID_SERVER_CLASS_NAME");
        }
    }

    private final HttpPyramidConfiguration.Process processConfiguration;
    private volatile List<HttpPyramidService> services = null;

    public HttpPyramidServer(HttpPyramidConfiguration.Process processConfiguration) {
        this.processConfiguration = Objects.requireNonNull(processConfiguration);
    }

    public void start() throws Exception {
        final List<HttpPyramidService> services = new ArrayList<>();
        try {
            for (HttpPyramidConfiguration.Service serviceConfiguration : processConfiguration.getServices()) {
                final String planePyramidFactory = serviceConfiguration.getPlanePyramidFactory();
                final String planePyramidFactoryConfiguration =
                    serviceConfiguration.getPlanePyramidFactoryConfiguration();
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

    public void waitForFinishAndProcessSystemCommands() throws InterruptedException {
        final List<Thread> waitingThreads = new ArrayList<>();
        for (final HttpPyramidService service : services) {
            final Thread thread = new Thread() {
                @Override
                public void run() {
                    service.waitForFinishAndProcessSystemCommands();
                }
            };
            thread.start();
            waitingThreads.add(thread);
        }
        for (final Thread thread : waitingThreads) {
            thread.join();
        }
    }

    public HttpPyramidConfiguration.Process getProcessConfiguration() {
        return processConfiguration;
    }

    public List<HttpPyramidService> getServices() {
        return services;
    }

    public void printWelcomeAndKillOnEnterKey() {
        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    // - to be on the side: little pause to allow services to show their logging messages
                    System.out.printf("%nThe server successfully started on ports %s%n",
                        processConfiguration.allPorts());
                    System.out.printf("Press \"Ctrl+C\" or \"ENTER\" to kill the server, "
                        + "or wait when it will be finished normally by HTTP command...%n%n");
                    System.in.read();
                } catch (Exception e) {
                    // should not occur
                }
                System.exit(2);
            }
        };
        thread.setDaemon(true);
        // - this thred must not prevent normal exiting
        thread.start();
    }

    public static void printExceptionWaitForEnterKeyAndExit(Throwable exception) {
        System.err.printf("%nSome problems occured while starting services! Error message:%n%s%n%nStack trace:%n",
            exception.getMessage());
        exception.printStackTrace();
        System.err.printf("%nPress \"ENTER\" to exit...%n");
        try {
            System.in.read();
        } catch (IOException e) {
            // should not occur
            e.printStackTrace();
        }
        System.exit(1);
    }

    protected HttpPyramidService newService(PlanePyramidFactory factory, int port) throws IOException {
        return new HttpPyramidService(
            factory,
            port,
            processConfiguration.parentConfiguration().systemCommandsFolder());
    }

    protected void addHandlers(HttpPyramidService service) {
        service.addStandardHandlers();
    }

    public static void main(String[] args) throws InterruptedException {
        int startArgIndex = 0;
        boolean serviceMode = false;
        String groupId = null;
        if (args.length > startArgIndex && args[startArgIndex].equals(
            HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG))
        {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].startsWith("--groupId=")) {
            groupId = args[startArgIndex].substring("--groupId=".length());
            startArgIndex++;
        }
        if (args.length < startArgIndex + 1 || groupId == null) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s --groupId=com.xxxxxxx configurationFolder%n", HttpPyramidServer.class.getName());
            System.out.printf("or%n");
            System.out.printf("    %s --groupId=com.xxxxxxx configurationFolder somePath/.global-configuration.json "
                + "somePath/.format1.json somePath/.format2.json ...%n",
                HttpPyramidServer.class.getName());
            if (groupId == null) {
                System.out.printf("--groupId is not specified%n");
            }
            return;
        }
        final Path configurationFolder = Paths.get(args[startArgIndex]);
        final HttpPyramidConfiguration configuration;
        final HttpPyramidServer server;
        try {
            if (args.length > startArgIndex + 1) {
                final Path globalConfigurationFile = Paths.get(args[startArgIndex + 1]);
                final List<Path> files = new ArrayList<>();
                for (int index = startArgIndex + 2; index < args.length; index++) {
                    files.add(Paths.get(args[index]));
                }
                configuration = HttpPyramidConfiguration.readConfigurationFromFiles(
                    configurationFolder, globalConfigurationFile, files);
            } else {
                configuration = HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder);
            }
            final HttpPyramidConfiguration.Process process = configuration.getProcess(groupId);
            if (process == null) {
                throw new IllegalArgumentException("Process with groupId \"" + groupId + "\" is not found");
            }
            server = new HttpPyramidServer(process);
            server.start();
        } catch (Exception e) {
            if (serviceMode) {
                e.printStackTrace();
                System.exit(1);
            } else {
                printExceptionWaitForEnterKeyAndExit(e);
            }
            return;
            // - this operator will never executed
        }
        if (!serviceMode) {
            server.printWelcomeAndKillOnEnterKey();
        }
        server.waitForFinishAndProcessSystemCommands();
    }
}
