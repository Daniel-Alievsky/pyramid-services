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

package net.algart.pyramid.external;


import net.algart.pyramid.api.http.HttpPyramidServicesConfiguration;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ClassPathGroupResolver {

    private final HttpPyramidServicesConfiguration configuration;

    public ClassPathGroupResolver(HttpPyramidServicesConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    public Map<Path, JsonObject> resolveAllClassPaths() throws IOException {
        final Map<Path, JsonObject> result = new LinkedHashMap<>();
        for (HttpPyramidServicesConfiguration.Service service : configuration.allSortedServices().values()) {
            final JsonObject json = toJson(service.toJsonString());
            final JsonObject resolved = resolveClassPath(
                json, HttpPyramidServicesConfiguration.Service.CLASS_PATH_FIELD);
            if (resolved != null) {
                result.put(service.getConfigurationFile(), resolved);
            }
        }
        final JsonObject json = toJson(configuration.toJsonString(false));
        final JsonObject resolved = resolveClassPath(json, HttpPyramidServicesConfiguration.COMMON_CLASS_PATH_FIELD);
        if (resolved != null) {
            result.put(configuration.getGlobalConfigurationFile(), resolved);
        }
        return result;
    }

    public JsonObject resolveClassPath(JsonObject jsonWithClassPath, String classPathField) throws IOException {
        final JsonArray jsonClassPath = jsonWithClassPath.getJsonArray(classPathField);
        if (jsonClassPath == null) {
            // - possible for global configuration file
            return null;
        }
        Set<String> classPath = new TreeSet<>();
        for (int k = 0, n = jsonClassPath.size(); k < n; k++) {
            classPath.add(jsonClassPath.getString(k));
        }
        final Set<String> resolveClassPath = resolveClassPath(classPath);
        final boolean changed = !resolveClassPath.equals(classPath);
        if (changed) {
            return modifyJsonObject(jsonWithClassPath, classPathField, toJsonArray(resolveClassPath));
        } else {
            return null;
        }
    }

    public Set<String> resolveClassPath(Set<String> sourceClassPath) throws IOException {
        final Set<String> result = new LinkedHashSet<>();
        for (String path : sourceClassPath) {
            if (path.equals("*") || path.endsWith("/*") || path.endsWith(File.separator + "*")) {
                // java -cp syntax "somefolder/*" means all JARs in this folder
                final Path jarFolder = Paths.get(path.substring(0, Math.max(0, path.length() - 2)));
                // - Math.max for a case of single "*" (current folder)
                final Path actualJarFolder = configuration.getPyramidServicesFolder().resolve(jarFolder);
                final List<String> jarNames = new ArrayList<>();
                if (Files.exists(actualJarFolder)) {
                    try (final DirectoryStream<Path> jars = Files.newDirectoryStream(actualJarFolder, "*.jar")) {
                        for (Path jar : jars) {
                            jarNames.add(jar.getFileName().toString());
                        }
                    }
                }
                if (jarNames.isEmpty()) {
                    result.add(path);
                    // Special case: preserving "*" in for empty or non-existing directory.
                    // Maybe, the administrator will add some JARs there later,
                    // and JVM will be able to understand this without resolving.
                } else {
                    Collections.sort(jarNames);
                    for (String jarName : jarNames) {
                        result.add(jarFolder.resolve(jarName).toString().replace(File.separatorChar, '/'));
                    }
                }
            } else {
                result.add(path);
            }
        }
        return result;
    }

    private static JsonObject modifyJsonObject(JsonObject json, String fieldName, JsonValue newValue) {
        Objects.requireNonNull(fieldName);
        Objects.requireNonNull(newValue);
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : json.entrySet()) {
            builder.add(entry.getKey(), fieldName.equals(entry.getKey()) ? newValue : entry.getValue());
        }
        return builder.build();
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

    private static void writeJson(Path path, JsonObject json) throws IOException {
        JsonWriterFactory factory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        // Note: UTF-8 BOM is not added, and it is correct
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
            System.out.printf("Usage: %s [-f] projectRoot%n"
                    + "Without -f flag, it works in read-only mode.%n",
                ClassPathGroupResolver.class.getName());
            return;
        }
        HttpPyramidServicesConfiguration.setGlobalConfigurationFileRequired(false);
        final Path projectRoot = Paths.get(args[startIndex]);
        final HttpPyramidServicesConfiguration configuration =
            HttpPyramidServicesConfiguration.readFromRootFolder(projectRoot);
        final Map<Path, JsonObject> correctedJsons = new ClassPathGroupResolver(configuration).resolveAllClassPaths();
        if (correctedJsons.isEmpty()) {
            System.out.printf("Nothing to do%n");
        } else {
            for (Map.Entry<Path, JsonObject> entry : correctedJsons.entrySet()) {
                if (!readOnly) {
                    writeJson(entry.getKey(), entry.getValue());
                }
                System.out.printf("File %s %s changed%n", entry.getKey(), readOnly ? "should be" : "has been");
            }
        }
    }
}
