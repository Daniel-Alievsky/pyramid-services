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

package net.algart.pyramid.http.control;

import net.algart.pyramid.api.http.HttpPyramidServicesConfiguration;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidServersManager {
    private static final boolean AUTO_REVIVE = getBooleanProperty(
        "net.algart.pyramid.http.control.autoRevive", true);
    // - can be disabled for debugging needs

    private static final Logger LOG = Logger.getLogger(HttpPyramidServersManager.class.getName());
    private static final int REVIVING_DELAY_IN_MILLISECONDS = 3000;

    private final HttpPyramidServersLauncher launcher;

    private volatile boolean shutdown = false;
    private volatile boolean revivingActive = false;
    private final Object lock = new Object();

    private HttpPyramidServersManager(
        HttpPyramidServicesConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        this.launcher = new HttpPyramidServersLauncher(configuration, specificServerConfiguration);
    }

    public static HttpPyramidServersManager newInstance(
        HttpPyramidServicesConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        return new HttpPyramidServersManager(configuration, specificServerConfiguration);
    }

    public static HttpPyramidServersManager newInstance(
        Path projectRoot,
        Path specificServerConfigurationFile) throws IOException
    {
        return new HttpPyramidServersManager(
            HttpPyramidServicesConfiguration.readFromRootFolder(projectRoot),
            HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile));
    }

    public HttpPyramidServersLauncher getLauncher() {
        return launcher;
    }

    public HttpPyramidServicesConfiguration getConfiguration() {
        return launcher.getConfiguration();
    }

    public HttpPyramidSpecificServerConfiguration getSpecificServerConfiguration() {
        return launcher.getSpecificServerConfiguration();
    }

    public void startAll() throws IOException {
        if (shutdown) {
            throw new IllegalStateException("Server manager was shut down and cannot be used");
        }
        launcher.startAll(false);
        revivingActive = true;
        new RevivingThread().start();
    }

    public void stopAll() {
        synchronized (lock) {
            shutdown = true;
            lock.notifyAll();
            while (revivingActive) {
                // we must stop reviving thread before an attempt to stop servers
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    LOG.log(Level.SEVERE, "Unexpected interrupted exception", e);
                }
            }
        }
        // Actually the processes will be stopped at the end of RevivingThread.run()
    }

    private void reviveAll() throws InvalidFileConfigurationException {
        final List<AsyncPyramidCommand> allSubCommands = new ArrayList<>();
        final List<AsyncPyramidCommand> serviceCommands = new ArrayList<>();
        final List<String> allGroupId = new ArrayList<>(launcher.getConfiguration().allGroupId());
        for (String groupId : allGroupId) {
            final AsyncPyramidCommand command = launcher.restartPyramidServicesGroupRequest(groupId, true);
            if (!(command instanceof ImmediatePyramidCommand)) {
                serviceCommands.add(command);
            }
        }
        allSubCommands.addAll(serviceCommands);
        final AsyncPyramidCommand proxyCommand;
        final boolean hasProxy = launcher.getSpecificServerConfiguration().hasProxy();
        final boolean revivingByManager = hasProxy
            && launcher.getSpecificServerConfiguration().getProxySettings().isAutoRevivingByManager();
        if (revivingByManager) {
            proxyCommand = launcher.restartPyramidProxyRequest(true);
            if (!(proxyCommand instanceof ImmediatePyramidCommand)) {
                allSubCommands.add(proxyCommand);
            }
        } else {
            proxyCommand = null;
        }
        if (!allSubCommands.isEmpty()) {
            new MultipleAsyncPyramidCommand(allSubCommands).setFinishHandler(() -> {
                int serviceCount = 0, processCount = 0;
                for (int i = 0; i < serviceCommands.size(); i++) {
                    if (serviceCommands.get(i).isAccepted()) {
                        processCount++;
                        serviceCount += launcher.getConfiguration().numberOfProcessServices(allGroupId.get(i));
                    }
                }
                LOG.info(String.format("%n%d services in %d processes successfully revived, %s",
                    serviceCount, processCount, proxyCommand != null && proxyCommand.isAccepted() ?
                        "1 proxy successfully revived" :
                        revivingByManager ? "0 proxy successfully revived" :
                            !hasProxy ? "proxy absent" : "proxy not checked"));
            }).waitFor();
        }
    }

    private static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        if (defaultValue) {
            return !"false".equalsIgnoreCase(System.getProperty(propertyName));
        } else {
            return Boolean.getBoolean(propertyName);
        }
    }

    private class RevivingThread extends Thread {
        @Override
        public void run() {
            synchronized (lock) {
                try {
                    while (!shutdown) {
                        lock.wait(REVIVING_DELAY_IN_MILLISECONDS);
                        if (AUTO_REVIVE) {
                            reviveAll();
                        }
                    }
                } catch (InterruptedException e) {
                    LOG.log(Level.SEVERE, "Unexpected interrupted exception", e);
                } catch (InvalidFileConfigurationException e) {
                    LOG.log(Level.SEVERE, "Unexpected corruption of configuration file structure while"
                        + " reviving pyramid services", e.getCause());
                } catch (Throwable e) {
                    LOG.log(Level.SEVERE, "Unexpected runtime exception", e);
                } finally {
                    LOG.info("Finishing reviving thread");
                    revivingActive = false;
                    lock.notifyAll();
                }
            }
            try {
                launcher.stopAllRequest(false).waitFor();
            } catch (Throwable e) {
                LOG.log(Level.SEVERE, "CANNOT STOP PYRAMID SERVICES! Probably the file structure is corrupted! "
                    + "Please reboot the server and check all confiration files and folders", e);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.printf("Usage:%n");
            System.out.printf("    %s projectRoot specificServerConfigurationFile%n",
                HttpPyramidServersManager.class.getName());
            return;
        }
        final Path projectRoot = Paths.get(args[0]);
        final Path specificServerConfigurationFile = Paths.get(args[1]);
        final HttpPyramidServersManager manager = newInstance(projectRoot, specificServerConfigurationFile);
        try {
            manager.startAll();
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.printf("%n%nStarting failed; attempt to stop all started processes...%n%n");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }
            manager.launcher.stopAllRequest(false).waitFor();
            System.exit(1);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        System.out.printf("%nPress \"ENTER\" to stop all started servers...%n%n");
        try {
            System.in.read();
        } catch (IOException ignored) {
        }
        manager.stopAll();
    }
}

