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

package net.algart.pyramid.api.http;

import net.algart.pyramid.api.common.IllegalJREException;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.common.PyramidConstants;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration of the pyramid proxy server and other features that can very for specific servers.
 * Customized by system administrator for each server.
 */
public class HttpServerConfiguration extends ConvertibleToJson {
    public static class JRE extends ConvertibleToJson {
        public static String DEFAULT_NAME = "default";

        private final String name;
        private final String homePath;
        private final HttpServerConfiguration parentConfiguration;

        private JRE(HttpServerConfiguration parentConfiguration, JsonObject json) {
            this.name = getRequiredString(json, "name",
                parentConfiguration.serverConfigurationFile);
            this.homePath = getRequiredString(json, "homePath",
                parentConfiguration.serverConfigurationFile);
            this.parentConfiguration = parentConfiguration;
        }

        public String getName() {
            return name;
        }

        public String getHomePath() {
            return homePath;
        }

        public HttpServerConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        public String toJsonString() {
            return toJson().toString();
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("name", name);
            builder.add("homePath", homePath);
            return builder.build();
        }
    }

    public static class SSLSettings extends ConvertibleToJson {
        private final String keystoreFile;
        private final String keystorePassword;
        private final String keyPassword;
        private final HttpServerConfiguration parentConfiguration;

        private SSLSettings(HttpServerConfiguration parentConfiguration, JsonObject json) {
            this.keystoreFile = getRequiredString(json, "keystoreFile",
                parentConfiguration.serverConfigurationFile);
            this.keystorePassword = getRequiredString(json, "keystorePassword",
                parentConfiguration.serverConfigurationFile);
            this.keyPassword = json.getString("keyPassword", keystorePassword);
            this.parentConfiguration = parentConfiguration;
        }

        public String getKeystoreFile() {
            return keystoreFile;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public Path keystoreFile() {
            return parentConfiguration.serverConfigurationFile
                .getParent().resolve(keystoreFile).toAbsolutePath();
        }

        public String toJsonString() {
            return toJson().toString();
        }

        public HttpServerConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("keystoreFile", keystoreFile);
            builder.add("keystorePassword", keystorePassword);
            builder.add("keyPassword", keyPassword);
            return builder.build();
        }
    }

    public static class ProxySettings extends ConvertibleToJson {
        public static class DefaultServer extends ConvertibleToJson {
            private final boolean enable;
            private final String host;
            private final int port;
            private final ProxySettings parentProxySettings;

            private DefaultServer(ProxySettings parentProxySettings, JsonObject json) {
                Objects.requireNonNull(parentProxySettings);
                if (json == null) {
                    json = Json.createObjectBuilder().build();
                }
                this.enable = json.getBoolean("enable", false);
                this.host = json.getString("host", "localhost");
                this.port = json.getInt("port", 80);
                if (port <= 0) {
                    throw new JsonException("Invalid proxy configuration JSON "
                        + parentProxySettings.parentConfiguration.serverConfigurationFile + ": "
                        + "zero or negative default server port number " + port);
                }
                this.parentProxySettings = parentProxySettings;
            }

            public boolean isEnable() {
                return enable;
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

            public ProxySettings parentProxySettings() {
                return parentProxySettings;
            }

            JsonObject toJson() {
                final JsonObjectBuilder builder = Json.createObjectBuilder();
                builder.add("enable", enable);
                builder.add("host", host);
                builder.add("port", port);
                return builder.build();
            }
        }

        private final int proxyPort;
        private final String pyramidHost;
        private final boolean ssl;
        private final DefaultServer defaultServer;
        private final boolean autoRevivingByManager;
        private final HttpServerConfiguration parentConfiguration;

        private ProxySettings(HttpServerConfiguration parentConfiguration, JsonObject json) {
            Objects.requireNonNull(parentConfiguration);
            Objects.requireNonNull(json);
            this.parentConfiguration = parentConfiguration;
            this.proxyPort = getRequiredInt(json, "proxyPort",
                parentConfiguration.serverConfigurationFile);
            if (proxyPort <= 0 || proxyPort > PyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid proxy configuration JSON"
                    + parentConfiguration.serverConfigurationFile + ":"
                    + " invalid proxy port number " + proxyPort
                    + " (must be in range 1.." + PyramidConstants.MAX_ALLOWED_PORT + ")");
            }
            this.pyramidHost = json.getString("pyramidHost", "localhost");
            this.ssl = json.getBoolean("ssl", false);
            this.defaultServer = new DefaultServer(this, json.getJsonObject("defaultServer"));
            this.autoRevivingByManager = json.getBoolean("autoRevivingByManager", false);
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public String getPyramidHost() {
            return pyramidHost;
        }

        public boolean isSsl() {
            return ssl;
        }

        public DefaultServer getDefaultServer() {
            return defaultServer;
        }

        /**
         * <p>Whether the manager should also revive proxy. (Usual services are revived always.)</p>
         *
         * <p>Note: we DO NOT recoomend to set this if the proxy is SSL, because attempt to check its state
         * can lead to an exception.</p>
         *
         * @return whether the manager should also revive proxy.
         */
        public boolean isAutoRevivingByManager() {
            return autoRevivingByManager;
        }

        public String toJsonString() {
            return toJson().toString();
        }

        public HttpServerConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("proxyPort", proxyPort);
            builder.add("pyramidHost", pyramidHost);
            builder.add("defaultServer", defaultServer.toJson());
            builder.add("ssl", ssl);
            builder.add("autoRevivingByManager", autoRevivingByManager);
            return builder.build();
        }
    }

    private final String configRootDir;
    private final String configFileName;
    private final String imagesRootDir;
    // - note that "imagesRootDir" is not used by pyramid services,
    // but can be useful for upload and user management systems
    private final Map<String, JRE> jreMap;
    private final boolean proxy;
    private final ProxySettings proxySettings;
    private final SSLSettings sslSettings;
    private final Path serverConfigurationFile;

    private HttpServerConfiguration(Path serverConfigurationFile, JsonObject json) {
        Objects.requireNonNull(serverConfigurationFile);
        Objects.requireNonNull(json);
        this.serverConfigurationFile = serverConfigurationFile;
        this.configRootDir = json.getString("configRootDir", PyramidConstants.DEFAULT_CONFIG_ROOT_DIR);
        this.configFileName = json.getString("configFileName", PyramidConstants.DEFAULT_CONFIG_FILE_NAME);
        this.imagesRootDir = json.getString("imagesRootDir", PyramidConstants.DEFAULT_IMAGES_ROOT_DIR);
        this.jreMap = new LinkedHashMap<>();
        final JsonArray jreListJson = json.getJsonArray("jre");
        if (jreListJson != null) {
            for (JsonValue jreJson : jreListJson) {
                if (jreJson instanceof JsonObject) {
                    final JRE jre = new JRE(this, ((JsonObject) jreJson));
                    this.jreMap.put(jre.name, jre);
                }
            }
        }
        this.proxy = json.getBoolean("proxy", false);
        final JsonObject proxySettingsJson = json.getJsonObject("proxySettings");
        if (proxy && proxySettingsJson == null) {
            throw new JsonException("Invalid configuration JSON " + serverConfigurationFile + ": "
                + "\"proxySettings\" value required when \"proxy\" mode is used");
        }
        this.proxySettings = proxySettingsJson == null ?
            null :
            new ProxySettings(this, proxySettingsJson);
        final JsonObject sslSettingsJson = json.getJsonObject("sslSettings");
        if (proxySettings != null && proxySettings.ssl && sslSettingsJson == null) {
            throw new JsonException("Invalid configuration JSON " + serverConfigurationFile + ": "
                + "\"sslSettings\" value required when \"proxySettings\".\"ssl\" mode is used");
        }
        this.sslSettings = sslSettingsJson == null ? null : new SSLSettings(this, sslSettingsJson);
    }

    public String getConfigRootDir() {
        return configRootDir;
    }

    public String getConfigFileName() {
        return configFileName;
    }

    public boolean hasProxy() {
        return proxy;
    }

    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    public SSLSettings getSslSettings() {
        return sslSettings;
    }

    public Path getServerConfigurationFile() {
        return serverConfigurationFile;
    }

    public String jreHome() {
        final JRE jre = jreMap.get(JRE.DEFAULT_NAME);
        return jre != null ? jre.getHomePath() : PyramidApiTools.currentJREHome();
    }

    public String jreHome(String jreName) throws IllegalJREException {
        if (jreName == null) {
            return jreHome();
        }
        final JRE jre = jreMap.get(jreName);
        if (jre == null) {
            throw new IllegalJREException("Unknown JRE with name \"" + jreName
                + "\": such JRE is not specified in " + serverConfigurationFile);
        }
        return jre.getHomePath();
    }

    public Path javaExecutable() throws IllegalJREException {
        return javaExecutable(null);
    }

    public Path javaExecutable(final String jreName) throws IllegalJREException {
        final String jreHome = jreHome(jreName);
        try {
            return PyramidApiTools.javaExecutable(jreHome);
        } catch (IllegalJREException e) {
            if (jreName == null && jreMap.get(JRE.DEFAULT_NAME) == null) {
                // - the same JRE as in jreHome()
                assert jreHome.equals(PyramidApiTools.currentJREHome());
                // Currently running Java must exist always!
                throw new AssertionError("Invalid default JRE (specified by \"java.home\" "
                    + "system property): the path \"" + jreHome
                    + "\" does not contain correct Java Runtime Envitonment");
            } else {
                if (jreName != null) {
                    e = new IllegalJREException("Illegal JRE \"" + jreName + "\": " + e.getMessage());
                }
                throw e;
            }
        }
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public String toString() {
        return jsonToPrettyString(toJson());
    }

    public static HttpServerConfiguration readFromFile(Path serverConfigurationFile)
        throws IOException
    {
        Objects.requireNonNull(serverConfigurationFile, "Null serverConfigurationFile");
        return new HttpServerConfiguration(
            serverConfigurationFile,
            readJson(serverConfigurationFile));
    }

    JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("configRootDir", configRootDir);
        builder.add("configFileName", configFileName);
        builder.add("imagesRootDir", imagesRootDir);
        final JsonArrayBuilder jreBuilder = Json.createArrayBuilder();
        for (JRE jre : this.jreMap.values()) {
            jreBuilder.add(jre.toJson());
        }
        builder.add("jre", jreBuilder.build());
        builder.add("proxy", proxy);
        if (proxySettings != null) {
            builder.add("proxySettings", proxySettings.toJson());
        }
        if (sslSettings != null) {
            builder.add("sslSettings", sslSettings.toJson());
        }
        return builder.build();
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
}

