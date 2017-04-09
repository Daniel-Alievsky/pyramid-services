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

package net.algart.pyramid.http.proxy;

import net.algart.http.proxy.HttpProxy;
import net.algart.http.proxy.HttpServerAddress;
import net.algart.http.proxy.HttpServerFailureHandler;
import net.algart.pyramid.api.common.PyramidServicesConfiguration;
import net.algart.pyramid.api.http.*;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

public final class HttpPyramidProxyServer {
    static {
        if (!HttpPyramidProxyServer.class.getName().equals(
            HttpPyramidConstants.HTTP_PYRAMID_PROXY_SERVER_CLASS_NAME))
        {
            throw new AssertionError("Invalid constant HTTP_PYRAMID_PROXY_SERVER_CLASS_NAME");
        }
    }

    static final Logger LOG = Logger.getLogger(HttpPyramidProxyServer.class.getName());

    private final PyramidServicesConfiguration serviceConfiguration;
    private final HttpServerConfiguration serverConfiguration;
    private final HttpProxy proxy;

    public HttpPyramidProxyServer(
        PyramidServicesConfiguration servicesConfiguration,
        HttpServerConfiguration serverConfiguration)
    {
        Objects.requireNonNull(servicesConfiguration, "Null servicesConfiguration of pyramid services");
        Objects.requireNonNull(serverConfiguration, "Null serverConfiguration for specific server");
        if (!serverConfiguration.hasProxy()) {
            throw new IllegalArgumentException("Proxy is not used in this serverConfiguration");
        }
        this.serviceConfiguration = servicesConfiguration;
        this.serverConfiguration = serverConfiguration;
        final StandardPyramidServerResolver serverResolver = new StandardPyramidServerResolver(
            servicesConfiguration, serverConfiguration);
        serverResolver.addPyramidIdFinder(new HttpPyramidIdFinderBetweenSlashsAfterPrefix(
            HttpPyramidConstants.CommandPrefixes.TMS + "/"));
        serverResolver.addPyramidIdFinder(new HttpPyramidIdFinderBetweenSlashsAfterPrefix(
            HttpPyramidConstants.CommandPrefixes.ZOOMIFY + "/"));
        this.proxy = new HttpProxy(
            serverConfiguration.getProxySettings().getProxyPort(),
            serverResolver,
            new HttpServerFailureHandler() {
                @Override
                public void onConnectionFailed(HttpServerAddress address, Throwable throwable) {
                    //In future, it can restart the server, reserving 2nd instance to be on the safe side
                    LOG.warning("Cannot connect to " + address + ": maybe the service crashed");
                }

                @Override
                public void onServerTimeout(HttpServerAddress address, String requestURL) {
                    LOG.warning("Timeout while accessing " + address + ": maybe the service crashed");
                }
            });
        if (serverConfiguration.getProxySettings().isSsl()) {
            this.proxy.enableSsl(
                serverConfiguration.getSslSettings().keystoreFile(),
                serverConfiguration.getSslSettings().getKeystorePassword(),
                serverConfiguration.getSslSettings().getKeyPassword());
        }
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
                        Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY_AFTER_FINISH);
                        // - additional delay is to be on the safe side: allow all tasks to be correctly
                        // finished; do this BEFORE removing key file: in other case the client
                        // will "think" that the process is shut down, but the OS process will be still alive
                    } finally {
                        Files.deleteIfExists(finishKeyFile());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
        LOG.info("Finishing " + proxy);
    }

    private Path finishKeyFile() {
        return HttpPyramidApiTools.keyFile(
            HttpProxy.FINISH_COMMAND, proxy.getProxyPort(),
            HttpPyramidApiTools.systemCommandsFolder(serviceConfiguration.getProjectRoot())
        );
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
            HttpPyramidConstants.HTTP_PYRAMID_SERVICE_MODE_FLAG))
        {
            serviceMode = true;
            startArgIndex++;
        }
        if (args.length < startArgIndex + 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s projectRoot serverConfigurationFile%n",
                HttpPyramidProxyServer.class.getName());
            return;
        }
        final Path projectRoot = Paths.get(args[startArgIndex]);
        final Path serverConfigurationFile = Paths.get(args[startArgIndex + 1]);
        final HttpPyramidProxyServer server;
        try {
            final PyramidServicesConfiguration serviceConfiguration =
                PyramidServicesConfiguration.readFromRootFolder(projectRoot);
            final HttpServerConfiguration serverConfiguration =
                HttpServerConfiguration.readFromFile(serverConfigurationFile);
            server = new HttpPyramidProxyServer(serviceConfiguration, serverConfiguration);
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
        System.exit(0);
        // - force quick exiting the server after "finish" command
    }
}
