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

package net.algart.pyramid.api.http;

import java.util.Objects;

public class HttpPyramidIdFinderBetweenSlashsAfterPrefix implements HttpPyramidIdFinder {
    private final String requiredURLPrefix;

    public HttpPyramidIdFinderBetweenSlashsAfterPrefix(String requiredURLPrefix) {
        Objects.requireNonNull(requiredURLPrefix, "Null URL prefix");
        if (!requiredURLPrefix.endsWith("/")) {
            throw new IllegalArgumentException("URL prefix must end with /");
        }
        this.requiredURLPrefix = requiredURLPrefix;
    }

    @Override
    public String findPyramidId(String urlPath) {
        Objects.requireNonNull(urlPath, "Null path in URL");
        if (!urlPath.startsWith(requiredURLPrefix)) {
            return null;
        }
        int p = requiredURLPrefix.length();
        int q = urlPath.indexOf("/", p);
        if (q == -1) {
            return null;
        }
        return urlPath.substring(p, q);
    }
}
