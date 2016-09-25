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
import net.algart.http.proxy.HttpServerResolver;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;
import org.glassfish.grizzly.http.util.Parameters;

import javax.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

class StandardPyramidServerResolver implements HttpServerResolver {

    private static final int POOL_SIZE = 500000;
    // - several megabytes as a maximum
    private static final Logger LOG = Logger.getLogger(StandardPyramidServerResolver.class.getName());

    private final Map<String, HttpServerAddress> pool = new ServerAddressHashMap();
    private final HttpPyramidConfiguration configuration;
    private final HttpPyramidSpecificServerConfiguration.Proxy proxyConfiguration;
    private final Object lock = new Object();

    StandardPyramidServerResolver(
        HttpPyramidConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        assert configuration != null && specificServerConfiguration != null;
        assert specificServerConfiguration.getProxy() != null;
        this.configuration = configuration;
        this.proxyConfiguration = specificServerConfiguration.getProxy();
    }

    @Override
    public HttpServerAddress findServer(String requestURI, Parameters queryParameters) throws IOException {
        if (HttpPyramidApiTools.isUriPyramidCommand(requestURI)) {
            final String pyramidId = findPyramidId(requestURI, queryParameters);
            if (pyramidId != null) {
                synchronized (lock) {
                    HttpServerAddress result = pool.get(pyramidId);
                    if (result == null) {
                        result = pyramidIdToServerAddress(pyramidId);
                        pool.put(pyramidId, result);
                    }
                    LOG.config("Proxying " + requestURI + " to " + result + " by pyramidId=" + pyramidId);
                    return result;
                }
            }
            final Integer serverPort = findServerPort(queryParameters);
            if (serverPort != null) {
                HttpServerAddress result = new HttpServerAddress(proxyConfiguration.getPyramidHost(), serverPort);
                LOG.config("Proxying " + requestURI + " to " + result + " by direct port parameter " + serverPort);
                return result;
            }
        }
        HttpServerAddress result = new HttpServerAddress(
            proxyConfiguration.getDefaultHost(), proxyConfiguration.getDefaultPort());
        LOG.config("Proxying " + requestURI + " to default server " + result);
        return result;
    }

    @Override
    public String toString() {
        return "standard pyramid server resolver";
    }

    private HttpServerAddress pyramidIdToServerAddress(String pyramidId) throws IOException {
        final String pyramidConfiguration = PyramidApiTools.pyramidIdToConfiguration(pyramidId);
        final JsonObject config = PyramidApiTools.configurationToJson(pyramidConfiguration);
        final Path pyramidDir = PyramidApiTools.getPyramidPath(config);
        final JsonObject pyramidJson = PyramidApiTools.readDefaultPyramidConfiguration(pyramidDir);
        final String pyramidFormatName = PyramidApiTools.getFormatNameFromPyramidJson(pyramidJson);
        final HttpPyramidConfiguration.Service service = configuration.findServiceByFormatName(pyramidFormatName);
        if (service == null) {
            throw new IOException("Service not found for pyramid format \"" + pyramidFormatName + "\"");
        }
        return new HttpServerAddress(proxyConfiguration.getPyramidHost(), service.getPort());
    }

    private static Integer findServerPort(Parameters queryParameters) {
        final String[] values = queryParameters.getParameterValues(HttpPyramidConstants.SERVER_PORT_PARAMETER_NAME);
        if (values != null && values.length >= 1) {
            return Integer.valueOf(values[0]);
        }
        return null;
    }

    private static String findPyramidId(String requestURI, Parameters queryParameters) {
        final String[] values = queryParameters.getParameterValues(HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME);
        if (values != null && values.length >= 1) {
            return values[0];
        }
        return HttpPyramidApiTools.tryToFindPyramidIdInURLPath(requestURI);
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
