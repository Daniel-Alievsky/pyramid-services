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

package net.algart.pyramid.http.server.handlers;

import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.server.HttpPyramidCommand;
import net.algart.pyramid.http.server.HttpPyramidService;
import net.algart.pyramid.requests.PlanePyramidReadInformationRequest;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;

public class InformationHttpPyramidCommand extends HttpPyramidCommand {
    public InformationHttpPyramidCommand(HttpPyramidService httpPyramidService) {
        super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.INFORMATION);
    }

    @Override
    protected void service(Request request, Response response) throws Exception {
        final String configuration = pyramidIdToConfiguration(
            getParameter(request, HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME));
        response.setContentType("application/json; charset=utf-8");
        response.addHeader("Access-Control-Allow-Origin", "*");
        // - Allows browser JavaScript to access this via XMLHttpRequest.
        // It does not violate security, because other client can access this information in any case,
        // and Web pages cannot abuse it: it is not more dangerous than simple ability to read images.
        final PlanePyramidReadInformationRequest informationRequest =
            new PlanePyramidReadInformationRequest(configuration);
        httpPyramidService.createReadTask(request, response, informationRequest);
    }

    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
        return httpPyramidService.pyramidIdToConfiguration(pyramidId);
    }
}
