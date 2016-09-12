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

package net.algart.pyramid.api.http;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HttpPyramidConfiguration {
    public static final String GLOBAL_CONFIGURATION_FILE_NAME = ".global-configuration.json";
    public static final String CONFIGURATION_FILE_MASK = ".*.json";
    public static final String COMMON_CLASS_PATH_FIELD = "commonClassPath";

    public static class Service extends ConvertibleToJson {
        public static final String CLASS_PATH_FIELD = "classPath";

        private final Path configurationFile;
        private final String formatName;
        // - must be unique
        private final String groupId;
        private final String planePyramidFactory;
        private final String planePyramidFactoryConfiguration;
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
            this.formatName = getRequiredString(json, "formatName");
            this.groupId = getRequiredString(json, "groupId");
            this.planePyramidFactory = getRequiredString(json, "planePyramidFactory");
            this.planePyramidFactoryConfiguration = json.getString("planePyramidFactoryConfiguration", null);
            final JsonArray classPath = getRequiredJsonArray(json, CLASS_PATH_FIELD);
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
            final String memory = json.getString("memory", null);
            this.memory = memory != null ? parseLongWithMetricalSuffixes(memory) : null;
            this.port = getRequiredInt(json, "port");
            if (port <= 0 || port > HttpPyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid configuration JSON:"
                    + " invalid port number " + port
                    + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
            }
        }

        public Path getConfigurationFile() {
            return configurationFile;
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
            builder.add("formatName", formatName);
            builder.add("groupId", groupId);
            builder.add("planePyramidFactory", planePyramidFactory);
            builder.add("planePyramidFactoryConfiguration", planePyramidFactoryConfiguration);
            builder.add("classPath", toJsonArray(classPath));
            builder.add("vmOptions", toJsonArray(vmOptions));
            if (memory != null) {
                builder.add("memory", memory);
            }
            builder.add("port", port);
            return builder.build();
        }
    }

    public static class Process extends ConvertibleToJson {
        private final String groupId;
        private final List<Service> services;
        private final String workingDirectory;
        private final long requiredMemory;
        private HttpPyramidConfiguration parentConfiguration;

        private Process(String groupId, List<Service> services) {
            this.groupId = Objects.requireNonNull(groupId);
            this.services = Objects.requireNonNull(services);
            for (Service service : services) {
                service.parentProcess = this;
            }
            String workingDirectory = null;
            long requiredMemory = 0;
            for (Service service : services) {
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

        public Path workingDirectory() {
            if (workingDirectory == null) {
                return parentConfiguration.rootFolder.toAbsolutePath();
            } else {
                return parentConfiguration.rootFolder.resolve(workingDirectory).toAbsolutePath();
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
            for (Service service : services) {
                result.addAll(service.vmOptions);
            }
            result.addAll(parentConfiguration.commonVmOptions);
            return result;
        }

        public Long xmx() {
            final long commonMemory = parentConfiguration.commonMemory == null ? 0 : parentConfiguration.commonMemory;
            final long requiredMemory = Math.max(this.requiredMemory, commonMemory);
            return requiredMemory == 0 ? null : requiredMemory;
        }

        public String xmxOption() {
            return HttpPyramidConfiguration.xmxOption(xmx());
        }

        public HttpPyramidConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("groupId", groupId);
            builder.add("services", toJsonArray(services));
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

    public static class Proxy extends ConvertibleToJson {
        public static class PyramidServer extends ConvertibleToJson {
            private String host = "localhost";
            private String pathRegExp = HttpPyramidConstants.CommandPrefixes.PREXIX_START_REG_EXP;

            private PyramidServer(JsonObject json) {
                if (json != null) {
                    this.host = json.getString("host", host);
                    this.pathRegExp = json.getString("pathRegExp", pathRegExp);
                }
            }

            public String getHost() {
                return host;
            }

            public String getPathRegExp() {
                return pathRegExp;
            }

            public boolean uriMatches(String uriPath) {
                return uriPath.matches(pathRegExp);
            }

            public String toJsonString() {
                return toJson().toString();
            }

            JsonObject toJson() {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                builder.add("host", host);
                builder.add("pathRegExp", pathRegExp);
                return builder.build();
            }
        }

        public static class DefaultServer extends ConvertibleToJson {
            private String host = "localhost";
            private int port = 80;

            private DefaultServer(JsonObject json) {
                if (json != null) {
                    this.host = json.getString("host", host);
                    this.port = json.getInt("port", port);
                    if (port <= 0) {
                        throw new JsonException("Invalid configuration JSON:"
                            + " zero or negative default server port number " + port);
                    }
                }
            }

            public String getHost() {
                return host;
            }

            public int getPort() {
                return port;
            }

            public String toJsonString() {
                return toJson().toString();
            }

            JsonObject toJson() {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                builder.add("host", host);
                builder.add("port", port);
                return builder.build();
            }
        }

        private final int proxyPort;
        private final PyramidServer pyramidServer;
        private final DefaultServer defaultServer;
        private final HttpPyramidConfiguration parentConfiguration;

        private Proxy(JsonObject json, HttpPyramidConfiguration parentConfiguration) {
            Objects.requireNonNull(json);
            Objects.requireNonNull(parentConfiguration);
            this.proxyPort = getRequiredInt(json, "proxyPort");
            if (proxyPort <= 0 || proxyPort > HttpPyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid configuration JSON:"
                    + " invalid proxy port number " + proxyPort
                    + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
            }
            this.pyramidServer = new PyramidServer(json.getJsonObject("pyramidServer"));
            this.defaultServer = new DefaultServer(json.getJsonObject("defaultServer"));
            this.parentConfiguration = parentConfiguration;
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public PyramidServer getPyramidServer() {
            return pyramidServer;
        }

        public DefaultServer getDefaultServer() {
            return defaultServer;
        }

        public boolean hasWorkingDirectory() {
            // Maybe in future version we will allow proxy to specify own working directory
            return false;
        }

        public Path workingDirectory() {
            return parentConfiguration.rootFolder.toAbsolutePath();
        }

        public Collection<String> classPath(boolean resolve) {
            final Set<String> result = new TreeSet<>();
            for (String p : parentConfiguration.commonClassPath) {
                result.add(resolve ? parentConfiguration.resolveClassPath(p) : p);
            }
            return result;
        }

        public Collection<String> vmOptions() {
            return parentConfiguration.getCommonVmOptions();
        }

        public HttpPyramidConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        public Long xmx() {
            return parentConfiguration.getCommonMemory();
        }

        public String xmxOption() {
            return HttpPyramidConfiguration.xmxOption(xmx());
        }

        public String toJsonString() {
            return toJson().toString();
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("proxyPort", proxyPort);
            builder.add("pyramidServer", pyramidServer.toJson());
            builder.add("defaultServer", defaultServer.toJson());
            return builder.build();
        }
    }

    private final Map<String, Process> processes;
    private final Map<String, Service> allServices;
    private final Path rootFolder;
    private final Path globalConfigurationFile;
    private final Set<String> commonClassPath;
    // - some common JARs used by all processes: common open-source API
    private final Set<String> commonVmOptions;
    // - for example, here we can add -ea -esa to all processes
    private final Long commonMemory;
    // - actual -Xmx for every process is a maximum of this value and its xmx()
    private String systemCommandsFolder;
    private final Proxy proxy;

    private HttpPyramidConfiguration(
        Path rootFolder, Path globalConfigurationFile, JsonObject globalConfiguration, Map<String, Process> processes)
    {
        Objects.requireNonNull(rootFolder);
        Objects.requireNonNull(globalConfigurationFile);
        Objects.requireNonNull(globalConfiguration);
        Objects.requireNonNull(processes);
        this.rootFolder = rootFolder;
        this.globalConfigurationFile = globalConfigurationFile;
        this.processes = processes;
        final List<Process> processList = new ArrayList<>(processes.values());
        for (Process process : processList) {
            process.parentConfiguration = this;
        }
        final JsonObject proxy = globalConfiguration.getJsonObject("proxy");
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
        final String commonMemory = globalConfiguration.getString("commonMemory", null);
        this.commonMemory = commonMemory != null ? parseLongWithMetricalSuffixes(commonMemory) : null;
        this.systemCommandsFolder = globalConfiguration.getString("systemCommandsFolder",
            HttpPyramidConstants.DEFAULT_SYSTEM_COMMANDS_FOLDER);
        this.proxy = proxy == null ? null : new Proxy(proxy, this);
        this.allServices = new LinkedHashMap<>();
        for (Process process : processList) {
            for (Service service : process.services) {
                if (allServices.putIfAbsent(service.formatName, service) != null) {
                    throw new JsonException("Invalid configuration JSON: "
                        + "two or more services with the single format \"" + service.formatName + "\"");
                }
            }
        }
    }

    public Map<String, Process> getProcesses() {
        return Collections.unmodifiableMap(processes);
    }

    public Process getProcess(String groupId) {
        return processes.get(groupId);
    }

    /**
     * Returns proxy configuraion.
     * @return proxy configuraion; may be <tt>null</tt>.
     */
    public Path getRootFolder() {
        return rootFolder;
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

    public String getSystemCommandsFolder() {
        return systemCommandsFolder;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public Path systemCommandsFolder() {
        return rootFolder.resolve(systemCommandsFolder).toAbsolutePath();
    }

    public Collection<String> allGroupId() {
        return Collections.unmodifiableSet(processes.keySet());
    }

    public Map<String, Service> allServices() {
        return Collections.unmodifiableMap(allServices);
    }

    public Service findServiceByFormatName(String formatName) {
        return allServices.get(formatName);
    }

    public int numberOfProcesses() {
        return processes.size();
    }

    public int numberOfServices() {
        return allServices.size();
    }

    public int numberOfProcessServices(String groupId) {
        return processes.get(groupId).services.size();
    }

    public String toJsonString(boolean includeServices) {
        return toJson(includeServices).toString();
    }

    public String toString() {
        JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter)) {
            jsonWriter.writeObject(toJson(true));
            return stringWriter.toString();
        }
    }

    public static HttpPyramidConfiguration readConfigurationFromFolder(Path configurationFolder)
        throws IOException
    {
        Objects.requireNonNull(configurationFolder, "Null configurationFolder");
        final Path globalConfigurationFile = configurationFolder.resolve(GLOBAL_CONFIGURATION_FILE_NAME);
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(
            configurationFolder, CONFIGURATION_FILE_MASK))
        {
            return readConfigurationFromFiles(configurationFolder, globalConfigurationFile, files);
        }
    }

    public static HttpPyramidConfiguration readConfigurationFromFiles(
        Path configurationFolder,
        Path globalConfigurationFile,
        Iterable<Path> configurationFiles)
        throws IOException
    {
        Objects.requireNonNull(configurationFolder, "Null configurationFolder");
        Objects.requireNonNull(globalConfigurationFile, "Null globalConfigurationFile");
        Objects.requireNonNull(configurationFiles, "Null configurationFiles");
        if (!Files.isDirectory(configurationFolder)) {
            throw new FileNotFoundException(configurationFolder + " is not an existing folder");
        }
        if (!Files.isRegularFile(globalConfigurationFile)) {
            throw new FileNotFoundException(globalConfigurationFile + " is not an existing file");
        }
        final JsonObject globalConfiguration = readJson(globalConfigurationFile);
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
        return new HttpPyramidConfiguration(
            configurationFolder,
            globalConfigurationFile,
            globalConfiguration,
            processes);
    }

    public static Path getCurrentJREHome() {
        String s = System.getProperty("java.home");
        if (s == null) {
            throw new InternalError("Null java.home system property");
        }
        return Paths.get(s);
    }

    public static Path getJavaExecutable(Path jreHome) throws FileNotFoundException {
        // Finding according http://docs.oracle.com/javase/1.5.0/docs/tooldocs/solaris/jdkfiles.html
        if (jreHome == null) {
            throw new NullPointerException("Null jreHome argument");
        }
        if (!Files.exists(jreHome)) {
            throw new FileNotFoundException("JRE home directory " + jreHome + " does not exist");
        }
        Path javaBin = jreHome.resolve("bin");
        Path javaFile = javaBin.resolve("java"); // Unix
        if (!Files.exists(javaFile)) {
            javaFile = javaBin.resolve("java.exe"); // Windows
        }
        if (!Files.exists(javaFile)) {
            throw new FileNotFoundException("Cannot find java utility at " + javaFile);
        }
        return javaFile;
    }

    private String resolveClassPath(String p) {
        return rootFolder.resolve(p).toAbsolutePath().normalize().toString();
    }

    private JsonObject toJson(boolean includeServices) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        if (includeServices) {
            builder.add("processes", toJsonArray(processes.values()));
        }
        builder.add(COMMON_CLASS_PATH_FIELD, toJsonArray(commonClassPath));
        builder.add("commonVmOptions", toJsonArray(commonVmOptions));
        if (commonMemory != null) {
            builder.add("commonMemory", commonMemory);
        }
        if (proxy != null) {
            builder.add("proxy", proxy.toJson());
        }
        return builder.build();
    }

    private static String xmxOption(Long xmx) {
        if (xmx == null) {
            return null;
        } else if (xmx % (1024 * 1024 * 1024) == 0) {
            return "-Xmx" + xmx / (1024 * 1024 * 1024) + "g";
        } else if (xmx % (1024 * 1024) == 0) {
            return "-Xmx" + xmx / (1024 * 1024) + "m";
        } else if (xmx % 1024 == 0) {
            return "-Xmx" + xmx / 1024 + "k";
        } else {
            return "-Xmx" + xmx;
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

    private static String getRequiredString(JsonObject json, String name) {
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

    // Class, not interface: method toJson should not be public
    private static abstract class ConvertibleToJson {
        abstract JsonObject toJson();
    }
}
