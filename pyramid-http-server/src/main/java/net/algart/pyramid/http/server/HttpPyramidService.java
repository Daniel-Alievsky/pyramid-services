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
import net.algart.pyramid.PlanePyramidPool;
import net.algart.pyramid.http.api.HttpPyramidApiTools;
import net.algart.pyramid.http.api.HttpPyramidConstants;
import net.algart.pyramid.http.server.handlers.*;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.http.server.*;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpPyramidService {
    private static final Logger LOG = Logger.getLogger(HttpPyramidService.class.getName());

    private final HttpServer server;
    private final int port;
    private final int controlPort;
    private final ReadThreadPool threadPool;
    private final ServerConfiguration serverConfiguration;
    private final PlanePyramidPool pyramidPool;
    private volatile boolean shutdown = false;

    public HttpPyramidService(PlanePyramidFactory factory, int port) {
        Objects.requireNonNull(factory, "Null plane pyramid factory");
        if (port <= 0 || port > HttpPyramidConstants.MAX_ALLOWED_PORT) {
            throw new IllegalArgumentException("Invalid port " + port
                + ": must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT);
        }
        this.pyramidPool = new PlanePyramidPool(factory, HttpPyramidConstants.MAX_NUMBER_OF_PYRAMIDS_IN_POOL);
        this.threadPool = new ReadThreadPool(Runtime.getRuntime().availableProcessors());
        this.server = new HttpServer();
        this.port = port;
        this.controlPort = port + HttpPyramidConstants.PORT_INCREMENT_FOR_CONTROL_COMMANDS;
        server.addListener(new NetworkListener(HttpPyramidService.class.getName(),
            "localhost", port));
        server.addListener(new NetworkListener(HttpPyramidService.class.getName() + "-control",
            "localhost", controlPort));
        this.serverConfiguration = server.getServerConfiguration();
//        try {Thread.sleep(5000);} catch (InterruptedException e) {}
        addHandler(HttpPyramidConstants.ALIVE_STATUS_COMMAND_PREFIX, new AliveStatusCommand(this));
        addHandler(HttpPyramidConstants.FINISH_CONTROL_COMMAND_PREFIX, new FinishCommand(this));
        addHandler(HttpPyramidConstants.GC_CONTROL_COMMAND_PREFIX, new GcCommand(this));
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

    public int getPort() {
        return port;
    }

    public int getControlPort() {
        return controlPort;
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
        return HttpPyramidApiTools.pyramidIdToConfiguration(pyramidId);
    }

    @Override
    public String toString() {
        return getClass().getName() + " on port " + port + " with factory " + pyramidPool.getFactory();
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

        @Override
        boolean isControlCommand() {
            return true;
        }
    }

    private class GcCommand extends HttpPyramidCommand {
        public GcCommand(HttpPyramidService httpPyramidService) {
            super(httpPyramidService);
        }

        @Override
        public void service(
            Request request,
            Response response)
            throws Exception
        {
            final Runtime rt = Runtime.getRuntime();
            LOG.info(String.format(Locale.US, "GC report before: used memory %.5f MB / %.5f MB",
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.maxMemory() / 1048576.0));
            System.runFinalization();
            System.gc();
            LOG.info(String.format(Locale.US, "GC report after: used memory %.5f MB / %.5f MB",
                (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.maxMemory() / 1048576.0));
            response.setContentType("text/plain");
            response.setStatus(200, "OK");
            response.getWriter().write("Ok");
            response.finish();
        }

        @Override
        boolean isControlCommand() {
            return true;
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
            final int requestPort = request.getServerPort();
            final String path = request.getRequestURI();
            if (requestPort != (command.isControlCommand() ? controlPort : port)) {
                response.setStatus(404, "Invalid request path");
                response.setContentType("text/plain");
                response.getWriter().write(String.format("Invalid path %s%n", path));
                LOG.log(Level.SEVERE, String.format("Invalid port: %d", requestPort));
                return;
            }
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
