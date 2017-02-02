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

package net.algart.pyramid.standard;

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidFactory;
import net.algart.pyramid.api.common.PyramidApiTools;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static net.algart.pyramid.api.common.PyramidConstants.FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE;

public class StandardPlanePyramidFactory implements PlanePyramidFactory {
    private volatile PlanePyramidSourceFactory planePyramidSourceFactory;

    @Override
    public void initializeConfiguration(Object factoryConfiguration) throws Exception {
        Objects.requireNonNull(factoryConfiguration, "Null plane pyramid source sub-factory (configuration argument");
        if (!(factoryConfiguration instanceof String)) {
            throw new IllegalArgumentException("Invalid type of configuration argument: it must be a String "
                + "(the name of PlanePyramidSourceFactory class)");
        }
        this.planePyramidSourceFactory = (PlanePyramidSourceFactory)
            Class.forName((String) factoryConfiguration).newInstance();
    }

    @Override
    public PlanePyramid newPyramid(final String pyramidConfiguration) throws Exception {
        Objects.requireNonNull(pyramidConfiguration);
        final JsonObject config = PyramidApiTools.configurationToJson(pyramidConfiguration);
        final Path pyramidDir = PyramidApiTools.getPyramidPath(config);
        final JsonObject pyramidJson = PyramidApiTools.readPyramidConfiguration(pyramidDir);
        final String fileName = pyramidJson.getString(FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE, null);
        if (fileName == null) {
            throw new IOException("Invalid pyramid configuration json: no \""
                + FILE_NAME_IN_PYRAMID_DATA_CONFIG_FILE + "\" attribute <<<" + pyramidJson + ">>>");
        }
        final Path pyramidFile = pyramidDir.resolve(fileName);
        if (!Files.exists(pyramidFile)) {
            throw new IOException("Pyramid file at " + pyramidFile + " does not exist");
        }
        JsonObject rendererJson = config.getJsonObject(PlanePyramid.RENDERER_KEY);
        if (rendererJson == null) {
            rendererJson = Json.createObjectBuilder().build();
        }
        final boolean rawBytes = config.getBoolean("rawBytes", false);
        final boolean cacheable = config.getBoolean("cacheable", true);
        final PlanePyramidSource planePyramidSource = planePyramidSourceFactory.newPlanePyramidSource(
            pyramidFile.toAbsolutePath().toString(),
            pyramidJson.toString(),
            rendererJson.toString());
        return new StandardPlanePyramid(
            planePyramidSource, pyramidJson, rendererJson, rawBytes, cacheable, pyramidConfiguration);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " based on " + planePyramidSourceFactory.getClass().getName();
    }
}
