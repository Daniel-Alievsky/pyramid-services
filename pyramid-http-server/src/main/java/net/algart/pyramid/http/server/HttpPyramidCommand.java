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

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.util.Objects;

public abstract class HttpPyramidCommand {
    protected final HttpPyramidService httpPyramidService;
    final String urlPrefix;

    protected HttpPyramidCommand(HttpPyramidService httpPyramidService, String urlPrefix) {
        this.httpPyramidService = Objects.requireNonNull(httpPyramidService, "Null httpPyramidService");
        this.urlPrefix = Objects.requireNonNull(urlPrefix, "Null urlPrefix");
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }

    protected abstract void service(Request request, Response response) throws Exception;

    protected boolean isSubFoldersAllowed() {
        return false;
    }

    public static String getParameter(Request request, String name) {
        return Objects.requireNonNull(request.getParameter(name), "Parameter " + name + " required");
    }

    public static double getDoubleParameter(Request request, String name) {
        final String result = getParameter(request, name);
        try {
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Parameter " + name + " is not a correct double: " + result);
        }
    }

    public static int getIntParameter(Request request, String name) {
        final String result = getParameter(request, name);
        try {
            return Integer.parseInt(result);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Parameter " + name + " is not a correct integer: " + result);
        }
    }

    public static long getLongParameter(Request request, String name) {
        final String result = getParameter(request, name);
        try {
            return Long.parseLong(result);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Parameter " + name + " is not a correct integer: " + result);
        }
    }

    public static boolean getBooleanParameter(Request request, String name) {
        final String result = getParameter(request, name);
        return "true".equalsIgnoreCase(result);
    }
}
