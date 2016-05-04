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

import net.algart.json.ConvertibleToJson;
import net.algart.json.JsonHelper;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HttpPyramidServiceConfiguration {
    private static final String GLOBAL_CONFIGURATION_FILE_NAME = ".global-configuration.json";

    public static class Service implements ConvertibleToJson {
        private final String formatName;
        private final String groupId;
        private final String planePyramidFactory;
        private final String planePyramidFactoryConfiguration;
        private final Set<String> classPath;
        private final Set<String> vmOptions;
        private final Long xmx;
        // - classPath and vmOptions of all services of the single processes are joined
        private final int port;
        private Process parent;

        private Service(JsonObject json) {
            Objects.requireNonNull(json);
            this.formatName = JsonHelper.getRequiredString(json, "formatName");
            this.groupId = JsonHelper.getRequiredString(json, "groupId");
            this.planePyramidFactory = JsonHelper.getRequiredString(json, "planePyramidFactory");
            this.planePyramidFactoryConfiguration = json.getString("planePyramidFactoryConfiguration", null);
            final JsonArray classPath = JsonHelper.getRequiredJsonArray(json, "classPath");
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
            this.port = JsonHelper.getRequiredInt(json, "port");
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

        public Process parent() {
            return parent;
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("formatName", formatName);
            builder.add("groupId", groupId);
            builder.add("planePyramidFactory", planePyramidFactory);
            builder.add("planePyramidFactoryConfiguration", planePyramidFactoryConfiguration);
            builder.add("classPath", JsonHelper.toJsonArray(classPath));
            builder.add("vmOptions", JsonHelper.toJsonArray(vmOptions));
            if (xmx != null) {
                builder.add("xmx", xmx);
            }
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class Process implements ConvertibleToJson {
        private final List<Service> services;
        private HttpPyramidServiceConfiguration parent;

        private Process(List<Service> services) {
            this.services = Objects.requireNonNull(services);
            for (Service service : services) {
                service.parent = this;
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

        public HttpPyramidServiceConfiguration parent() {
            return parent;
        }

        public JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("services", JsonHelper.toJsonArray(services));
            return builder.build();
        }
    }

    private final List<Process> processes;
    private final Set<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final Set<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes
    private final Long commonXmx;
    // - actual -Xmx for every process is a maximum of this value and xmx for all its services

    private HttpPyramidServiceConfiguration(JsonObject globalConfiguration, List<Process> processes) {
        Objects.requireNonNull(globalConfiguration);
        Objects.requireNonNull(processes);
        this.processes = processes;
        for (Process process : processes) {
            process.parent = this;
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
    }

    public List<Process> getProcesses() {
        return Collections.unmodifiableList(processes);
    }

    public Collection<String> getCommonClassPath() {
        return Collections.unmodifiableSet(commonClassPath);
    }

    public Collection<String> getCommonVmOptions() {
        return Collections.unmodifiableSet(commonVmOptions);
    }

    public String toString() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("processes", JsonHelper.toJsonArray(processes));
        builder.add("commonClassPath", JsonHelper.toJsonArray(commonClassPath));
        builder.add("commonVmOptions", JsonHelper.toJsonArray(commonVmOptions));
        if (commonXmx != null) {
            builder.add("commonXmx", commonXmx);
        }
        return builder.build().toString();
    }

    public static HttpPyramidServiceConfiguration getConfiguration(Path configurationFolder) throws IOException {
        final Path globalConfigurationFile = configurationFolder.resolve(GLOBAL_CONFIGURATION_FILE_NAME);
        if (!Files.isRegularFile(globalConfigurationFile)) {
            throw new FileNotFoundException(globalConfigurationFile + " not found");
        }
        final JsonObject globalConfiguration = JsonHelper.readJson(globalConfigurationFile);
        final LinkedHashMap<String, List<Service>> groups = new LinkedHashMap<>();
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(configurationFolder, ".*.json")) {
            for (Path file : files) {
                final String fileName = file.getFileName().toString();
                if (fileName.equals(GLOBAL_CONFIGURATION_FILE_NAME)) {
                    continue;
                }
                final Service service = new Service(JsonHelper.readJson(file));
                List<Service> group = groups.get(service.groupId);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(service.groupId, group);
                }
                group.add(service);
            }
        }
        final List<Process> processes = new ArrayList<>();
        for (List<Service> group : groups.values()) {
            processes.add(new Process(group));
        }
        return new HttpPyramidServiceConfiguration(globalConfiguration, processes);
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
}
