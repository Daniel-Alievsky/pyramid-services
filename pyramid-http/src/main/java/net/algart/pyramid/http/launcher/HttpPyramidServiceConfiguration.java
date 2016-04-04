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

package net.algart.pyramid.http.launcher;

import javax.json.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HttpPyramidServiceConfiguration {
    private static final String GLOBAL_CONFIGURATION_FILE_NAME = ".global-configuration.json";

    public static class ViewerService implements ConvertibleToJson {
        private final String formatName;
        private final String groupId;
        private final String planePyramidFactory;
        private final String planePyramidFactoryConfiguration;
        private final Set<String> classPath;
        private final Set<String> vmOptions;
        // - classPath and vmOptions of all services of the single processes are joined
        private final int port;

        private ViewerService(JsonObject json) {
            Objects.requireNonNull(json);
            this.formatName = getRequiredString(json, "formatName");
            this.groupId = getRequiredString(json, "groupId");
            this.planePyramidFactory = getRequiredString(json, "planePyramidFactory");
            this.planePyramidFactoryConfiguration = json.getString("planePyramidFactoryConfiguration", null);
            final JsonArray classPath = getRequiredJsonArray(json, "classPath");
            this.classPath = new TreeSet<>();
            for (int k = 0, n = classPath.size(); k < n; k++) {
                this.classPath.add(classPath.getString(k));
            }
            final JsonArray vmOptions = json.getJsonArray("vmOptions");
            this.vmOptions = new TreeSet<>();
            if (vmOptions != null) {
                for (int k = 0, n = vmOptions.size(); k < n; k++) {
                    this.vmOptions.add(vmOptions.getString(k));
                }
            }
            this.port = getRequiredInt(json, "port");
        }

        public String getFormatName() {
            return formatName;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getPlanePyramidFactory() {
            return planePyramidFactory;
        }

        public String getPlanePyramidFactoryConfiguration() {
            return planePyramidFactoryConfiguration;
        }

        public Collection<String> getClassPath() {
            return Collections.unmodifiableSet(classPath);
        }

        public Collection<String> getVmOptions() {
            return Collections.unmodifiableSet(vmOptions);
        }

        public int getPort() {
            return port;
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("formatName", formatName);
            builder.add("groupId", groupId);
            builder.add("planePyramidFactory", planePyramidFactory);
            builder.add("planePyramidFactoryConfiguration", planePyramidFactoryConfiguration);
            builder.add("classPath", toJsonArray(classPath));
            builder.add("vmOptions", toJsonArray(vmOptions));
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class ViewerProcess implements ConvertibleToJson {
        private final List<ViewerService> viewerServices;

        private ViewerProcess(List<ViewerService> viewerServices) {
            this.viewerServices = viewerServices;
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("viewerServices", toJsonArray(viewerServices));
            return builder.build();
        }

        public Collection<String> getClassPath() {
            final Set<String> result = new TreeSet<>();
            for (ViewerService viewerService : viewerServices) {
                result.addAll(viewerService.classPath);
            }
            return result;
        }

        public Collection<String> getVmOptions() {
            final Set<String> result = new TreeSet<>();
            for (ViewerService viewerService : viewerServices) {
                result.addAll(viewerService.vmOptions);
            }
            return result;
        }

    }

    private final List<ViewerProcess> viewerProcesses;
    private final Set<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final Set<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes

    private HttpPyramidServiceConfiguration(JsonObject globalConfiguration, List<ViewerProcess> viewerProcesses) {
        Objects.requireNonNull(globalConfiguration);
        Objects.requireNonNull(viewerProcesses);
        this.viewerProcesses = viewerProcesses;
        final JsonArray commonClassPath = globalConfiguration.getJsonArray("commonClassPath");
        this.commonClassPath = new TreeSet<>();
        if (commonClassPath != null) {
            for (int k = 0, n = commonClassPath.size(); k < n; k++) {
                this.commonClassPath.add(commonClassPath.getString(k));
            }
        }
        final JsonArray commonVmOptions = globalConfiguration.getJsonArray("commonVmOptions");
        this.commonVmOptions = new TreeSet<>();
        if (commonVmOptions != null) {
            for (int k = 0, n = commonVmOptions.size(); k < n; k++) {
                this.commonVmOptions.add(commonVmOptions.getString(k));
            }
        }
    }

    public List<ViewerProcess> getViewerProcesses() {
        return Collections.unmodifiableList(viewerProcesses);
    }

    public Collection<String> getCommonClassPath() {
        return Collections.unmodifiableSet(commonClassPath);
    }

    public Collection<String> getCommonVmOptions() {
        return Collections.unmodifiableSet(commonVmOptions);
    }

    public String toString() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("viewerProcesses", toJsonArray(viewerProcesses));
        builder.add("commonClassPath", toJsonArray(commonClassPath));
        builder.add("commonVmOptions", toJsonArray(commonVmOptions));
        return builder.build().toString();
    }

    public static HttpPyramidServiceConfiguration getConfiguration(Path configurationFolder) throws IOException {
        final Path globalConfigurationFile = configurationFolder.resolve(GLOBAL_CONFIGURATION_FILE_NAME);
        if (!Files.isRegularFile(globalConfigurationFile)) {
            throw new FileNotFoundException(globalConfigurationFile + " not found");
        }
        final JsonObject globalConfiguration = readJson(globalConfigurationFile);
        final LinkedHashMap<String, List<ViewerService>> groups = new LinkedHashMap<>();
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(configurationFolder, ".*.json")) {
            for (Path file : files) {
                final String fileName = file.getFileName().toString();
                if (fileName.equals(GLOBAL_CONFIGURATION_FILE_NAME)) {
                    continue;
                }
                final ViewerService viewerService = new ViewerService(readJson(file));
                List<ViewerService> group = groups.get(viewerService.groupId);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(viewerService.groupId, group);
                }
                group.add(viewerService);
            }
        }
        final List<ViewerProcess> viewerProcesses = new ArrayList<>();
        for (List<ViewerService> group : groups.values()) {
            viewerProcesses.add(new ViewerProcess(group));
        }
        return new HttpPyramidServiceConfiguration(globalConfiguration, viewerProcesses);
    }

    private static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.getString();
    }

    private static int getRequiredInt(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result.intValue();
    }

    private static JsonArray getRequiredJsonArray(JsonObject json, String name) {
        final JsonArray result = json.getJsonArray(name);
        if (result == null) {
            throw new JsonException("Invalid JSON: \"" + name + "\" value required");
        }
        return result;
    }

    private static JsonArray toJsonArray(Collection<?> collection) {
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

    private static JsonObject readJson(Path path) throws IOException {
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(path, StandardCharsets.UTF_8)))
        {
            return reader.readObject();
        }
    }

    private interface ConvertibleToJson {
        JsonObject toJson();
    }
}
