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
import net.algart.pyramid.api.common.PyramidConstants;
import net.algart.pyramid.api.common.PyramidFormat;
import net.algart.simagis.pyramid.PlanePyramidSourceFactory;

import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import java.util.List;
import java.util.Objects;

public class StandardPlanePyramidFactory implements PlanePyramidFactory {
    private volatile PyramidFormat pyramidFormat = null;
    private volatile PlanePyramidSourceFactory planePyramidSourceFactory = null;

    @Override
    public void initializeConfiguration(Object factoryConfiguration) throws Exception {
        Objects.requireNonNull(factoryConfiguration,
            "Null plane pyramid factory configuration argument");
        if (!(factoryConfiguration instanceof JsonObject)) {
            throw new IllegalArgumentException("Invalid type of configuration argument: it must be JsonObject "
                + "(containing name of PlanePyramidSourceFactory class, format name and list of file extensions)");
        }
        final JsonObject configuration = (JsonObject) factoryConfiguration;
        this.pyramidFormat = PyramidFormat.getInstance(configuration);
        final String planePyramidSubFactory = getRequiredString(configuration,
            PyramidConstants.PLANE_PYRAMID_SUB_FACTORY_IN_PYRAMID_FACTORY_CONFIGURATION_JSON);
        this.planePyramidSourceFactory = (PlanePyramidSourceFactory)
            Class.forName(planePyramidSubFactory).newInstance();
    }

    @Override
    public PlanePyramid newPyramid(final String pyramidConfiguration) throws Exception {
        return new StandardPlanePyramid(this, pyramidConfiguration);
    }

    public PyramidFormat getPyramidFormat() {
        return pyramidFormat;
    }

    public PlanePyramidSourceFactory getPlanePyramidSourceFactory() {
        return planePyramidSourceFactory;
    }

    @Override
    public List<String> accompanyingResources(String pyramidDataPath) {
        if (planePyramidSourceFactory == null) {
            throw new IllegalStateException("Factory is not initialized yet");
        }
        return planePyramidSourceFactory.accompanyingResources(pyramidDataPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " based on " + planePyramidSourceFactory.getClass().getName();
    }

    private static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid factory configuration JSON: \"" + name
                + "\" value required <<<" + json + ">>>");
        }
        return result.getString();
    }
}
