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

package net.algart.pyramid.external;


import net.algart.pyramid.http.api.HttpPyramidConfiguration;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ClassPathGroupResolver {

    private final HttpPyramidConfiguration configuration;

    public ClassPathGroupResolver(HttpPyramidConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    public Map<Path, JsonObject> resolveAllClassPaths() {
        final Map<Path, JsonObject> result = new LinkedHashMap<>();
        for (HttpPyramidConfiguration.Service service : configuration.allServices().values()) {
            final JsonObject json = toJson(service.toJsonString());
            if (resolveClassPath(json, HttpPyramidConfiguration.Service.CLASS_PATH_FIELD)) {
                result.put(service.getConfigurationFile(), json);
            }
        }
        final JsonObject json = toJson(configuration.toJsonString(false));
        if (resolveClassPath(json, HttpPyramidConfiguration.COMMON_CLASS_PATH_FIELD)) {
            result.put(configuration.getGlobalConfigurationFile(), json);
        }
        return result;
    }

    public boolean resolveClassPath(JsonObject jsonWithClassPath, String classPathField) {
        final JsonArray jsonClassPath = jsonWithClassPath.getJsonArray(classPathField);
        if (jsonClassPath == null) {
            return false;
            //TODO!! test this
        }
        Set<String> classPath = new TreeSet<>();
        for (int k = 0, n = jsonClassPath.size(); k < n; k++) {
            classPath.add(jsonClassPath.getString(k));
        }
        final Set<String> resolveClassPath = resolveClassPath(classPath);
        final boolean changed = !resolveClassPath.equals(classPath);
        if (changed) {
            //TODO!! test this
            jsonWithClassPath.put(classPathField, toJsonArray(resolveClassPath));
        }
        return changed;
    }

    public Set<String> resolveClassPath(Set<String> sourceClassPath) {
        throw new UnsupportedOperationException();
        //TODO!!
    }

    private static JsonArray toJsonArray(Collection<String> collection) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (String s : collection) {
            builder.add(s);
        }
        return builder.build();
    }

    private static JsonObject toJson(String jsonString) {
        try (final JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            return reader.readObject();
        }
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            return reader.readObject();
        }
    }

    private static void writeJson(Path path, JsonObject json) throws IOException {
        JsonWriterFactory factory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        //TODO!! what will be with BOM??
        try (final JsonWriter writer = factory.createWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.writeObject(json);
        }
    }

    public static void main(String[] args) throws IOException {
        int startIndex = 0;
        boolean readOnly = true;
        if (startIndex < args.length && args[startIndex].equals("-f")) {
            startIndex++;
            readOnly = false;
        }
        if (args.length < startIndex + 1) {
            System.out.printf("Usage: %s [-f] configurationFolder,%n"
                    + "Without -f flag, it works in read-only mode.%n",
                ClassPathGroupResolver.class.getName());
            return;
        }
        final Path configurationFolder = Paths.get(args[startIndex]);
        final HttpPyramidConfiguration configuration =
            HttpPyramidConfiguration.readConfigurationFromFolder(configurationFolder);
        final Map<Path, JsonObject> correctedJsons = new ClassPathGroupResolver(configuration).resolveAllClassPaths();
        for (Map.Entry<Path, JsonObject> entry : correctedJsons.entrySet()) {
            if (!readOnly) {
                writeJson(entry.getKey(), entry.getValue());
                System.out.printf("File %s %s changed%n", entry.getKey(), readOnly ? "should be" : "has been");
            }
        }
    }
}
