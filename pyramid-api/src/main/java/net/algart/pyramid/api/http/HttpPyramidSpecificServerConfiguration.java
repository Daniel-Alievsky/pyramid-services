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

import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.pyramid.api.common.PyramidConstants;

import javax.json.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration of the pyramid proxy server and other features that can very for specific servers.
 * Customized by system administrator for each server.
 */
public class HttpPyramidSpecificServerConfiguration extends ConvertibleToJson {
    public static class JRE extends ConvertibleToJson {
        public static String DEFAULT_NAME = "default";

        private final String name;
        private final String homePath;
        private final HttpPyramidSpecificServerConfiguration parentConfiguration;

        public JRE(HttpPyramidSpecificServerConfiguration parentConfiguration, JsonObject json) {
            this.name = HttpPyramidConfiguration.getRequiredString(json, "name");
            this.homePath = HttpPyramidConfiguration.getRequiredString(json, "homePath");
            this.parentConfiguration = parentConfiguration;
        }

        public String getName() {
            return name;
        }

        public String getHomePath() {
            return homePath;
        }

        public HttpPyramidSpecificServerConfiguration parentConfiguration() {
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
        private final HttpPyramidSpecificServerConfiguration parentConfiguration;

        private SSLSettings(HttpPyramidSpecificServerConfiguration parentConfiguration, JsonObject json) {
            this.keystoreFile = HttpPyramidConfiguration.getRequiredString(json, "keystoreFile");
            this.keystorePassword = HttpPyramidConfiguration.getRequiredString(json, "keystorePassword");
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
            return parentConfiguration.specificServerConfigurationFile
                .getParent().resolve(keystoreFile).toAbsolutePath();
        }

        public String toJsonString() {
            return toJson().toString();
        }

        public HttpPyramidSpecificServerConfiguration parentConfiguration() {
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
                    throw new JsonException("Invalid proxy configuration JSON:"
                        + " zero or negative default server port number " + port);
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
        private final HttpPyramidSpecificServerConfiguration parentConfiguration;

        private ProxySettings(HttpPyramidSpecificServerConfiguration parentConfiguration, JsonObject json) {
            Objects.requireNonNull(parentConfiguration);
            Objects.requireNonNull(json);
            this.proxyPort = HttpPyramidConfiguration.getRequiredInt(json, "proxyPort");
            if (proxyPort <= 0 || proxyPort > HttpPyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid proxy configuration JSON:"
                    + " invalid proxy port number " + proxyPort
                    + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
            }
            this.pyramidHost = json.getString("pyramidHost", "localhost");
            this.ssl = json.getBoolean("ssl", false);
            this.defaultServer = new DefaultServer(this, json.getJsonObject("defaultServer"));
            this.parentConfiguration = parentConfiguration;
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

        public String toJsonString() {
            return toJson().toString();
        }

        public HttpPyramidSpecificServerConfiguration parentConfiguration() {
            return parentConfiguration;
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("proxyPort", proxyPort);
            builder.add("pyramidHost", pyramidHost);
            builder.add("defaultServer", defaultServer.toJson());
            builder.add("ssl", ssl);
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
    private final Path specificServerConfigurationFile;

    private HttpPyramidSpecificServerConfiguration(Path specificServerConfigurationFile, JsonObject json) {
        Objects.requireNonNull(specificServerConfigurationFile);
        Objects.requireNonNull(json);
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
            throw new JsonException("Invalid configuration JSON: "
                + "\"proxySettings\" value required when \"proxy\" mode is used");
        }
        this.proxySettings = proxySettingsJson == null ? null : new ProxySettings(this, proxySettingsJson);
        final JsonObject sslSettingsJson = json.getJsonObject("sslSettings");
        if (proxySettings != null && proxySettings.ssl && sslSettingsJson == null) {
            throw new JsonException("Invalid configuration JSON: "
                + "\"sslSettings\" value required when \"proxySettings\".\"ssl\" mode is used");
        }
        this.sslSettings = sslSettingsJson == null ? null : new SSLSettings(this, sslSettingsJson);
        this.specificServerConfigurationFile = specificServerConfigurationFile;
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

    public Path getSpecificServerConfigurationFile() {
        return specificServerConfigurationFile;
    }

    public String jreHome() {
        final JRE jre = jreMap.get(JRE.DEFAULT_NAME);
        return jre != null ? jre.getHomePath() : PyramidApiTools.currentJREHome();
    }

    public String jreHome(String jreName) {
        if (jreName == null) {
            return jreHome();
        }
        final JRE jre = jreMap.get(jreName);
        return jre != null ? jre.getHomePath() : jreHome();
    }

    public Path javaExecutable() {
        return javaExecutable(null);
    }

    public Path javaExecutable(String jreName) {
        final String jreHome = jreHome(jreName);
        try {
            return PyramidApiTools.javaExecutable(jreHome);
        } catch (FileNotFoundException e) {
            // Currently running Java must exist always!
            throw new JsonException("Invalid "
                + (jreName == null ? "default JRE" : "JRE \"" + jreName + "\"")
                + ": the path \"" + jreHome + "\" does not contain correct Java Runtime Envitonment", e);
        }
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public String toString() {
        return HttpPyramidConfiguration.jsonToPrettyString(toJson());
    }

    public static HttpPyramidSpecificServerConfiguration readFromFile(Path specificServerConfigurationFile)
        throws IOException
    {
        Objects.requireNonNull(specificServerConfigurationFile, "Null specificServerConfigurationFile");
        return new HttpPyramidSpecificServerConfiguration(
            specificServerConfigurationFile,
            HttpPyramidConfiguration.readJson(specificServerConfigurationFile));
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
}

