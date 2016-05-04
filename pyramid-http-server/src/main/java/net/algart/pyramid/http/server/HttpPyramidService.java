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

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.http.api.HttpPyramidConstants;
import net.algart.pyramid.http.server.handlers.*;
import net.algart.pyramid.requests.PlanePyramidRequest;
import net.algart.pyramid.PlanePyramidPool;
import org.glassfish.grizzly.http.server.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidService {
    static final long IMAGE_CACHING_MEMORY = Math.max(16, Long.getLong(
        "net.algart.pyramid.http.imageCachingMemory", 256L * 1024L * 1024L));
    static final Logger LOG = Logger.getLogger(HttpPyramidService.class.getName());

    private static final String CONFIG_ROOT_DIR = System.getProperty(
        "net.algart.pyramid.http.configRoot", "/pp-links");
    private static final String CONFIG_FILE_NAME = System.getProperty(
        "net.algart.pyramid.http.configFile", "config.json");
    private static final int MAX_NUMBER_OF_PYRAMIDS_IN_POOL = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.imageCachingMemory", 256));

    private final HttpServer server;
    private final int port;
    private final ReadImageThreadPool threadPool;
    private final ServerConfiguration serverConfiguration;
    private final PlanePyramidPool pyramidPool;
    private volatile boolean shutdown = false;

    public HttpPyramidService(PlanePyramidFactory factory, int port) {
        this.pyramidPool = new PlanePyramidPool(factory, MAX_NUMBER_OF_PYRAMIDS_IN_POOL);
        this.threadPool = new ReadImageThreadPool(Runtime.getRuntime().availableProcessors());
        this.server = new HttpServer();
        this.port = port;
        server.addListener(new NetworkListener(HttpPyramidService.class.getName(), "localhost", port));
        this.serverConfiguration = server.getServerConfiguration();
        addBuiltInHandlers();
    }

    public final void addHandler(String urlPrefix, HttpPyramidCommand command) {
        Objects.requireNonNull(urlPrefix, "Null URL prefix");
        Objects.requireNonNull(command, "Null HTTP-pyramid command");
        serverConfiguration.addHttpHandler(new HttpPyramidHandler(urlPrefix, command), urlPrefix);
        LOG.info("Adding HTTP handler " + urlPrefix);
    }

    public final void addStandardHandlers() {
        addHandler(HttpPyramidConstants.INFORMATION_COMMAND_PREFIX, new InformationHttpPyramidCommand(this));
        addHandler(HttpPyramidConstants.READ_RECTANGLE_COMMAND_PREFIX, new ReadRectangleHttpPyramidCommand(this));
        addHandler(HttpPyramidConstants.TMS_COMMAND_PREFIX, new TmsHttpPyramidCommand(this));
        addHandler(HttpPyramidConstants.ZOOMIFY_COMMAND_PREFIX, new ZoomifyHttpPyramidCommand(this));
        addHandler(HttpPyramidConstants.READ_SPECIAL_IMAGE_COMMAND_PREFIX, new ReadSpecialImagePyramidCommand(this));
    }

    public final void start() throws IOException {
        LOG.info("Starting " + this);
        server.start();
    }

    public final void waitForFinish() {
        try {
            while (!shutdown) {
                Thread.sleep(500);
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Unexpected interrupted exception", e);
        }
        // - to be on the safe side: allow all tasks to be correctly finished
        LOG.info("Finishing " + this);
    }

    public void finish() {
        LOG.log(Level.INFO, "Shutting down pyramid service...");
        server.shutdown();
        threadPool.shutdown();
        shutdown = true;
    }

    public final PlanePyramidPool getPyramidPool() {
        return pyramidPool;
    }

    public final boolean createReadImageTask(
        Request request,
        Response response,
        PlanePyramid pyramid,
        PlanePyramidRequest pyramidRequest)
    {
        return threadPool.createReadImageTask(request, response, pyramid, pyramidRequest);
    }

    public String pyramidIdToConfiguration(String pyramidId) throws IOException {
        final Path path = Paths.get(CONFIG_ROOT_DIR, pyramidId, CONFIG_FILE_NAME);
        if (!Files.isRegularFile(path)) {
            throw new FileNotFoundException("File " + path.toAbsolutePath() + " does not exists");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return getClass().getName() + " on port " + port + " with factory " + pyramidPool.getFactory();
    }

    private void addBuiltInHandlers() {
        addHandler(HttpPyramidConstants.ALIVE_STATUS_COMMAND_PREFIX, new AliveStatusCommand(this));
        addHandler(HttpPyramidConstants.FINISH_COMMAND_PREFIX, new FinishCommand(this));
    }

    private class FinishCommand extends HttpPyramidCommand {
        public FinishCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService);
        }

        @Override
        public void service(
            Request request,
            Response response)
            throws Exception
        {
            finish();
            response.setContentType("text/plain");
            response.setStatus(200, "OK");
            response.getWriter().write("Finishing service");
            response.finish();
        }
    }

    private class AliveStatusCommand extends HttpPyramidCommand {
        public AliveStatusCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService);
        }

        @Override
        public void service(
            Request request,
            Response response)
            throws Exception
        {
            response.setContentType("text/plain");
            response.setStatus(200, "OK");
            response.getWriter().write(HttpPyramidConstants.ALIVE_RESPONSE);
            response.finish();
        }
    }

    private class HttpPyramidHandler extends HttpHandler {
        final String prefix;
        final HttpPyramidCommand command;

        HttpPyramidHandler(
            String prefix,
            HttpPyramidCommand command)
        {
            this.prefix = Objects.requireNonNull(prefix);
            this.command = Objects.requireNonNull(command);
        }

        @Override
        public void service(Request request, Response response) throws IOException {
//            System.out.println(request.getRequestURI());
//            System.out.println("path info: " + request.getPathInfo());
//            System.out.println("context: " + request.getContextPath());
//            System.out.println("query:" + request.getQueryString());
//            for (String name : request.getParameterNames()) {
//                System.out.printf("%s: %s%n", name, request.getParameter(name));
//            }
            final String path = request.getRequestURI();
            if (!command.isSubFoldersAllowed() && !prefix.equals(path) && !(prefix + "/").equals(path)) {
                response.setStatus(404, "Invalid request path");
                response.setContentType("text/plain");
                response.getWriter().write(String.format("Invalid path %s%n", path));
                LOG.log(Level.SEVERE, String.format("Subfolders are not supported: %s", path));
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
