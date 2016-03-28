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

package net.algart.pyramid.standard;

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidFactory;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class StandardPlanePyramidFactory implements PlanePyramidFactory {
    private volatile PlanePyramidSourceFactory planePyramidSourceFactory;

    @Override
    public void initializeConfiguration(Object configuration) throws Exception {
        Objects.requireNonNull(configuration, "Null plane pyramid source sub-factory (configuration argument");
        if (!(configuration instanceof String)) {
            throw new IllegalArgumentException("Invalid type of configuration argument: it must be a String "
                + "(the name of PlanePyramidSourceFactory class)");
        }
        this.planePyramidSourceFactory = (PlanePyramidSourceFactory)
            Class.forName((String) configuration).newInstance();
    }

    @Override
    public PlanePyramid newPyramid(final String configJson) throws Exception {
        Objects.requireNonNull(configJson);
        final JsonObject config;
        try {
            config = Json.createReader(new StringReader(configJson)).readObject();
        } catch (JsonException e) {
            throw new IOException("Invalid configuration json: <<<" + configJson + ">>>", e);
        }
        final Path path = Paths.get(config.getString("pyramidPath"));
        final JsonObject pyramidJson;
        try (final JsonReader reader = Json.createReader(Files.newBufferedReader(
            path.resolve(".pp.json"), StandardCharsets.UTF_8)))
        {
            pyramidJson = reader.readObject();
        }
        final String fileName = pyramidJson.getString("fileName");
        final Path pyramidPath = path.resolve(fileName);
        if (!Files.exists(pyramidPath)) {
            throw new IOException("Pyramid file at " + pyramidPath + " does not exist");
        }
        JsonObject rendererJson = config.getJsonObject("renderer");
        if (rendererJson == null) {
            rendererJson = Json.createObjectBuilder().build();
        }
        final boolean rawBytes = config.getBoolean("rawBytes", false);
        final boolean cacheable = config.getBoolean("cacheable", true);
        final PlanePyramidSource planePyramidSource = planePyramidSourceFactory.newPlanePyramidSource(
            pyramidPath.toAbsolutePath().toString(),
            pyramidJson.toString(),
            rendererJson.toString());
        return new StandardPlanePyramid(planePyramidSource, rendererJson, rawBytes, cacheable, configJson);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " based on " + planePyramidSourceFactory.getClass().getName();
    }
}
