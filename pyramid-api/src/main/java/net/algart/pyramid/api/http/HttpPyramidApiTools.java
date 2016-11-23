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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

public class HttpPyramidApiTools {
    private HttpPyramidApiTools() {
    }

    public static Path keyFile(Path systemCommandsFolder, String urlPrefix, int port) {
        final String keyFileFrefix = String.format(Locale.US, HttpPyramidConstants.SYSTEM_COMMANDS_FILE_PREFIX, port);
        return systemCommandsFolder.resolve(keyFileFrefix + urlPrefix.substring(1, urlPrefix.length()));
    }

    /**
     * <p>Appends pyramid ID by special prefix and separator. It is useful when the pyramid ID is not a separate
     * argument with name {@link HttpPyramidConstants#PYRAMID_ID_PARAMETER_NAME}, but a part of path
     * (like in TMS request). It allows to find pyramid ID in URL by 3rd party services like proxy.</p>
     *
     * <p>This function is used mostly for debugging and error messages.</p>
     *
     * @param pyramidId the pyramid unique identifier.
     * @return          this ID appended by {@link HttpPyramidConstants#PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME}
     *                  and {@link HttpPyramidConstants#SEPARATOR_AFTER_PYRAMID_ID_IN_PATHNAME}.
     */
    public static String appendPyramidIdForURLPath(String pyramidId) {
        return HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME
            + pyramidId
            + HttpPyramidConstants.SEPARATOR_AFTER_PYRAMID_ID_IN_PATHNAME;
    }

    public static String removePrefixBeforePyramidIdFromURLPath(String urlEncodedPrefixedId) {
        final String prefixedId;
        try {
            prefixedId = URLDecoder.decode(urlEncodedPrefixedId, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 encoding not found");
        }
        if (!prefixedId.startsWith(HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME)) {
            throw new IllegalArgumentException("Invalid path syntax: pyramid ID must be written as \""
                + HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME + "XXXXXXXX"
                + "\", but it is actually written as \"" + prefixedId + "\"");
        }
        return prefixedId.substring(HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME.length());
    }

    public static String tryToFindPyramidIdInURLPath(String path) {
        int p = path.indexOf(HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME);
        if (p == -1) {
            return null;
        }
        int q = path.indexOf(HttpPyramidConstants.SEPARATOR_AFTER_PYRAMID_ID_IN_PATHNAME,
            p + HttpPyramidConstants.PREFIX_BEFORE_PYRAMID_ID_IN_PATHNAME.length());
        if (q == -1) {
            return null;
        }
        return removePrefixBeforePyramidIdFromURLPath(path.substring(p, q));
    }

    public static boolean isUriPyramidCommand(String uriPath) {
        return uriPath.matches(HttpPyramidConstants.CommandPrefixes.PREXIX_START_REG_EXP);
    }
}
