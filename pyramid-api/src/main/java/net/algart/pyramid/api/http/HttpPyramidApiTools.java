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

import java.nio.file.Path;
import java.util.Locale;

public class HttpPyramidApiTools {
    private HttpPyramidApiTools() {
    }

    public static Path keyFile(Path systemCommandsFolder, String urlPrefix, int port) {
        final String keyFileFrefix = String.format(Locale.US, HttpPyramidConstants.SYSTEM_COMMANDS_FILE_PREFIX, port);
        return systemCommandsFolder.resolve(keyFileFrefix + urlPrefix.substring(1, urlPrefix.length()));
    }

    public static boolean isUriPyramidCommand(String uriPath) {
        return uriPath.matches(HttpPyramidConstants.CommandPrefixes.PREXIX_START_REG_EXP);
    }
}
