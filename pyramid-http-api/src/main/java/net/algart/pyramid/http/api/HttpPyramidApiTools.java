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

package net.algart.pyramid.http.api;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

public class HttpPyramidApiTools {
    private static final boolean ENABLE_ALL_CHARACTERS_IN_PYRAMID_ID_FOR_DEBUG = false;
    // - must be false for secure working

    private HttpPyramidApiTools() {
    }

    public static boolean isAllowedPyramidId(String pyramidId) {
        Objects.requireNonNull(pyramidId, "Null pyramidId");
        if (ENABLE_ALL_CHARACTERS_IN_PYRAMID_ID_FOR_DEBUG) {
            // - dangerous solution: in any case we must disable characters like / \ . ..
            return true;
        }
        return pyramidId.matches("^[A-Za-z0-9_\\-]*$");
    }

    public static Path keyFile(Path systemCommandsFolder, String urlPrefix, int port) {
        final String keyFileFrefix = String.format(Locale.US, HttpPyramidConstants.SYSTEM_COMMANDS_FILE_PREFIX, port);
        return systemCommandsFolder.resolve(keyFileFrefix + urlPrefix.substring(1, urlPrefix.length()));
    }

    /**
     * Appends pyramid ID by special prefix and postfix. It is useful when the pyramid ID is not a separate
     * argument with name {@link HttpPyramidConstants#PYRAMID_ID_ARGUMENT_NAME}, but a part of path
     * (like in TMS request). It allows to find pyramid ID in URL by 3rd party services like proxy.
     *
     * @param pyramidId the pyramid unique identifier.
     * @return          this ID appedned by {@link HttpPyramidConstants#PYRAMID_ID_PREFIX_IN_PATHNAME}
     *                  and {@link HttpPyramidConstants#PYRAMID_ID_POSTFIX_IN_PATHNAME}.
     */
    public static String appendPyramidIdForURLPath(String pyramidId) {
        return HttpPyramidConstants.PYRAMID_ID_PREFIX_IN_PATHNAME
            + pyramidId
            + HttpPyramidConstants.PYRAMID_ID_POSTFIX_IN_PATHNAME;
    }

    public static String extractPyramidIdFromAppendedIdForURLPath(String urlEncodedAppendedId) {
        final String appendedId;
        try {
            appendedId = URLDecoder.decode(urlEncodedAppendedId, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 encoding not found");
        }
        if (!appendedId.startsWith(HttpPyramidConstants.PYRAMID_ID_PREFIX_IN_PATHNAME)
            || !appendedId.endsWith(HttpPyramidConstants.PYRAMID_ID_POSTFIX_IN_PATHNAME))
        {
            throw new IllegalArgumentException("Invalid path syntax: pyramid ID must be written as \""
                + appendPyramidIdForURLPath("XXXXXXXX")
                + "\", but it is actually written as \"" + appendedId + "\"");
        }
        return appendedId.substring(
            HttpPyramidConstants.PYRAMID_ID_PREFIX_IN_PATHNAME.length(),
            appendedId.length() - HttpPyramidConstants.PYRAMID_ID_POSTFIX_IN_PATHNAME.length());
    }

    public static String tryToFindPyramidIdInURLPath(String path) {
        int p = path.indexOf(HttpPyramidConstants.PYRAMID_ID_PREFIX_IN_PATHNAME);
        if (p == -1) {
            return null;
        }
        int q = path.indexOf(HttpPyramidConstants.PYRAMID_ID_POSTFIX_IN_PATHNAME, p);
        if (q == -1) {
            return null;
        }
        q += HttpPyramidConstants.PYRAMID_ID_POSTFIX_IN_PATHNAME.length();
        return extractPyramidIdFromAppendedIdForURLPath(path.substring(p, q));
    }

    public static String pyramidIdToConfiguration(String pyramidId) throws IOException {
        if (!isAllowedPyramidId(pyramidId)) {
            throw new IllegalArgumentException("Disallowed pyramid id: \"" + pyramidId + "\"");
        }
        final Path path = Paths.get(
            HttpPyramidConstants.CONFIG_ROOT_DIR, pyramidId, HttpPyramidConstants.CONFIG_FILE_NAME);
        if (!Files.isRegularFile(path)) {
            throw new FileNotFoundException("File " + path.toAbsolutePath() + " does not exists");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static JsonObject configurationToJson(String pyramidConfiguration) throws IOException {
        try {
            return Json.createReader(new StringReader(pyramidConfiguration)).readObject();
        } catch (JsonException e) {
            throw new IOException("Invalid configuration json: <<<" + pyramidConfiguration + ">>>", e);
        }
    }

    public static Path getDefaultPyramidPath(JsonObject pyramidConfigurationJson) throws IOException {
        final String pyramidPath = pyramidConfigurationJson.getString(
            HttpPyramidConstants.DEFAULT_PYRAMID_PATH_NAME, null);
        if (pyramidPath == null) {
            throw new IOException("Invalid configuration json: no " + HttpPyramidConstants.DEFAULT_PYRAMID_PATH_NAME
                + " value <<<" + pyramidConfigurationJson + ">>>");
        }
        return Paths.get(pyramidPath);
    }

    public static JsonObject readDefaultPyramidConfiguration(Path pyramidPath) throws IOException {
        final Path pyramidConfigurationFile = pyramidPath.resolve(".pp.json");
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(
            pyramidConfigurationFile, StandardCharsets.UTF_8)))
        {
            return reader.readObject();
        } catch (JsonException e) {
            throw new IOException("Invalid pyramid configuration json at " + pyramidConfigurationFile, e);
        }
    }
}
