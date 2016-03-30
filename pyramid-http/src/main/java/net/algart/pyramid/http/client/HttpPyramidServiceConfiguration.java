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

package net.algart.pyramid.http.client;

import javax.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HttpPyramidServiceConfiguration {
    public static class ViewerService implements ConvertibleToJson {
        private final String formatName;
        private final String planePyramidFactory;
        private final String planePyramidFactoryConfiguration;
        private final int port;

        public ViewerService(JsonObject json) {
            Objects.requireNonNull(json);
            this.formatName = getRequiredString(json, "formatName");
            this.planePyramidFactory = getRequiredString(json, "planePyramidFactory");
            this.planePyramidFactoryConfiguration = json.getString("planePyramidFactoryConfiguration", null);
            this.port = getRequiredInt(json, "port");
        }

        public String getFormatName() {
            return formatName;
        }

        public String getPlanePyramidFactory() {
            return planePyramidFactory;
        }

        public String getPlanePyramidFactoryConfiguration() {
            return planePyramidFactoryConfiguration;
        }

        public int getPort() {
            return port;
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("formatName", formatName);
            builder.add("planePyramidFactory", planePyramidFactory);
            builder.add("planePyramidFactoryConfiguration", planePyramidFactoryConfiguration);
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class ViewerProcess implements ConvertibleToJson {
        private final List<String> classPath;
        private final List<String> vmOptions;
        private final List<ViewerService> viewerServices;

        public ViewerProcess(JsonObject json) {
            Objects.requireNonNull(json);
            final JsonArray classPath = json.getJsonArray("classPath");
            this.classPath = new ArrayList<>();
            for (int k = 0, n = classPath.size(); k < n; k++) {
                this.classPath.add(classPath.getString(k));
            }
            final JsonArray vmOptions = json.getJsonArray("vmOptions");
            this.vmOptions = new ArrayList<>();
            for (int k = 0, n = vmOptions.size(); k < n; k++) {
                this.vmOptions.add(vmOptions.getString(k));
            }
            final JsonArray viewerServices = json.getJsonArray("viewerServices");
            this.viewerServices = new ArrayList<>();
            for (int k = 0, n = viewerServices.size(); k < n; k++) {
                this.viewerServices.add(new ViewerService(viewerServices.getJsonObject(k)));
            }
        }

        public List<String> getClassPath() {
            return classPath;
        }

        public List<String> getVmOptions() {
            return vmOptions;
        }

        public List<ViewerService> getViewerServices() {
            return Collections.unmodifiableList(viewerServices);
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("classPath", toJsonArray(classPath));
            builder.add("vmOptions", toJsonArray(vmOptions));
            builder.add("viewerServices", toJsonArray(viewerServices));
            return builder.build();
        }
    }

    private final List<ViewerProcess> viewerProcesses;
    private final List<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final List<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes

    private HttpPyramidServiceConfiguration(JsonObject configuration) {
        Objects.requireNonNull(configuration);
        final JsonArray viewerProcesses = configuration.getJsonArray("viewerProcesses");
        this.viewerProcesses = new ArrayList<>();
        for (int k = 0, n = viewerProcesses.size(); k < n; k++) {
            this.viewerProcesses.add(new ViewerProcess(viewerProcesses.getJsonObject(k)));
        }
        final JsonArray commonClassPath = configuration.getJsonArray("commonClassPath");
        this.commonClassPath = new ArrayList<>();
        for (int k = 0, n = commonClassPath.size(); k < n; k++) {
            this.commonClassPath.add(commonClassPath.getString(k));
        }
        final JsonArray commonVmOptions = configuration.getJsonArray("commonVmOptions");
        this.commonVmOptions = new ArrayList<>();
        for (int k = 0, n = commonVmOptions.size(); k < n; k++) {
            this.commonVmOptions.add(commonVmOptions.getString(k));
        }
    }

    public List<ViewerProcess> getViewerProcesses() {
        return Collections.unmodifiableList(viewerProcesses);
    }

    public List<String> getCommonClassPath() {
        return Collections.unmodifiableList(commonClassPath);
    }

    public List<String> getCommonVmOptions() {
        return Collections.unmodifiableList(commonVmOptions);
    }

    public JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("viewerProcesses", toJsonArray(viewerProcesses));
        builder.add("commonClassPath", toJsonArray(commonClassPath));
        builder.add("commonVmOptions", toJsonArray(commonVmOptions));
        return builder.build();
    }

    public static HttpPyramidServiceConfiguration getConfiguration(JsonObject configuration) {
        return new HttpPyramidServiceConfiguration(configuration);
    }

    public static HttpPyramidServiceConfiguration getConfiguration(Path configurationFile) throws IOException {
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(
            configurationFile.resolve(".pp.json"), StandardCharsets.UTF_8)))
        {
            return getConfiguration(reader.readObject());
        }
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

    private static JsonArray toJsonArray(List<?> list) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for(Object o : list) {
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

    interface ConvertibleToJson {
        JsonObject toJson();
    }
}
