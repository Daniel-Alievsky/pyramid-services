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

import net.algart.pyramid.api.http.HttpPyramidApiTools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

// Unlike usual commands, all system commands are executed as a reply to appearing special key files.
abstract class SystemCommand {
    final HttpPyramidService httpPyramidService;
    final String urlPrefix;

    SystemCommand(HttpPyramidService httpPyramidService, String urlPrefix) {
        this.httpPyramidService = Objects.requireNonNull(httpPyramidService, "Null httpPyramidService");
        this.urlPrefix = Objects.requireNonNull(urlPrefix, "Null urlPrefix");
        if (urlPrefix.length() < 2 || urlPrefix.charAt(0) != '/') {
            throw new AssertionError("Illegal URL prefix");
        }
    }

    abstract void service() throws IOException;

    Path keyFile() {
        return HttpPyramidApiTools.keyFile(
            urlPrefix,
            httpPyramidService.getPort(),
            httpPyramidService.getSystemCommandsFolder());
    }
}
