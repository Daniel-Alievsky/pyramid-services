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

package net.algart.pyramid.http.proxy;

import net.algart.http.proxy.HttpServerAddress;
import net.algart.http.proxy.HttpServerDetector;
import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidPool;
import net.algart.pyramid.http.api.HttpPyramidConfiguration;
import org.glassfish.grizzly.http.util.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

class ServerDetector implements HttpServerDetector {

    private static final int POOL_SIZE = 500000;
    // - several megabytes as a maximum
    private static final Logger LOG = Logger.getLogger(ServerDetector.class.getName());

    private final Map<String, HttpServerAddress> pool = new ServerAddressHashMap();
    private final HttpPyramidConfiguration configuration;

    ServerDetector(HttpPyramidConfiguration configuration) {
        assert configuration != null;
        this.configuration = configuration;
    }

    @Override
    public HttpServerAddress getServer(String requestURI, Parameters queryParameters) {
        throw new UnsupportedOperationException();
    }


    private static final HttpServerAddress pyramidIdToServerAddress(String pyramidId) {
        throw new UnsupportedOperationException();
        //TODO!! see StandardPlanePyramidFactory
    }

    private static class ServerAddressHashMap extends LinkedHashMap<String, HttpServerAddress> {
        public ServerAddressHashMap() {
            super(16, 0.75f, true);
            // necessary to set access order to true
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, HttpServerAddress> eldest) {
            if (size() > POOL_SIZE) {
                LOG.info("Proxy server detector pool overflow; freeing and removing pyramid id " + eldest.getKey());
                return true;
            } else {
                return false;
            }
        }
    }
}
