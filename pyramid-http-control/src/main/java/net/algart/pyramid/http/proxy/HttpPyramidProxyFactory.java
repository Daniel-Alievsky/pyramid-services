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

import net.algart.http.proxy.HttpProxy;
import net.algart.pyramid.http.api.HttpPyramidConfiguration;

import java.util.Objects;

public class HttpPyramidProxyFactory {

    private final HttpPyramidConfiguration configuration;

    public HttpPyramidProxyFactory(HttpPyramidConfiguration configuration) {
        Objects.requireNonNull(configuration, "Null configuration");
        Objects.requireNonNull(configuration.getProxy(), "Proxy configuration not present");
        this.configuration = configuration;
    }

    public HttpProxy newProxy() {
        return new HttpProxy(
            configuration.getProxy().getProxyPort(),
            new StandardPlanePyramidServerResolver(configuration),
            new HttpPyramidServiceServerFailureHandler(configuration));
    }
}
