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

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration of the pyramid proxy server and other features that can very for specific servers.
 * Customized by system administrator for each server.
 */
public class HttpPyramidSpecificServerConfiguration extends ConvertibleToJson {
    public static class SSLSettings extends ConvertibleToJson {
        private final String keystoreFile;
        private final String keystorePassword;
        private final String keyPassword;

        private SSLSettings(JsonObject json) {
            this.keystoreFile = HttpPyramidConfiguration.getRequiredString(json, "keystoreFile");
            this.keystorePassword = HttpPyramidConfiguration.getRequiredString(json, "keystorePassword");
            this.keyPassword = json.getString("keyPassword", keystorePassword);
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

        public String toJsonString() {
            return toJson().toString();
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
        private final int proxyPort;
        private final String pyramidHost;
        private final String defaultHost;
        private final int defaultPort;
        private final boolean ssl;

        private ProxySettings(JsonObject json) {
            Objects.requireNonNull(json);
            this.proxyPort = HttpPyramidConfiguration.getRequiredInt(json, "proxyPort");
            if (proxyPort <= 0 || proxyPort > HttpPyramidConstants.MAX_ALLOWED_PORT) {
                throw new JsonException("Invalid proxy configuration JSON:"
                    + " invalid proxy port number " + proxyPort
                    + " (must be in range 1.." + HttpPyramidConstants.MAX_ALLOWED_PORT + ")");
            }
            this.pyramidHost = json.getString("pyramidHost", "localhost");
            this.defaultHost = json.getString("defaultHost", "localhost");
            this.defaultPort = json.getInt("defaultPort", 80);
            if (defaultPort <= 0) {
                throw new JsonException("Invalid proxy configuration JSON:"
                    + " zero or negative default server port number " + defaultPort);
            }
            this.ssl = json.getBoolean("ssl", false);
        }

        public int getProxyPort() {
            return proxyPort;
        }

        public String getPyramidHost() {
            return pyramidHost;
        }

        public String getDefaultHost() {
            return defaultHost;
        }

        public int getDefaultPort() {
            return defaultPort;
        }

        public boolean isSsl() {
            return ssl;
        }

        public String toJsonString() {
            return toJson().toString();
        }

        public String toString() {
            return HttpPyramidConfiguration.jsonToPrettyString(toJson());
        }

        JsonObject toJson() {
            final JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("proxyPort", proxyPort);
            builder.add("pyramidHost", pyramidHost);
            builder.add("defaultHost", defaultHost);
            builder.add("defaultPort", defaultPort);
            builder.add("ssl", ssl);
            return builder.build();
        }
    }

    private final boolean proxy;
    private final ProxySettings proxySettings;
    private final SSLSettings sslSettings;
    private final Path specificServerConfigurationFile;

    private HttpPyramidSpecificServerConfiguration(Path specificServerConfigurationFile, JsonObject json) {
        Objects.requireNonNull(specificServerConfigurationFile);
        Objects.requireNonNull(json);
        this.proxy = json.getBoolean("proxy", false);
        final JsonObject proxySettingsJson = json.getJsonObject("proxySettings");
        if (proxy && proxySettingsJson == null) {
            throw new JsonException("Invalid configuration JSON: "
                + "\"proxySettings\" value required when \"proxy\" mode is used");
        }
        this.proxySettings = proxySettingsJson == null ? null : new ProxySettings(proxySettingsJson);
        final JsonObject sslSettingsJson = json.getJsonObject("sslSettings");
        if (proxySettings != null && proxySettings.ssl && sslSettingsJson == null) {
            throw new JsonException("Invalid configuration JSON: "
                + "\"sslSettings\" value required when \"proxySettings\".\"ssl\" mode is used");
        }
        this.sslSettings = sslSettingsJson == null ? null : new SSLSettings(sslSettingsJson);
        this.specificServerConfigurationFile = specificServerConfigurationFile;
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

