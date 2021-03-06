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

package net.algart.pyramid.standard.tests;

import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.api.common.PyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.server.HttpPyramidService;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Paths;

class SimpleHttpPyramidServer {
    public final void doMain(String[] args) throws Exception {
        final Class<?>[] planePyramidFactoryClasses =
            splitClassNames("net.algart.pyramid.http.planePyramidFactory");
        String[] planePyramidSubFactoryClasses =
            splitStringsOrReturnNulls("net.algart.pyramid.http.planePyramidSubFactory",
                planePyramidFactoryClasses.length);
        if (planePyramidSubFactoryClasses.length != planePyramidFactoryClasses.length) {
            throw new IllegalArgumentException("Different number of sub-factories ("
                + planePyramidSubFactoryClasses.length
                + ") and factories (" + planePyramidFactoryClasses.length + ")");
        }
        final int[] ports = splitIntegers("net.algart.pyramid.http.port");
        if (ports.length != planePyramidFactoryClasses.length) {
            throw new IllegalArgumentException("Different number of ports (" + ports.length
                + ") and factories (" + planePyramidFactoryClasses.length + ")");
        }
        HttpPyramidService[] services = new HttpPyramidService[planePyramidFactoryClasses.length];
        try {
            for (int k = 0; k < services.length; k++) {
                final PlanePyramidFactory factory = (PlanePyramidFactory)
                    planePyramidFactoryClasses[k].newInstance();
                if (planePyramidSubFactoryClasses[k] != null) {
                    final JsonObjectBuilder builder = Json.createObjectBuilder();
                    builder.add(PyramidConstants.PLANE_PYRAMID_SUB_FACTORY_IN_PYRAMID_FACTORY_CONFIGURATION_JSON,
                        planePyramidSubFactoryClasses[k]);
                    builder.add(PyramidConstants.FORMAT_NAME_IN_PYRAMID_FACTORY_CONFIGURATION_JSON, "unknown");
                    factory.initializeConfiguration(builder.build());
                }
                services[k] = newService(factory, ports[k]);
                addHandlers(services[k]);
                services[k].start();
            }
        } catch (Exception | Error e) {
            for (final HttpPyramidService service : services) {
                if (service != null) {
                    service.finish();
                }
            }
            throw e;
        }
        for (final HttpPyramidService service : services) {
            new Thread() {
                @Override
                public void run() {
                    service.waitForFinishAndProcessSystemCommands();
                }
            }.start();
        }
    }

    protected HttpPyramidService newService(PlanePyramidFactory factory, int port) throws IOException {
        return new HttpPyramidService(factory, port,
            Paths.get(HttpPyramidConstants.SYSTEM_COMMANDS_FOLDER));
    }

    protected void addHandlers(HttpPyramidService service) {
        service.addStandardHandlers();
    }

    private static int[] splitIntegers(String propertyName) {
        final String intList = System.getProperty(propertyName);
        if (intList == null) {
            throw new IllegalArgumentException(propertyName + " property not set");
        }
        final String[] ints = intList.split("\\|");
        final int[] result = new int[ints.length];
        for (int k = 0; k < result.length; k++) {
            result[k] = Integer.parseInt(ints[k]);
        }
        return result;
    }


    private static String[] splitStringsOrReturnNulls(String propertyName, int resultLengthIfAbsent) {
        final String stringList = System.getProperty(propertyName);
        if (stringList == null) {
            return new String[resultLengthIfAbsent];
        }
        return stringList.split("\\|");
    }

    private static Class<?>[] splitClassNames(String propertyName) throws ClassNotFoundException {
        final String classNameList = System.getProperty(propertyName);
        if (classNameList == null) {
            throw new IllegalArgumentException(propertyName + " property not set");
        }
        final String[] classNames = classNameList.split("\\|");
        final Class<?>[] result = new Class<?>[classNames.length];
        for (int k = 0; k < result.length; k++) {
            final String name = classNames[k].trim();
            result[k] = Class.forName(name);
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        new SimpleHttpPyramidServer().doMain(args);
    }
}
