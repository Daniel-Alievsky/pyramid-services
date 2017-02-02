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

package net.algart.pyramid.http.proxy;

import net.algart.http.proxy.HttpServerAddress;
import net.algart.http.proxy.HttpServerResolver;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.common.StandardPyramidDataConfiguration;
import net.algart.pyramid.api.http.*;
import org.glassfish.grizzly.http.util.Parameters;

import javax.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

class StandardPyramidServerResolver implements HttpServerResolver {

    private static final int POOL_SIZE = 500000;
    // - several megabytes as a maximum
    private static final Logger LOG = Logger.getLogger(StandardPyramidServerResolver.class.getName());

    private final Map<String, HttpServerAddress> pool = new ServerAddressHashMap();
    private final HttpPyramidServicesConfiguration configuration;
    private final HttpPyramidSpecificServerConfiguration specificServerConfiguration;
    private final HttpPyramidSpecificServerConfiguration.ProxySettings proxyConfiguration;
    private final List<HttpPyramidIdFinder> pyramidIdFinders = new ArrayList<>();
    private final Object lock = new Object();

    StandardPyramidServerResolver(
        HttpPyramidServicesConfiguration configuration,
        HttpPyramidSpecificServerConfiguration specificServerConfiguration)
    {
        assert configuration != null && specificServerConfiguration != null;
        assert specificServerConfiguration.getProxySettings() != null;
        this.configuration = configuration;
        this.specificServerConfiguration = specificServerConfiguration;
        this.proxyConfiguration = specificServerConfiguration.getProxySettings();
    }

    public void addPyramidIdFinder(HttpPyramidIdFinder pyramidIdFinder) {
        Objects.requireNonNull(pyramidIdFinder, "Null pyramidIdFinder");
        this.pyramidIdFinders.add(pyramidIdFinder);
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
        if (proxyConfiguration.getDefaultServer().isEnable()) {
            HttpServerAddress result = new HttpServerAddress(
                proxyConfiguration.getDefaultServer().getHost(), proxyConfiguration.getDefaultServer().getPort());
            LOG.config("Proxying " + requestURI + " to default server " + result);
            return result;
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "standard pyramid server resolver";
    }

    private HttpServerAddress pyramidIdToServerAddress(String pyramidId) throws IOException {
        final String pyramidConfiguration = PyramidApiTools.pyramidIdToConfiguration(
            pyramidId,
            specificServerConfiguration.getConfigRootDir(),
            specificServerConfiguration.getConfigFileName());
        final JsonObject config = PyramidApiTools.pyramidConfigurationToJson(pyramidConfiguration);
        final Path pyramidPath = PyramidApiTools.getPyramidPath(config);
        final StandardPyramidDataConfiguration pyramidDataConfiguration =
            StandardPyramidDataConfiguration.readFromPyramidFolder(pyramidPath);
        final String formatName = pyramidDataConfiguration.getFormatName();
        final HttpPyramidServicesConfiguration.Service service = configuration.findServiceByFormatName(formatName);
        if (service == null) {
            throw new IOException("Service not found for pyramid format \"" + formatName + "\"");
        }
        return new HttpServerAddress(proxyConfiguration.getPyramidHost(), service.getPort());
    }

    private String findPyramidId(String requestURI, Parameters queryParameters) {
        final String[] values = queryParameters.getParameterValues(HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME);
        if (values != null && values.length >= 1) {
            return values[0];
        }
        for (HttpPyramidIdFinder finder : pyramidIdFinders) {
            final String pyramidId = finder.findPyramidId(requestURI);
            if (pyramidId != null) {
                return pyramidId;
            }
        }
        return null;
    }


    private static Integer findServerPort(Parameters queryParameters) {
        final String[] values = queryParameters.getParameterValues(HttpPyramidConstants.SERVER_PORT_PARAMETER_NAME);
        if (values != null && values.length >= 1) {
            return Integer.valueOf(values[0]);
        }
        return null;
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
