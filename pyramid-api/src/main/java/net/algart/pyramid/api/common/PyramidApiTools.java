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
    private PyramidApiTools() {
    }

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

    public static String pyramidIdToConfiguration(String pyramidId, String configRootDir, String configFileName)
        throws IOException
    {
        if (!isAllowedPyramidId(pyramidId)) {
            throw new IllegalArgumentException("Disallowed pyramid id: \"" + pyramidId + "\"");
        }
        Objects.requireNonNull(configRootDir, "Null configRootDir");
        Objects.requireNonNull(configFileName, "Null configFileName");
        final Path path = Paths.get(configRootDir, pyramidId, configFileName);
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
            PyramidConstants.PYRAMID_PATH_NAME_IN_CONFIGURATION_JSON, null);
        if (pyramidPath == null) {
            throw new IOException("Invalid configuration json: no "
                + PyramidConstants.PYRAMID_PATH_NAME_IN_CONFIGURATION_JSON
                + " value <<<" + configurationJson + ">>>");
        }
        return Paths.get(pyramidPath);
    }

    public static JsonObject readPyramidConfiguration(Path pyramidPath) throws IOException {
        final Path pyramidConfigurationFile = pyramidPath.resolve(
            PyramidConstants.PYRAMID_DATA_CONFIG_FILE_NAME);
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
            PyramidConstants.FORMAT_NAME_IN_PYRAMID_DATA_CONFIG_FILE, null);
        if (pyramidFormatName == null) {
            throw new IOException("Invalid pyramid configuration json ("
                + PyramidConstants.PYRAMID_DATA_CONFIG_FILE_NAME + "): no "
                + PyramidConstants.FORMAT_NAME_IN_PYRAMID_DATA_CONFIG_FILE
                + " value <<<" + pyramidJson + ">>>");
        }
        return pyramidFormatName;
    }

    public static String currentJREHome() {
        String s = System.getProperty("java.home");
        if (s == null) {
            throw new IllegalStateException("Null java.home system property");
        }
        return s;
    }

    public static Path javaExecutable(String jreHome) throws FileNotFoundException {
        // Finding executable file according http://docs.oracle.com/javase/1.5.0/docs/tooldocs/solaris/jdkfiles.html
        if (jreHome == null) {
            throw new NullPointerException("Null jreHome argument");
        }
        final Path jrePath = Paths.get(jreHome);
        if (!Files.exists(jrePath)) {
            throw new FileNotFoundException("JRE home directory " + jrePath + " does not exist");
        }
        Path javaBin = jrePath.resolve("bin");
        Path javaFile = javaBin.resolve("java"); // Unix
        if (!Files.exists(javaFile)) {
            javaFile = javaBin.resolve("java.exe"); // Windows
        }
        if (!Files.exists(javaFile)) {
            throw new FileNotFoundException("Cannot find java utility at " + javaFile);
        }
        return javaFile;
    }
}
