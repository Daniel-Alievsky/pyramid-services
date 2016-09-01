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

package net.algart.http.proxy;

import org.glassfish.grizzly.http.util.Parameters;

import java.io.IOException;
import java.util.*;

public interface HttpServerDetector {
    /**
     * Finds and returns the server, to which the proxy should address the request.
     *
     * @param requestURI      path in the site, for example /folder/test.html for request
     *                        http://localhost:8080/folder/test.html?arg1=value1&arg2=value2 (8080 is the proxy port)
     * @param queryParameters parsed GET/POST parameters in the query string (arg1=value1 and arg2=value2)
     * @return host and port of the server, which should really process this request, for example somesite.com:9000
     */
    HttpServerAddress getServer(String requestURI, Parameters queryParameters) throws IOException;

    abstract class BasedOnMap implements HttpServerDetector {
        @Override
        public HttpServerAddress getServer(String requestURI, Parameters queryParameters) throws IOException {
            return getServer(requestURI, toMap(queryParameters));
        }

        public abstract HttpServerAddress getServer(String requestURI, Map<String, String> queryParameters)
            throws IOException;

        public static Map<String, String> toMap(Parameters queryParameters) {
            Objects.requireNonNull(queryParameters);
            final Map<String, String> result = new LinkedHashMap<>();
            for (final String name : queryParameters.getParameterNames()) {
                final String[] values = queryParameters.getParameterValues(name);
                if (values != null) {
                    for (String value : values) {
                        result.put(name, value);
                        // - using the first from several values
                        break;
                    }
                }
            }
            return result;
        }
    }
}
