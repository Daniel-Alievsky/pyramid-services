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

package net.algart.pyramid.http.handlers;

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.http.HttpPyramidCommand;
import net.algart.pyramid.http.HttpPyramidService;
import net.algart.pyramid.requests.PlanePyramidRequest;
import net.algart.pyramid.requests.PlanePyramidSpecialImageRequest;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;

public class ReadSpecialImagePyramidCommand extends HttpPyramidCommand {
    public ReadSpecialImagePyramidCommand(HttpPyramidService httpPyramidService) {
        super(httpPyramidService);
    }

    @Override
    public void service(
        Request request,
        Response response)
        throws Exception
    {
        final String configuration = pyramidIdToConfiguration(getParameter(request, "pyramidId"));
        final String specialImageName = getParameter(request, "specialImageName");
        final Integer width = request.getParameter("width") == null ? null : getIntParameter(request, "width");
        final Integer height = request.getParameter("height") == null ? null : getIntParameter(request, "height");
        final PlanePyramid pyramid = httpPyramidService.getPyramidPool().getHttpPlanePyramid(configuration);
        final PlanePyramidRequest imageRequest = new PlanePyramidSpecialImageRequest(
            configuration, specialImageName, width, height);
        httpPyramidService.createReadImageTask(request, response, pyramid, imageRequest);
    }

    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
        return httpPyramidService.pyramidIdToConfiguration(pyramidId);
    }
}
