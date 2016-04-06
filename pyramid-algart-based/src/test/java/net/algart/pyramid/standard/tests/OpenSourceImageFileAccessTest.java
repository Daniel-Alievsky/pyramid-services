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

package net.algart.pyramid.standard.tests;

import net.algart.pyramid.http.HttpPyramidService;
import net.algart.pyramid.http.SimpleHttpPyramidServiceLauncher;
import net.algart.pyramid.http.handlers.InformationHttpPyramidCommand;
import net.algart.pyramid.http.handlers.ReadRectangleHttpPyramidCommand;
import net.algart.pyramid.http.handlers.TmsHttpPyramidCommand;
import net.algart.pyramid.standard.StandardPlanePyramidFactory;
import net.algart.simagis.pyramid.factories.ImageIOPlanePyramidSourceFactory;

import java.io.IOException;

public class OpenSourceImageFileAccessTest {
    public static void main(String[] args) throws Exception {
        boolean addGnu = args.length >= 1 && args[0].equals("-gnu");
        System.setProperty(
            "net.algart.pyramid.http.port", "9001" + (addGnu ? "|9100" : ""));
        System.setProperty(
            "net.algart.pyramid.http.planePyramidFactory",
            StandardPlanePyramidFactory.class.getName()
                + (addGnu ? "|" + StandardPlanePyramidFactory.class.getName() : ""));
        System.setProperty(
            "net.algart.pyramid.http.planePyramidSubFactory",
            ImageIOPlanePyramidSourceFactory.class.getName()
                + (addGnu ? "|com.simagis.pyramid.loci.server.LociPlanePyramidSourceFactory" : ""));
        new SimpleHttpPyramidServiceLauncher() {
            @Override
            protected void addHandlers(HttpPyramidService service) {
                super.addHandlers(service);
                service.addHandler("/unsafe-information", new InformationHttpPyramidCommand(service) {
                    @Override
                    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
                        return pyramidId;
                    }
                });
                service.addHandler("/unsafe-read-rectangle", new ReadRectangleHttpPyramidCommand(service) {
                    @Override
                    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
                        return pyramidId;
                    }
                });
                service.addHandler("/unsafe-tms", new TmsHttpPyramidCommand(service) {
                    @Override
                    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
                        return pyramidId
                            .replace("~~2F", "/")
                            .replace("~~3A", ":")
                            .replace("~~7B", "{")
                            .replace("~~7D", "}")
                            .replace("~~22", "\"");
                    }
                });
            }

        }.doMain(args);
    }
}