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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StandardPyramidDataConfiguration {
    private final JsonObject pyramidDataJson;
    private final Path pyramidDataFile;
    private final String formatName;

    private StandardPyramidDataConfiguration(Path pyramidPath) throws IOException {
        final Path pyramidDataConfigFile = pyramidPath.resolve(PyramidConstants.PYRAMID_DATA_CONFIG_FILE_NAME);
        if (!Files.exists(pyramidDataConfigFile)) {
            this.pyramidDataJson = Json.createObjectBuilder().build();
        } else {
            try (final JsonReader reader = Json.createReader(Files.newBufferedReader(
                pyramidDataConfigFile, StandardCharsets.UTF_8)))
            {
                this.pyramidDataJson = reader.readObject();
            } catch (JsonException e) {
                throw new IOException("Invalid pyramid data configuration json "
                    + pyramidDataConfigFile.toAbsolutePath(), e);
            }
        }
        final String formatName = this.pyramidDataJson.getString(
            PyramidConstants.FORMAT_NAME_IN_PYRAMID_DATA_CONFIG_FILE, null);
        final String dataFileName = pyramidDataJson.getString(
            PyramidConstants.FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE, null);
        if (dataFileName == null) {
            //TODO!! detect
            throw new IOException("Invalid pyramid data configuration json "
                + pyramidDataConfigFile.toAbsolutePath() + ": no \""
                + PyramidConstants.FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE + "\" value");
        }
        if (formatName == null) {
            //TODO!! detect
            throw new IOException("Invalid pyramid configuration json "
                + pyramidDataConfigFile.toAbsolutePath() + ": no \""
                + PyramidConstants.FORMAT_NAME_IN_PYRAMID_DATA_CONFIG_FILE + "\" value");
        }
        this.pyramidDataFile = pyramidPath.resolve(dataFileName);
        if (!Files.exists(pyramidDataFile)) {
            throw new IOException("Pyramid data file at " + pyramidDataFile + " does not exist");
        }
        this.formatName = formatName;
    }

    public static StandardPyramidDataConfiguration readFromPyramidFolder(Path pyramidPath) throws IOException {
        return new StandardPyramidDataConfiguration(pyramidPath);
    }

    public JsonObject getPyramidDataJson() {
        return pyramidDataJson;
    }

    public Path getPyramidDataFile() {
        return pyramidDataFile;
    }

    public String getFormatName() {
        return formatName;
    }
}
