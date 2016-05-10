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

package net.algart.pyramid.http.api;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HttpPyramidServiceConfiguration {
    public static final String GLOBAL_CONFIGURATION_FILE_NAME = ".global-configuration.json";

    public static class Service extends ConvertibleToJson {
        private final String formatName;
        // - must be unique
        private final String groupId;
        private final String planePyramidFactory;
        private final String planePyramidFactoryConfiguration;
        private final Set<String> classPath;
        private final Set<String> vmOptions;
        private final Long xmx;
        // - classPath and vmOptions of all services of the single processes are joined
        private final int port;
        private Process parentProcess;

        private Service(JsonObject json) {
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

            final String xmx = json.getString("xmx", null);
            this.xmx = xmx != null ? parseLongWithMetricalSuffixes(xmx) : null;
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

        public Long getXmx() {
            return xmx;
        }

        public int getPort() {
            return port;
        }

        public Process parentProcess() {
            return parentProcess;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("formatName", formatName);
            builder.add("groupId", groupId);
            builder.add("planePyramidFactory", planePyramidFactory);
            builder.add("planePyramidFactoryConfiguration", planePyramidFactoryConfiguration);
            builder.add("classPath", toJsonArray(classPath));
            builder.add("vmOptions", toJsonArray(vmOptions));
            if (xmx != null) {
                builder.add("xmx", xmx);
            }
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class Process extends ConvertibleToJson {
        private final List<Service> services;
        private HttpPyramidServiceConfiguration parentConfiguration;

        private Process(List<Service> services) {
            this.services = Objects.requireNonNull(services);
            for (Service service : services) {
                service.parentProcess = this;
            }
        }

        public List<Service> getServices() {
            return Collections.unmodifiableList(services);
        }

        public Collection<String> getClassPath() {
            final Set<String> result = new TreeSet<>();
            for (Service service : services) {
                result.addAll(service.classPath);
            }
            return result;
        }

        public Collection<String> getVmOptions() {
            final Set<String> result = new TreeSet<>();
            for (Service service : services) {
                result.addAll(service.vmOptions);
            }
            return result;
        }

        public HttpPyramidServiceConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("services", toJsonArray(services));
            return builder.build();
        }
    }

    private final Map<String, Process> processes;
    private final Set<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final Set<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes
    private final Long commonXmx;
    // - actual -Xmx for every process is a maximum of this value and xmx for all its services
    private final Map<String, Service> allFormatServices;

    private HttpPyramidServiceConfiguration(JsonObject globalConfiguration, Map<String, Process> processes) {
        Objects.requireNonNull(globalConfiguration);
        Objects.requireNonNull(processes);
        this.processes = processes;
        final List<Process> processList = new ArrayList<>(processes.values());
        for (Process process : processList) {
            process.parentConfiguration = this;
        }
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
        final String commonXmx = globalConfiguration.getString("commonXmx", null);
        this.commonXmx = commonXmx != null ? parseLongWithMetricalSuffixes(commonXmx) : null;
        this.allFormatServices = new LinkedHashMap<>();
        for (Process process : processList) {
            for (Service service : process.services) {
                if (allFormatServices.putIfAbsent(service.formatName, service) != null) {
                    throw new JsonException("Invalid configuration JSON: "
                        + "two or more services with the single format \"" + service.formatName + "\"");
                }
            }
        }
    }

    public Map<String, Process> getProcesses() {
        return Collections.unmodifiableMap(processes);
    }

    public Collection<String> getCommonClassPath() {
        return Collections.unmodifiableSet(commonClassPath);
    }

    public Collection<String> getCommonVmOptions() {
        return Collections.unmodifiableSet(commonVmOptions);
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public String toString() {
        JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter)) {
            jsonWriter.writeObject(toJson());
            return stringWriter.toString();
        }
    }

    public static HttpPyramidServiceConfiguration readConfigurationFromFolder(Path configurationFolder)
        throws IOException
    {
        final Path globalConfigurationFile = configurationFolder.resolve(GLOBAL_CONFIGURATION_FILE_NAME);
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(configurationFolder, ".*.json")) {
            return readConfigurationFromFiles(globalConfigurationFile, files);
        }
    }

    public static HttpPyramidServiceConfiguration readConfigurationFromFiles(
        Path globalConfigurationFile,
        Iterable<Path> configurationFiles)
        throws IOException
    {
        if (!Files.isRegularFile(globalConfigurationFile)) {
            throw new FileNotFoundException(globalConfigurationFile + " not found");
        }
        final JsonObject globalConfiguration = readJson(globalConfigurationFile);
        final String globalConfigurationFileName = globalConfigurationFile.getFileName().toString();
        final LinkedHashMap<String, List<Service>> groups = new LinkedHashMap<>();
        for (Path file : configurationFiles) {
            final String fileName = file.getFileName().toString();
            if (fileName.equals(globalConfigurationFileName)) {
                continue;
            }
            final Service service = new Service(readJson(file));
            List<Service> group = groups.get(service.groupId);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(service.groupId, group);
            }
            group.add(service);
        }
        final Map<String, Process> processes = new LinkedHashMap<>();
        for (Map.Entry<String, List<Service>> entry : groups.entrySet()) {
            processes.put(entry.getKey(), new Process(entry.getValue()));
        }
        return new HttpPyramidServiceConfiguration(globalConfiguration, processes);
    }

    JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("processes", toJsonArray(processes.values()));
        builder.add("commonClassPath", toJsonArray(commonClassPath));
        builder.add("commonVmOptions", toJsonArray(commonVmOptions));
        if (commonXmx != null) {
            builder.add("commonXmx", commonXmx);
        }
        return builder.build();
    }

    private static long parseLongWithMetricalSuffixes(String s) {
        if (s == null) {
            throw new NumberFormatException("null");
        }
        int sh = 0;
        if (s.endsWith("K") || s.endsWith("k")) {
            sh = 10;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("M") || s.endsWith("m")) {
            sh = 20;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("G") || s.endsWith("g")) {
            sh = 30;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("T") || s.endsWith("t")) {
            sh = 40;
            s = s.substring(0, s.length() - 1);
        }
        long result = Long.parseLong(s);
        if (((result << sh) >> sh) != result) {
            // overflow
            throw new NumberFormatException("Too large 64-bit long integer value");
        }
        return result << sh;
    }

    private  static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid service configuration JSON: \"" + name + "\" value required");
        }
        return result.getString();
    }

    private static int getRequiredInt(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid service configuration JSON: \"" + name + "\" value required");
        }
        return result.intValueExact();
    }

    private static JsonArray getRequiredJsonArray(JsonObject json, String name) {
        final JsonArray result = json.getJsonArray(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid service configuration JSON: \"" + name + "\" value required");
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
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            return reader.readObject();
        }
    }

    // Class, not interface: method toJson should be not public
    private static abstract class ConvertibleToJson {
        abstract JsonObject toJson();
    }
}
