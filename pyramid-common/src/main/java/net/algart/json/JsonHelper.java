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

package net.algart.json;

import javax.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JsonHelper {
    private JsonHelper() {
    }

    public static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.getString();
    }

    public static int getRequiredInt(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.intValueExact();
    }

    public static long getRequiredLong(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.longValueExact();
    }

    public static double getRequiredDouble(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.doubleValue();
    }

    public static JsonArray getRequiredJsonArray(JsonObject json, String name) {
        final JsonArray result = json.getJsonArray(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result;
    }

    public static JsonArray toJsonArray(Collection<?> collection) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Object o : collection) {
            if (o instanceof String) {
                builder.add((String) o);
            } else if (o instanceof ConvertibleToJson) {
                builder.add(((ConvertibleToJson) o).toJson());
            } else {
                throw new AssertionError();
            }
        }
        return builder.build();
    }

    public static List<String> toStringList(JsonArray jsonArray) {
        final List<String> result = new ArrayList<>();
        for (JsonValue value : jsonArray) {
            result.add(((JsonString) value).getString());
        }
        return result;
    }

    public static JsonObject readJson(Path path) throws IOException {
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            return reader.readObject();
        }
    }
}
