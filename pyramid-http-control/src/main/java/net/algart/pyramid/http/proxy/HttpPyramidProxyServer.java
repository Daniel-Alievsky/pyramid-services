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

package net.algart.pyramid.http.proxy;

import net.algart.http.proxy.HttpProxy;
import net.algart.http.proxy.HttpServerAddress;
import net.algart.http.proxy.HttpServerFailureHandler;
import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

public final class HttpPyramidProxyServer {

    static final Logger LOG = Logger.getLogger(HttpPyramidProxyServer.class.getName());

    private final HttpPyramidConfiguration serviceConfiguration;
    private final HttpProxy proxy;

    public HttpPyramidProxyServer(
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        Objects.requireNonNull(configuration, "Null configuration of pyramid services");
        Objects.requireNonNull(specificServerConfiguration, "Null configuration for specific server");
        if (!specificServerConfiguration.hasProxy()) {
            throw new IllegalArgumentException("Proxy is not used in this configuration");
        }
        this.serviceConfiguration = configuration;
        this.proxy = new HttpProxy(
            specificServerConfiguration.getProxySettings().getProxyPort(),
            new StandardPyramidServerResolver(configuration, specificServerConfiguration),
            new HttpServerFailureHandler() {
                @Override
                public void onConnectionFailed(HttpServerAddress address, Throwable throwable) {
                    //In future, it can restart the server, reserving 2nd instance to be on the safe side
                    LOG.warning("Cannot connect to " + address + ": maybe the service crashed");
                }

                @Override
                public void onServerTimeout(HttpServerAddress address, String requestURI) {
                    LOG.warning("Timeout while accessing " + address + ": maybe the service crashed");
                }
            });

    }

    public void start() throws IOException {
        proxy.start();
    }

    public void waitForFinish() throws InterruptedException {
        try {
            for (; ; ) {
                Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY);
                if (Files.exists(finishKeyFile())) {
                    try {
                        proxy.finish();
                    } finally {
                        Files.deleteIfExists(finishKeyFile());
                    }
                    break;
                }
            }
            Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY_AFTER_FINISH);
            // - additional delay is to be on the safe side: allow all tasks to be correctly finished
        } catch (IOException e) {
            throw new IOError(e);
        }
        LOG.info("Finishing " + proxy);
    }

    private Path finishKeyFile() {
        return HttpPyramidApiTools.keyFile(
            serviceConfiguration.systemCommandsFolder(),
            HttpProxy.FINISH_COMMAND,
            proxy.getProxyPort());
    }

    private void printWelcomeAndKillOnEnterKey() {
        final Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    // - to be on the side: little pause to allow services to show their logging messages
                    System.out.printf("%nThe pyramid proxy successfully started%n");
                    System.out.printf("Press \"Ctrl+C\" or \"ENTER\" to kill the server, "
                        + "or wait when it will be finished normally by the system command...%n%n");
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

    private static void printExceptionWaitForEnterKeyAndExit(Throwable exception) {
        System.err.printf("%nSome problems occured while starting proxy! Error message:%n%s%n%nStack trace:%n",
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

    public static void main(String[] args) throws InterruptedException {
        int startArgIndex = 0;
        boolean serviceMode = false;
        if (args.length > startArgIndex && args[startArgIndex].equals(
            HttpPyramidConstants.HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG))
        {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length < startArgIndex + 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s configurationFolder specificServerConfigurationFile%n",
                HttpPyramidProxyServer.class.getName());
            return;
        }
        final Path configurationFolder = Paths.get(args[startArgIndex]);
        final Path specificServerConfigurationFile = Paths.get(args[startArgIndex + 1]);
        final HttpPyramidProxyServer server;
        try {
            final HttpPyramidConfiguration serviceConfiguration =
                HttpPyramidConfiguration.readFromFolder(configurationFolder);
            final HttpPyramidSpecificServerConfiguration specificServerConfiguration =
                HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile);
            server = new HttpPyramidProxyServer(serviceConfiguration, specificServerConfiguration);
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
        server.waitForFinish();
    }
}
