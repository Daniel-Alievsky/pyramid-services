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
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidServersManager {
    private static final Logger LOG = Logger.getLogger(HttpPyramidServersManager.class.getName());

    private final HttpPyramidServersLauncher launcher;

    private volatile boolean shutdown = false;
    private volatile boolean revivingActive = false;
    private final Object lock = new Object();

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
        if (shutdown) {
            throw new IllegalStateException("Server manager was shut down and cannot be used");
        }
        launcher.startAll(false);
        revivingActive = true;
        new RevivingThread().start();
    }

    public void stopAll() throws IOException {
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
        launcher.stopAll(false).waitFor();
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
        System.out.printf("Press \"ENTER\" to stop all started servers...%n%n");
        try {
            System.in.read();
        } catch (IOException ignored) {
        }
        manager.stopAll();
    }

    private class RevivingThread extends Thread {

        @Override
        public void run() {
            synchronized (lock) {
                try {
                    while (!shutdown) {
                        lock.wait(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY);
                        //TODO!! check behaviour with very long delay
                        reviveAll();
                    }
                } catch (InterruptedException e) {
                    LOG.log(Level.SEVERE, "Unexpected interrupted exception", e);
                }
                LOG.info("Finishing reviving thread");
                revivingActive = false;
                lock.notifyAll();
            }
        }

        private void reviveAll() {
            //TODO!!
        }
    }
}
