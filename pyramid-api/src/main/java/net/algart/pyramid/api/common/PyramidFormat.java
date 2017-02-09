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

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import java.nio.file.Path;
import java.util.*;

public class PyramidFormat {
    private final String formatName;
    private final Set<String> extensions;

    private PyramidFormat(String formatName, Set<String> extensions) {
        this.formatName = Objects.requireNonNull(formatName, "Null formatName");
        this.extensions = Objects.requireNonNull(extensions, "Null extensions");
    }

    public static PyramidFormat getInstance(String formatName, Set<String> extensions) {
        return new PyramidFormat(formatName, extensions);
    }

    public static PyramidFormat getInstance(JsonObject json) {
        final String formatName = getRequiredString(json,
            PyramidConstants.FORMAT_NAME_IN_PYRAMID_FACTORY_CONFIGURATION_JSON);
        final JsonArray jsonExtensions = json.getJsonArray(
            PyramidConstants.EXTENSIONS_IN_PYRAMID_FACTORY_CONFIGURATION_JSON);
        final Set<String> extensions = new LinkedHashSet<>();
        if (jsonExtensions != null) {
            for (int k = 0, n = jsonExtensions.size(); k < n; k++) {
                extensions.add(jsonExtensions.getString(k).toLowerCase());
            }
        }
        return getInstance(formatName, extensions);
    }

    public String getFormatName() {
        return formatName;
    }

    public Collection<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }

    public boolean matches(Path pyramidDataFile) {
        Objects.requireNonNull(pyramidDataFile, "Null pyramidDataFile");
        final String extension = getFileExtension(pyramidDataFile.getFileName().toString());
        return extension != null && extensions.contains(extension.toLowerCase());
    }

    @Override
    public String toString() {
        return "pyramid format \"" + formatName + "\", extensions " + extensions;
    }

    static String getFileExtension(String fileName) {
        int p = fileName.lastIndexOf('.');
        if (p == -1) {
            return null;
        }
        return fileName.substring(p + 1);
    }

    private static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid factory configuration JSON: \"" + name
                + "\" value required <<<" + json + ">>>");
        }
        return result.getString();
    }
}
