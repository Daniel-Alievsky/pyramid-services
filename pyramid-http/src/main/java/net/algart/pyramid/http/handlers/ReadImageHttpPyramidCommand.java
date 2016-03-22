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
 *
 */

package net.algart.pyramid.http.handlers;

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidImageRequest;
import net.algart.pyramid.http.HttpPyramidCommand;
import net.algart.pyramid.http.HttpPyramidService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;
import java.util.Objects;

import static net.algart.pyramid.http.HttpPyramidCommand.*;

public class ReadImageHttpPyramidCommand implements HttpPyramidCommand {
    private HttpPyramidService httpPyramidService;

    public ReadImageHttpPyramidCommand(HttpPyramidService httpPyramidService) {
        this.httpPyramidService = Objects.requireNonNull(httpPyramidService);
    }

    @Override
    public void service(
        Request request,
        Response response)
        throws Exception
    {
        final String configJson = pyramidIdToConfig(getParameter(request, "pyramidId"));
        final double compression = getDoubleParameter(request, "compression");
        final long fromX = getLongParameter(request, "fromX");
        final long fromY = getLongParameter(request, "fromY");
        final long toX = getLongParameter(request, "toX");
        final long toY = getLongParameter(request, "toY");
        final PlanePyramid pyramid = httpPyramidService.getPyramidPool().getHttpPlanePyramid(configJson);
        final PlanePyramidImageRequest imageRequest = new PlanePyramidImageRequest(
            pyramid.uniqueId(), compression, fromX, fromY, toX, toY);
        httpPyramidService.createReadImageTask(request, response, pyramid, imageRequest);
    }

    protected String pyramidIdToConfig(String pyramidId) throws IOException {
        return httpPyramidService.pyramidIdToConfig(pyramidId);
    }
}
