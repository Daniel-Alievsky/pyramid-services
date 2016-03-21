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
 *
 */

package net.algart.pyramid.http;

import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.http.handlers.InformationHttpPyramidCommand;
import net.algart.pyramid.http.handlers.ReadImageHttpPyramidCommand;

import java.io.IOException;

public class StandardHttpPyramidServiceLauncher {
    public static StandardHttpPyramidService newService(PlanePyramidFactory factory, int port) throws IOException {
        final StandardHttpPyramidService service = new StandardHttpPyramidService(factory, port);
        service.addHandler("/pp-information", new InformationHttpPyramidCommand(service));
        service.addHandler("/pp-read-image", new ReadImageHttpPyramidCommand(service));
        return service;
    }

    public static void main(String[] args)
        throws InterruptedException,
        ClassNotFoundException,
        IllegalAccessException,
        InstantiationException,
        IOException
    {
        final Class<?>[] planePyramidFactoryClasses =
            splitClassNames("net.algart.pyramid.http.planePyramidFactory");
        Class<?>[] planePyramidSubFactoryClasses =
            splitClassNamesOrReturnNulls("net.algart.pyramid.http.planePyramidSubFactory",
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
        StandardHttpPyramidService[] services = new StandardHttpPyramidService[planePyramidFactoryClasses.length];
        for (int k = 0; k < services.length; k++) {
            final PlanePyramidFactory factory = (PlanePyramidFactory)
                planePyramidFactoryClasses[k].newInstance();
            factory.initializeConfiguration(planePyramidSubFactoryClasses[k].newInstance());
            services[k] = newService(factory, ports[k]);
        }
        for (final StandardHttpPyramidService service : services) {
            new Thread() {
                @Override
                public void run() {
                    service.waitForFinish();
                }
            }.start();
        }
    }

    private static Class<?>[] splitClassNames(String propertyName) throws ClassNotFoundException {
        return splitClassNames(propertyName, false);
    }

    private static Class<?>[] splitClassNamesOrReturnNulls(String propertyName, int resultLengthIfAbsent)
        throws ClassNotFoundException
    {
        Class<?>[] result = splitClassNames(propertyName, true);
        return result == null ? new Class<?>[resultLengthIfAbsent] : result;
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

    private static Class<?>[] splitClassNames(String propertyName, boolean nullAllowed) throws ClassNotFoundException {
        final String classNameList = System.getProperty(propertyName);
        if (classNameList == null) {
            if (nullAllowed) {
                return null;
            } else {
                throw new IllegalArgumentException(propertyName + " property not set");
            }
        }
        final String[] classNames = classNameList.split("\\|");
        final Class<?>[] result = new Class<?>[classNames.length];
        for (int k = 0; k < result.length; k++) {
            final String name = classNames[k].trim();
            result[k] = nullAllowed && name.isEmpty() ? null : Class.forName(name);
        }
        return result;
    }
}
