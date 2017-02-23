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

package net.algart.pyramid.http.server;

import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidServicesConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class HttpPyramidServer {
    static {
        if (!HttpPyramidServer.class.getName().equals(HttpPyramidConstants.HTTP_PYRAMID_SERVER_CLASS_NAME)) {
            throw new AssertionError("Invalid constant DEFAULT_HTTP_PYRAMID_SERVER_CLASS_NAME");
        }
    }

    private static final Logger LOG = Logger.getLogger(HttpPyramidServer.class.getName());

    private final HttpPyramidServicesConfiguration.Process processConfiguration;
    private final HttpPyramidSpecificServerConfiguration specificServerConfiguration;
    private volatile List<HttpPyramidService> services = null;

    public HttpPyramidServer(
        HttpPyramidServicesConfiguration.Process processConfiguration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.processConfiguration = Objects.requireNonNull(processConfiguration);
        this.specificServerConfiguration = Objects.requireNonNull(specificServerConfiguration);
    }

    public void start() throws Exception {
        final List<HttpPyramidService> services = new ArrayList<>();
        try {
            for (HttpPyramidServicesConfiguration.Service serviceConfiguration : processConfiguration.getServices()) {
                final String planePyramidFactory = serviceConfiguration.getPlanePyramidFactory();
                final int port = serviceConfiguration.getPort();
                final Class<?> factoryClass = Class.forName(planePyramidFactory);
                final PlanePyramidFactory factory = (PlanePyramidFactory) factoryClass.newInstance();
                factory.initializeConfiguration(
                    PyramidApiTools.configurationToJson(serviceConfiguration.toJsonString()));
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
        LOG.info("Finishing pyramid server for ports " + processConfiguration.allPorts());
    }

    public HttpPyramidServicesConfiguration.Process getProcessConfiguration() {
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
                        + "or wait when it will be finished normally by the system command...%n%n");
                    System.in.read();
                } catch (Exception ignored) {
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
            processConfiguration.parentConfiguration().systemCommandsFolder())
            .setSpecificConfiguration(specificServerConfiguration);
    }

    protected void addHandlers(HttpPyramidService service) {
        service.addStandardHandlers();
    }

    public static void main(String[] args) throws InterruptedException {
        int startArgIndex = 0;
        boolean serviceMode = false;
        String groupId = null;
        if (args.length > startArgIndex && args[startArgIndex].equals(
            HttpPyramidConstants.HTTP_PYRAMID_SERVICE_MODE_FLAG))
        {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length > startArgIndex && args[startArgIndex].startsWith("--groupId=")) {
            groupId = args[startArgIndex].substring("--groupId=".length());
            startArgIndex++;
        }
        if (args.length < startArgIndex + 2 || groupId == null) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s --groupId=xxxxxxx projectRoot specificServerConfigurationFile%n"
                , HttpPyramidServer.class.getName());
            System.out.printf("or%n");
            System.out.printf("    %s --groupId=xxxxxxx projectRoot somePath/.global-configuration.json "
                + "somePath/.format1.json somePath/.format2.json ... specificServerConfigurationFile%n",
                HttpPyramidServer.class.getName());
            if (groupId == null) {
                System.out.printf("--groupId is not specified%n");
            }
            return;
        }
        final Path projectRoot = Paths.get(args[startArgIndex]);
        final Path specificServerConfigurationFile = Paths.get(args[args.length - 1]);
        // Note: specificServerConfigurationFile is used for finding configRootDir and configFileName
        final HttpPyramidServicesConfiguration configuration;
        final HttpPyramidServer server;
        try {
            if (args.length > startArgIndex + 2) {
                final Path globalConfigurationFile = Paths.get(args[startArgIndex + 1]);
                final List<Path> files = new ArrayList<>();
                for (int index = startArgIndex + 2; index < args.length - 1; index++) {
                    files.add(Paths.get(args[index]));
                }
                configuration = HttpPyramidServicesConfiguration.readFromFiles(
                    projectRoot, globalConfigurationFile, files);
            } else {
                configuration = HttpPyramidServicesConfiguration.readFromRootFolder(projectRoot);
            }
            final HttpPyramidServicesConfiguration.Process process = configuration.getProcess(groupId);
            if (process == null) {
                throw new IllegalArgumentException("Process with groupId \"" + groupId + "\" is not found");
            }
            final HttpPyramidSpecificServerConfiguration specificServerConfiguration =
                HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile);
            server = new HttpPyramidServer(process, specificServerConfiguration);
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
        System.exit(0);
        // - force quick exiting the server after "finish" command
        // (maybe, there is some "heavy" computations are performed now in some threads,
        // but they are absolutely useless after "finish" command)
    }
}
