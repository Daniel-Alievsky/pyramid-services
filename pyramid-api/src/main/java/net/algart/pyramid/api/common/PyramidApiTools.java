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

package net.algart.pyramid.api.common;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class PyramidApiTools {
    private static final boolean ENABLE_ALL_CHARACTERS_IN_PYRAMID_ID_FOR_DEBUG = false;
    // - must be false for secure working

    public static boolean isAllowedPyramidId(String pyramidId) {
        Objects.requireNonNull(pyramidId, "Null pyramidId");
        if (ENABLE_ALL_CHARACTERS_IN_PYRAMID_ID_FOR_DEBUG) {
            // - dangerous solution: in any case we must disable characters like / \ . ..
            return true;
        }
        return pyramidId.matches("^[A-Za-z0-9_\\-]*$");
    }

    public static String pyramidIdToConfiguration(String pyramidId) throws IOException {
        if (!isAllowedPyramidId(pyramidId)) {
            throw new IllegalArgumentException("Disallowed pyramid id: \"" + pyramidId + "\"");
        }
        final Path path = Paths.get(
            PyramidConstants.CONFIG_ROOT_DIR, pyramidId, PyramidConstants.CONFIG_FILE_NAME);
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

    public static Path getPyramidPath(JsonObject configurationJson) throws IOException {
        final String pyramidPath = configurationJson.getString(
            PyramidConstants.DEFAULT_PYRAMID_PATH_NAME_IN_CONFIGURATION_JSON, null);
        if (pyramidPath == null) {
            throw new IOException("Invalid configuration json: no "
                + PyramidConstants.DEFAULT_PYRAMID_PATH_NAME_IN_CONFIGURATION_JSON
                + " value <<<" + configurationJson + ">>>");
        }
        return Paths.get(pyramidPath);
    }

    public static JsonObject readDefaultPyramidConfiguration(Path pyramidPath) throws IOException {
        final Path pyramidConfigurationFile = pyramidPath.resolve(
            PyramidConstants.DEFAULT_PYRAMID_CONFIGURATION_FILE_NAME);
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(
            pyramidConfigurationFile, StandardCharsets.UTF_8)))
        {
            return reader.readObject();
        } catch (JsonException e) {
            throw new IOException("Invalid pyramid configuration json at " + pyramidConfigurationFile, e);
        }
    }

    public static String getFormatNameFromPyramidJson(JsonObject pyramidJson) throws IOException {
        final String pyramidFormatName = pyramidJson.getString(
            PyramidConstants.DEFAULT_FORMAT_NAME_IN_PYRAMID_CONFIGURATION_FILE_NAME, null);
        if (pyramidFormatName == null) {
            throw new IOException("Invalid pyramid configuration json ("
                + PyramidConstants.DEFAULT_PYRAMID_CONFIGURATION_FILE_NAME + "): no "
                + PyramidConstants.DEFAULT_FORMAT_NAME_IN_PYRAMID_CONFIGURATION_FILE_NAME
                + " value <<<" + pyramidJson + ">>>");
        }
        return pyramidFormatName;
    }

    private PyramidApiTools() {
    }
}
