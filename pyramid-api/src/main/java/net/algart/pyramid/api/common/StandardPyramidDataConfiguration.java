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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StandardPyramidDataConfiguration {
    private final JsonObject pyramidDataJson;
    private final Path pyramidDataFile;
    private final String formatName;
    private final String subFormatName;

    private StandardPyramidDataConfiguration(Path pyramidPath, List<PyramidFormat> supportedFormats)
        throws IOException
    {
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
        PyramidFormat currentFormat = null;
        for (PyramidFormat format : supportedFormats) {
            if (formatName != null && formatName.equals(format.getFormatName())) {
                currentFormat = format;
                break;
            }
        }
        final String dataFileName = pyramidDataJson.getString(
            PyramidConstants.FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE, null);
        Path dataFile = dataFileName == null ? null : pyramidPath.resolve(dataFileName);
        if (dataFile == null) {
            final List<Path> matched = new ArrayList<>();
            try (final DirectoryStream<Path> files = Files.newDirectoryStream(pyramidPath)) {
                for (Path file : files) {
                    for (PyramidFormat format :
                        (currentFormat != null ? Collections.singleton(currentFormat) : supportedFormats))
                    {
                        if (format.matches(file)) {
                            matched.add(file);
                        }
                    }
                }
            }
            if (matched.size() == 1) {
                dataFile = matched.get(0);
            } else {
                throw new IOException("Cannot find pyramid data file: pyramid data configuration json "
                    + pyramidDataConfigFile.toAbsolutePath() + " does not contains \""
                    + PyramidConstants.FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE + "\" value, and there are "
                    + (matched.isEmpty() ? "no" : "more than 1") + " files with suitable extensions");
            }
        }
        if (currentFormat == null) {
            for (PyramidFormat format : supportedFormats) {
                if (format.matches(dataFile)) {
                    currentFormat = format;
                    break;
                }
            }
            if (currentFormat == null) {
                throw new IOException("Cannot detect pyramid format: pyramid data configuration json "
                    + pyramidDataConfigFile.toAbsolutePath() + " does not contains \""
                    + PyramidConstants.FORMAT_NAME_IN_PYRAMID_DATA_CONFIG_FILE + "\" value, and an extension "
                    + "of the pyramid data file " + dataFile + " does not match any supported format");
            }
        }
        this.pyramidDataFile = dataFile;
        if (!Files.exists(pyramidDataFile)) {
            throw new IOException("Pyramid data file at " + pyramidDataFile + " does not exist");
        }
        this.formatName = currentFormat.getFormatName();
        this.subFormatName = PyramidFormat.getFileExtension(pyramidDataFile.getFileName().toString().toLowerCase());
    }

    public static StandardPyramidDataConfiguration readFromPyramidFolder(
        Path pyramidPath,
        List<PyramidFormat> supportedFormats)
        throws IOException
    {
        return new StandardPyramidDataConfiguration(pyramidPath, supportedFormats);
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

    public String getSubFormatName() {
        return subFormatName;
    }
}
