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
import net.algart.pyramid.PlanePyramidPool;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.common.PyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;
import net.algart.pyramid.http.server.handlers.*;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.http.server.*;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidService {
    static {
        System.setProperty(
            org.glassfish.grizzly.http.util.Constants.class.getName() + ".default-character-encoding", "UTF-8");
        // - Necessary to provide correct parsing GET and POST parameters, when encoding is not specified
        // (typical situation for POST, always for GET).
    }
    private static final Logger LOG = Logger.getLogger(HttpPyramidService.class.getName());

    private final HttpServer server;
    private final int port;
    private final Path systemCommandsFolder;
    private String configRootDir = PyramidConstants.DEFAULT_CONFIG_ROOT_DIR;
    private String configFileName = PyramidConstants.DEFAULT_CONFIG_FILE_NAME;

    private final List<SystemCommand> systemHandlers = new ArrayList<>();
    private final ReadThreadPool threadPool;
    private final ServerConfiguration serverConfiguration;
    private final PlanePyramidPool pyramidPool;
    private volatile boolean shutdown = false;

    public HttpPyramidService(
        PlanePyramidFactory factory,
        int port,
        Path systemCommandsFolder)
        throws IOException
    {
        Objects.requireNonNull(factory, "Null plane pyramid factory");
        Objects.requireNonNull(systemCommandsFolder, "Null folder for managing service by key files");
        if (port <= 0 || port > HttpPyramidConstants.MAX_ALLOWED_PORT) {
            throw new IllegalArgumentException("Invalid port " + port
                + ": must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT);
        }
        if (!Files.isDirectory(systemCommandsFolder)) {
            throw new IOException("Invalid folder for managing service by key files \""
                + systemCommandsFolder.toAbsolutePath()
                + "\": it must be an existing folder with permitted read/write operations");
        }
        this.pyramidPool = new PlanePyramidPool(factory, HttpPyramidConstants.MAX_NUMBER_OF_PYRAMIDS_IN_POOL);
        this.threadPool = new ReadThreadPool(Runtime.getRuntime().availableProcessors());
        this.server = new HttpServer();
        this.port = port;
        this.systemCommandsFolder = systemCommandsFolder;
        this.server.addListener(new NetworkListener(HttpPyramidService.class.getName(),
            HttpPyramidConstants.LOCAL_HOST, port));
        this.serverConfiguration = server.getServerConfiguration();
//        try {Thread.sleep(5000);} catch (InterruptedException e) {}
        addSystemHandler(new FinishCommand(this));
        addSystemHandler(new GcCommand(this));
        addHandler(new AliveStatusCommand(this));
    }

    public HttpPyramidService setSpecificConfiguration(
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        Objects.requireNonNull(specificServerConfiguration, "Null configuration for specific server");
        this.configRootDir = specificServerConfiguration.getConfigRootDir();
        this.configFileName = specificServerConfiguration.getConfigFileName();
        assert configRootDir != null;
        assert configFileName != null;
        return this;
    }

    public final void addHandler(HttpPyramidCommand command) {
        Objects.requireNonNull(command, "Null HTTP-pyramid command");
        serverConfiguration.addHttpHandler(new HttpPyramidHandler(command.urlPrefix, command), command.urlPrefix);
        LOG.config("Adding HTTP handler for " + command.urlPrefix);
    }

    public final void addStandardHandlers() {
        addHandler(new InformationHttpPyramidCommand(this));
        addHandler(new ReadRectangleHttpPyramidCommand(this));
        addHandler(new TmsHttpPyramidCommand(this));
        addHandler(new ZoomifyHttpPyramidCommand(this));
        addHandler(new ReadSpecialImagePyramidCommand(this));
    }

    public final void start() throws IOException {
        if (shutdown) {
            throw new IllegalStateException("Service " + this + " was shut down and cannot be used");
        }
        LOG.info("Starting " + this);
        server.start();
    }

    public final void waitForFinishAndProcessSystemCommands() {
        try {
            while (!shutdown) {
                Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY);
                for (SystemCommand systemCommand : systemHandlers) {
                    if (Files.exists(systemCommand.keyFile())) {
                        try {
                            systemCommand.service();
                            if (shutdown) {
                                Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY_AFTER_FINISH);
                                // - additional delay is to be on the safe side: allow all tasks to be correctly
                                // finished; do this BEFORE removing key file: in other case the client
                                // will "think" that the process is shut down, but the OS process will be still alive
                            }
                        } finally {
                            Files.deleteIfExists(systemCommand.keyFile());
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Unexpected interrupted exception", e);
        } catch (IOException e) {
            throw new IOError(e);
        }
        LOG.info("Finishing " + this);
    }

    public final void finish() {
        LOG.log(Level.INFO, "Shutting down pyramid service " + this + "...");
        server.shutdown();
        threadPool.shutdown();
        shutdown = true;
    }

    public final int getPort() {
        return port;
    }

    public final Path getSystemCommandsFolder() {
        return systemCommandsFolder;
    }

    public final PlanePyramidPool getPyramidPool() {
        return pyramidPool;
    }

    public final boolean createReadTask(
        Request request,
        Response response,
        PlanePyramidRequest pyramidRequest)
    {
        return threadPool.createReadTask(request, response, pyramidRequest, pyramidPool);
    }

    public String pyramidIdToConfiguration(String pyramidId) throws IOException {
        return PyramidApiTools.pyramidIdToConfiguration(pyramidId, configRootDir, configFileName);
    }

    @Override
    public String toString() {
        return getClass().getName() + " on port " + port + " with factory " + pyramidPool.getFactory();
    }

    private void addSystemHandler(SystemCommand command) {
        Objects.requireNonNull(command, "Null HTTP-pyramid command");
        systemHandlers.add(command);
        LOG.config("Adding file-managed handler for " + command.urlPrefix);
    }

    private class FinishCommand extends SystemCommand {

        FinishCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.FINISH);
        }
        @Override
        void service() throws IOException {
            LOG.info("Finish system command received for " + HttpPyramidService.this);
            finish();
        }
    }

    private class GcCommand extends SystemCommand {
        GcCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.GC);
        }

        @Override
        void service() throws IOException {
            final Runtime rt = Runtime.getRuntime();
            LOG.info(String.format(Locale.US, "GC report before: used memory %.5f MB / %.5f MB",
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.maxMemory() / 1048576.0));
            for (int k = 0; k < 3; k++) {
                // 3 attempts for the best results
                System.runFinalization();
                System.gc();
            }
            LOG.info(String.format(Locale.US, "GC report after: used memory %.5f MB / %.5f MB",
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.maxMemory() / 1048576.0));
        }
    }

    private class AliveStatusCommand extends HttpPyramidCommand {
        public AliveStatusCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.ALIVE_STATUS);
        }

        @Override
        protected void service(Request request, Response response) throws Exception {
            response.setContentType("text/plain; charset=utf-8");
            response.setStatus(200, "OK");
            response.getWriter().write(HttpPyramidConstants.ALIVE_RESPONSE);
            response.finish();
        }
    }

    void tryToStop() throws IOException {
        final Path keyFile = HttpPyramidApiTools.keyFile(
            HttpPyramidConstants.CommandPrefixes.FINISH, port, systemCommandsFolder);
        try {
            Files.createFile(keyFile);
        } catch (FileAlreadyExistsException e) {
            // it is not a problem if this file already exists
        }
        try {
            try {
                Thread.sleep(HttpPyramidConstants.SYSTEM_COMMNAD_STOP_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final boolean accepted = !Files.exists(keyFile);
            if (accepted) {
                try {
                    Thread.sleep(HttpPyramidConstants.SYSTEM_COMMANDS_DELAY_AFTER_FINISH);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            try {
                Files.deleteIfExists(keyFile);
                // - necessary to remove file even if the process did not react to it:
                // in other case, this file will lead to problems while new starting the process
            } catch (IOException ignored) {
            }
        }
    }

    private class HttpPyramidHandler extends HttpHandler {
        final String urlPrefix;
        final HttpPyramidCommand command;

        HttpPyramidHandler(
            String urlPrefix,
            HttpPyramidCommand command)
        {
            this.urlPrefix = Objects.requireNonNull(urlPrefix);
            this.command = Objects.requireNonNull(command);
        }

        @Override
        public void service(Request request, Response response) throws IOException {
//            System.out.println(request.getRequestURI());
//            System.out.println("path info: " + request.getPathInfo());
//            System.out.println("context: " + request.getContextPath());
//            System.out.println("query:" + request.getQueryString());
//            System.out.println("  headers:");
//            for (String headerName : request.getHeaderNames()) {
//                for (String headerValue : request.getHeaders(headerName)) {
//                    System.out.printf("    %s=%s%n", headerName, headerValue);
//                }
//            }
//            for (String name : request.getParameterNames()) {
//                System.out.printf("%s: %s%n", name, request.getParameter(name));
//            }
            final int requestPort = request.getServerPort();
            final String path = request.getRequestURI();
            if (requestPort != port) {
                response.setStatus(500, "Invalid request port");
                response.setContentType("text/plain; charset=utf-8");
                response.getWriter().write(String.format("Invalid port %n%n", port));
                LOG.log(Level.SEVERE, String.format("MUST NOT OCCUR! Invalid port: %d", requestPort));
                return;
            }
            if (!command.isSubFoldersAllowed() && !urlPrefix.equals(path) && !(urlPrefix + "/").equals(path)) {
                response.setStatus(404, "Invalid request path");
                response.setContentType("text/plain; charset=utf-8");
                response.getWriter().write(String.format("Invalid path %s%n", path));
                LOG.log(Level.WARNING, String.format("Subfolders are not supported: %s", path));
                return;
            }
            try {
                command.service(request, response);
            } catch (Throwable t) {
                response.setStatus(500, "Request error");
                response.setContentType("text/plain");
                response.getWriter().write(String.format("Request error%n"));
                // Minimizing information about an exception
                LOG.log(Level.SEVERE, "Problem in a pyramid created by " + pyramidPool.getFactory(), t);
            }
        }
    }

}
