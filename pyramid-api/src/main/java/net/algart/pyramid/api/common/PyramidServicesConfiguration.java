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

/**
 * Configuration of the set of services. Usually created on the compilation stage
 * and copied to the server without changes.
 */
public class PyramidServicesConfiguration {
    public static final String GLOBAL_CONFIGURATION_FILE_NAME = ".global-configuration.json";
    public static final String CONFIGURATION_FILE_MASK = ".*.json";
    public static final String COMMON_CLASS_PATH_FIELD = "commonClassPath";

    private static final String SERVICES_LOGGING_CONFIG_FILE = PyramidConstants.getStringEnv(
        "NET_ALGART_PYRAMID_PYRAMID_SERVICES_LOGGING_CONFIG_FILE", null);

    private static volatile boolean globalConfigurationFileRequired = true;

    public static class Service extends ConvertibleToJson {
        public static final String CLASS_PATH_FIELD = "classPath";

        private final Path configurationFile;
        private final PyramidFormat pyramidFormat;
        // - pyramidFormat.formatName must be unique
        private final String groupId;
        private final String planePyramidFactory;
        private final String planePyramidSubFactory;
        private final String formatAnalysisUtility;
        private final String jreName;
        private final Set<String> classPath;
        private final Set<String> vmOptions;
        // - classPath and vmOptions of all services of the single processes are joined
        private final String workingDirectory;
        // - workingDirectory of all services of the single processes MUST be null or an identical string
        private final int port;
        private final Long memory;
        private Process parentProcess;

        private Service(Path configurationFile, JsonObject json) {
            Objects.requireNonNull(configurationFile);
            Objects.requireNonNull(json);
            this.configurationFile = configurationFile;
            this.pyramidFormat = PyramidFormat.getInstance(json);
            this.groupId = getRequiredString(json, "groupId", configurationFile);
            this.planePyramidFactory = getRequiredString(json, "planePyramidFactory", configurationFile);
            this.planePyramidSubFactory = json.getString(
                PyramidConstants.PLANE_PYRAMID_SUB_FACTORY_IN_PYRAMID_FACTORY_CONFIGURATION_JSON,null);
            this.formatAnalysisUtility = json.getString("formatAnalysisUtility", null);
            this.jreName = json.getString("jreName", null);
            final JsonArray classPath = getRequiredJsonArray(json, CLASS_PATH_FIELD, configurationFile);
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
            this.workingDirectory = json.getString("workingDirectory", null);
            final String memory = getStringOrInt(json, "memory");
            this.memory = memory != null ? parseLongWithMetricalSuffixes(memory) : null;
            this.port = getRequiredInt(json, "port", configurationFile);
            if (port <= 0 || port > PyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid configuration JSON " + configurationFile + ":"
                    + " invalid port number " + port
                    + " (must be in range 1.." + PyramidConstants.MAX_ALLOWED_PORT + ")");
            }
        }

        public Path getConfigurationFile() {
            return configurationFile;
        }

        public PyramidFormat getPyramidFormat() {
            return pyramidFormat;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getPlanePyramidFactory() {
            return planePyramidFactory;
        }

        // Note: usually this method is not called directly; instead, the custom pyramid factory extracts this value
        // from JSON via PyramidConstants.PLANE_PYRAMID_SUB_FACTORY_IN_PYRAMID_FACTORY_CONFIGURATION_JSON key.
        public String getPlanePyramidSubFactory() {
            return planePyramidSubFactory;
        }

        public String getFormatAnalysisUtility() {
            return formatAnalysisUtility;
        }

        public String getJREName() {
            return jreName;
        }

        public Collection<String> getClassPath() {
            return Collections.unmodifiableSet(classPath);
        }

        public Collection<String> getVmOptions() {
            return Collections.unmodifiableSet(vmOptions);
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public Long getMemory() {
            return memory;
        }

        public int getPort() {
            return port;
        }

        public Process parentProcess() {
            return parentProcess;
        }

        public String toJsonString() {
            return toJson().toString();
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("formatName", pyramidFormat.getFormatName());
            if (pyramidFormat.getFormatTitle() != null) {
                builder.add("formatTitle", pyramidFormat.getFormatTitle());
            }
            builder.add("fileRegExp", pyramidFormat.getFileRegExp());
            if (pyramidFormat.getFileInFolderRegExp() != null) {
                builder.add("fileInFolderRegExp", pyramidFormat.getFileInFolderRegExp());
            }
            if (!pyramidFormat.getAccompanyingFiles().isEmpty()) {
                builder.add("accompanyingFiles", toJsonArray(pyramidFormat.getAccompanyingFiles()));
            }
            builder.add("accompanyingFilesRequired", pyramidFormat.isAccompanyingFilesRequired());
            builder.add("recognitionPriority", pyramidFormat.getRecognitionPriority());
            builder.add("groupId", groupId);
            builder.add("planePyramidFactory", planePyramidFactory);
            if (planePyramidSubFactory != null) {
                builder.add("planePyramidSubFactory", planePyramidSubFactory);
            }
            if (formatAnalysisUtility != null) {
                builder.add("formatAnalysisUtility", formatAnalysisUtility);
            }
            if (jreName != null) {
                builder.add("jreName", jreName);
            }
            builder.add("classPath", toJsonArray(classPath));
            builder.add("vmOptions", toJsonArray(vmOptions));
            if (workingDirectory != null) {
                builder.add("workingDirectory", workingDirectory);
            }
            if (memory != null) {
                builder.add("memory", toStringWithMetricalSuffixes(memory));
            }
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class Process extends ConvertibleToJson {
        private final String groupId;
        private final List<Service> services;
        private final String jreName;
        private final String workingDirectory;
        private final long requiredMemory;
        private PyramidServicesConfiguration parentConfiguration;

        private Process(String groupId, List<Service> services) {
            this.groupId = Objects.requireNonNull(groupId);
            this.services = Objects.requireNonNull(services);
            for (Service service : services) {
                service.parentProcess = this;
            }
            String jreName = null;
            String workingDirectory = null;
            long requiredMemory = 0;
            for (Service service : services) {
                if (service.jreName != null) {
                    if (jreName == null) {
                        jreName = service.jreName;
                    } else {
                        if (!jreName.equals(service.jreName)) {
                            throw new JsonException("Invalid configuration JSON:"
                                + " two services of the process with groupId=\"" + groupId
                                + "\" require different JRE names \""
                                + jreName + "\" and \"" + service.jreName+ "\"");
                        }
                    }
                }
                if (service.workingDirectory != null) {
                    if (workingDirectory == null) {
                        workingDirectory = service.workingDirectory;
                    } else {
                        if (!workingDirectory.equals(service.workingDirectory)) {
                            throw new JsonException("Invalid configuration JSON:"
                                + " two services of the process with groupId=\"" + groupId
                                + "\" require different working directories \""
                                + workingDirectory + "\" and \"" + service.workingDirectory + "\"");
                        }
                    }
                }
                requiredMemory += service.memory == null ? 0 : service.memory;
            }
            this.jreName = jreName;
            this.workingDirectory = workingDirectory;
            this.requiredMemory = requiredMemory;
        }

        public String getGroupId() {
            return groupId;
        }

        public List<Service> getServices() {
            return Collections.unmodifiableList(services);
        }

        public boolean hasWorkingDirectory() {
            return workingDirectory != null;
        }

        public String getWorkingDirectory() {
            return workingDirectory;
        }

        public List<Integer> allPorts() {
            final List<Integer> result = new ArrayList<>();
            for (Service service : services) {
                result.add(service.getPort());
            }
            return result;
        }

        public String jreName() {
            return jreName;
        }

        public Path workingDirectory() {
            if (workingDirectory == null) {
                return parentConfiguration.pyramidServicesFolder.toAbsolutePath();
            } else {
                return parentConfiguration.pyramidServicesFolder.resolve(workingDirectory).toAbsolutePath();
            }
        }

        public Collection<String> classPath(boolean resolve) {
            final Set<String> result = new TreeSet<>();
            for (Service service : services) {
                for (String p : service.classPath) {
                    result.add(resolve ? parentConfiguration.resolveClassPath(p) : p);
                }
            }
            for (String p : parentConfiguration.commonClassPath) {
                result.add(resolve ? parentConfiguration.resolveClassPath(p) : p);
            }
            return result;
        }

        public Collection<String> vmOptions() {
            final Set<String> result = new TreeSet<>();
            if (SERVICES_LOGGING_CONFIG_FILE != null) {
                result.add("-Djava.util.logging.config.file=" + SERVICES_LOGGING_CONFIG_FILE);
            }
            for (Service service : services) {
                result.addAll(service.vmOptions);
            }
            result.addAll(parentConfiguration.commonVmOptions);
            return result;
        }

        public Long xmx() {
            final long commonMemory = parentConfiguration.commonMemory == null ? 0 : parentConfiguration.commonMemory;
            final long requiredMemory = this.requiredMemory != 0 ? this.requiredMemory : commonMemory;
            return requiredMemory == 0 ? null : requiredMemory;
        }

        public String xmxOption() {
            return PyramidServicesConfiguration.xmxOption(xmx());
        }

        public PyramidServicesConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("groupId", groupId);
            builder.add("services", toJsonArray(services));
            if (jreName != null) {
                builder.add("jreName", jreName);
            }
            if (workingDirectory != null) {
                builder.add("workingDirectory", workingDirectory);
            }
            builder.add("resolvedWorkingDirectory", workingDirectory().toString());
            builder.add("resolvedClassPath", toJsonArray(classPath(true)));
            builder.add("rawClassPath", toJsonArray(classPath(false)));
            builder.add("vmOption", toJsonArray(vmOptions()));
            final Long xmx = xmx();
            if (xmx != null) {
                builder.add("xmx", xmx);
            }
            return builder.build();
        }
    }

    private final Map<String, Process> processes;
    private final List<PyramidFormat> allSortedFormats;
    private final Map<String, Service> allSortedServices;
    private final Path projectRoot;
    private final Path pyramidServicesFolder;
    private final Path globalConfigurationFile;
    private final Set<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final Set<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes
    private final Long commonMemory;
    // - actual -Xmx for every process is a maximum of this value and its xmx()

    private PyramidServicesConfiguration(
        Path projectRoot,
        Path globalConfigurationFile,
        JsonObject globalConfiguration,
        Map<String, Process> processes)
    {
        Objects.requireNonNull(projectRoot);
        Objects.requireNonNull(globalConfigurationFile);
        Objects.requireNonNull(globalConfiguration);
        Objects.requireNonNull(processes);
        this.projectRoot = projectRoot;
        this.pyramidServicesFolder = projectRoot.resolve(PyramidConstants.PYRAMID_SERVICES_IN_PROJECT_ROOT);
        this.globalConfigurationFile = globalConfigurationFile;
        this.processes = processes;
        final List<Process> processList = new ArrayList<>(processes.values());
        for (Process process : processList) {
            process.parentConfiguration = this;
        }
        final JsonArray commonClassPath = globalConfiguration.getJsonArray(COMMON_CLASS_PATH_FIELD);
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
        final String commonMemory = getStringOrInt(globalConfiguration, "commonMemory");
        this.commonMemory = commonMemory != null ? parseLongWithMetricalSuffixes(commonMemory) : null;
        final Map<String, Service> services = new HashMap<>();
        this.allSortedFormats = new ArrayList<>();
        for (Process process : processList) {
            for (Service service : process.services) {
                if (services.putIfAbsent(service.pyramidFormat.getFormatName(), service) != null) {
                    throw new JsonException("Invalid configuration JSON: two or more services "
                        + "with the same format name \"" + service.pyramidFormat.getFormatName()+ "\"");
                }
                if (!allSortedFormats.add(service.pyramidFormat)) {
                    throw new AssertionError("Invalid comparator in " + PyramidFormat.class);
                }
            }
        }
        Collections.sort(allSortedFormats);
        this.allSortedServices = new LinkedHashMap<>();
        for (PyramidFormat pyramidFormat : allSortedFormats) {
            final String name = pyramidFormat.getFormatName();
            allSortedServices.put(name, services.get(name));
        }
    }

    public Map<String, Process> getProcesses() {
        return Collections.unmodifiableMap(processes);
    }

    public Process getProcess(String groupId) {
        return processes.get(groupId);
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path getPyramidServicesFolder() {
        return pyramidServicesFolder;
    }

    public Path getGlobalConfigurationFile() {
        return globalConfigurationFile;
    }

    public Collection<String> getCommonClassPath() {
        return Collections.unmodifiableSet(commonClassPath);
    }

    public Collection<String> getCommonVmOptions() {
        return Collections.unmodifiableSet(commonVmOptions);
    }

    public Long getCommonMemory() {
        return commonMemory;
    }

    public Collection<String> classPath(boolean resolve) {
        final Set<String> result = new TreeSet<>();
        for (String p : commonClassPath) {
            result.add(resolve ? resolveClassPath(p) : p);
        }
        return result;
    }

    public String commonXmxOption() {
        return xmxOption(commonMemory);
    }

    public Collection<String> allGroupId() {
        return Collections.unmodifiableSet(processes.keySet());
    }

    /**
     * Returns all pyramid formats from all services in order of decreasing
     * {@link PyramidFormat#getRecognitionPriority() recognition priority}: the maximal priority will be first,
     * the mininal will be last.
     *
     * @return all pyramid formats from all services sorted by decreasing recognition priority.
     */
    public Collection<PyramidFormat> allSortedFormats() {
        return Collections.unmodifiableList(allSortedFormats);
    }

    /**
     * Returns map of all services (name => service). This map is roted in order of decreasing
     * {@link PyramidFormat#getRecognitionPriority() recognition priority} of the format:
     * the service with maximal priority of its format will be first.
     *
     * @return the map of all services sorted by decreasing recognition priority.
     */
    public Map<String, Service> allSortedServices() {
        return Collections.unmodifiableMap(allSortedServices);
    }

    public Service findServiceByFormatName(String formatName) {
        return allSortedServices.get(formatName);
    }

    public int numberOfProcesses() {
        return processes.size();
    }

    public int numberOfServices() {
        return allSortedFormats.size();
    }

    public int numberOfProcessServices(String groupId) {
        return processes.get(groupId).services.size();
    }

    public String toJsonString(boolean includeServices) {
        return toJson(includeServices).toString();
    }

    public String toString() {
        return jsonToPrettyString(toJson(true));
    }

    public static PyramidServicesConfiguration readFromRootFolder(Path projectRoot) throws IOException {
        Objects.requireNonNull(projectRoot, "Null projectRoot");
        final Path configurationFolder = projectRoot.resolve(
            PyramidConstants.CONFIGURATION_FOLDER_IN_PROJECT_ROOT);
        if (!Files.isDirectory(configurationFolder)) {
            throw new FileNotFoundException("Configuration folder "
                + configurationFolder + " is not an existing folder");
        }
        final Path globalConfigurationFile = configurationFolder.resolve(GLOBAL_CONFIGURATION_FILE_NAME);
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(
            configurationFolder, CONFIGURATION_FILE_MASK))
        {
            return readFromFiles(projectRoot, globalConfigurationFile, files);
        }
    }

    public static PyramidServicesConfiguration readFromFiles(
        Path projectRoot,
        Path globalConfigurationFile,
        Iterable<Path> configurationFiles)
        throws IOException
    {
        Objects.requireNonNull(projectRoot, "Null projectRoot");
        Objects.requireNonNull(globalConfigurationFile, "Null globalConfigurationFile");
        Objects.requireNonNull(configurationFiles, "Null configurationFiles");
        if (!Files.isDirectory(projectRoot)) {
            throw new FileNotFoundException("Project root " + projectRoot + " is not an existing folder");
        }
        final JsonObject globalConfiguration;
        if (!Files.isRegularFile(globalConfigurationFile)) {
            if (globalConfigurationFileRequired) {
                throw new FileNotFoundException("Global configuration file "
                    + globalConfigurationFile + " is not an existing file");
            } else {
                globalConfiguration = Json.createObjectBuilder().build();
            }
        } else {
            globalConfiguration = readJson(globalConfigurationFile);
        }
        final String globalConfigurationFileName = globalConfigurationFile.getFileName().toString();
        final LinkedHashMap<String, List<Service>> groups = new LinkedHashMap<>();
        for (Path file : configurationFiles) {
            final String fileName = file.getFileName().toString();
            if (fileName.equals(globalConfigurationFileName)) {
                continue;
            }
            final Service service = new Service(file, readJson(file));
            List<Service> group = groups.get(service.groupId);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(service.groupId, group);
            }
            group.add(service);
        }
        final Map<String, Process> processes = new LinkedHashMap<>();
        for (Map.Entry<String, List<Service>> entry : groups.entrySet()) {
            final String groupId = entry.getKey();
            processes.put(groupId, new Process(groupId, entry.getValue()));
        }
        return new PyramidServicesConfiguration(
            projectRoot,
            globalConfigurationFile,
            globalConfiguration,
            processes);
    }

    /**
     * Allows to enable running when the global configuration file {@link #GLOBAL_CONFIGURATION_FILE_NAME}
     * does not exist (by calling with <tt>false</tt> argument).
     * Usually should not be called, but can be useful in service utilities.
     *
     * @param globalConfigurationFileRequired <tt>false</tt> if you want to enable work without
     *                                        global configuration file; default is <tt>true</tt>.
     */
    public static void setGlobalConfigurationFileRequired(boolean globalConfigurationFileRequired) {
        PyramidServicesConfiguration.globalConfigurationFileRequired = globalConfigurationFileRequired;
    }

    private static String getStringOrInt(JsonObject json, String name) {
        final JsonValue jsonValue = json.get(name);
        if (jsonValue instanceof JsonString) {
            return ((JsonString) jsonValue).getString();
        }
        if (jsonValue instanceof JsonNumber) {
            return jsonValue.toString();
        }
        return null;
    }

    private static String getRequiredString(JsonObject json, String name, Path file) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid configuration JSON " + file + ": \"" + name + "\" value required");
        }
        return result.getString();
    }

    private static int getRequiredInt(JsonObject json, String name, Path file) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid configuration JSON " + file + ": \"" + name + "\" value required");
        }
        return result.intValueExact();
    }

    private static JsonArray getRequiredJsonArray(JsonObject json, String name, Path file) {
        final JsonArray result = json.getJsonArray(name);
        if (result == null) {
            throw new JsonException("Invalid configuration JSON " + file + ": \"" + name + "\" value required");
        }
        return result;
    }

    private static JsonArray toJsonArray(Collection<?> collection) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Object o : collection) {
            if (o instanceof ConvertibleToJson) {
                builder.add(((ConvertibleToJson) o).toJson());
            } else {
                builder.add(String.valueOf(o));
            }
        }
        return builder.build();
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            return reader.readObject();
        }
    }

    private static String jsonToPrettyString(JsonObject json) {
        JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter)) {
            jsonWriter.writeObject(json);
            return stringWriter.toString();
        }
    }

    private String resolveClassPath(String p) {
        final boolean endsWithSlashAsterisk = p.endsWith("/*");
        // - it is allowed form for JVM class path
        if (endsWithSlashAsterisk) {
            p = p.substring(0, p.length() - 2);
        }
        String result = pyramidServicesFolder.resolve(p).toAbsolutePath().normalize().toString();
        if (endsWithSlashAsterisk) {
            if (!result.endsWith("/")) {
                result += "/";
                // - to be on the safe side
            }
            result += "*";
        }
        return result;
    }

    private JsonObject toJson(boolean includeServices) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        if (includeServices) {
            builder.add("processes", toJsonArray(processes.values()));
        }
        builder.add(COMMON_CLASS_PATH_FIELD, toJsonArray(commonClassPath));
        builder.add("commonVmOptions", toJsonArray(commonVmOptions));
        if (commonMemory != null) {
            builder.add("commonMemory", toStringWithMetricalSuffixes(commonMemory));
        }
        return builder.build();
    }

    private static String xmxOption(Long xmx) {
        if (xmx == null) {
            return null;
        } else {
            return "-Xmx" + toStringWithMetricalSuffixes(xmx);
        }
    }

    private static String toStringWithMetricalSuffixes(long memory) {
        if (memory % (1024 * 1024 * 1024) == 0) {
            return memory / (1024 * 1024 * 1024) + "g";
        } else if (memory % (1024 * 1024) == 0) {
            return memory / (1024 * 1024) + "m";
        } else if (memory % 1024 == 0) {
            return memory / 1024 + "k";
        } else {
            return String.valueOf(memory);
        }
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
