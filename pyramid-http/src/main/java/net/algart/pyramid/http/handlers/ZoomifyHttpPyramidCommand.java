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
import net.algart.pyramid.PlanePyramidImageRequest;
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.http.HttpPyramidCommand;
import net.algart.pyramid.http.HttpPyramidService;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;
import java.util.Objects;

public class ZoomifyHttpPyramidCommand implements HttpPyramidCommand {
    private static final int DEFAULT_ZOOMIFY_TILE_DIM = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.zoomifyTileDim", 256));

    private final HttpPyramidService httpPyramidService;
    private volatile int tileDim = DEFAULT_ZOOMIFY_TILE_DIM;

    public ZoomifyHttpPyramidCommand(HttpPyramidService httpPyramidService) {
        this.httpPyramidService = Objects.requireNonNull(httpPyramidService);
    }

    @Override
    public void service(
        Request request,
        Response response)
        throws Exception
    {
        String path = request.getRequestURI();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        final String[] components = path.split("/");
        if (components.length != 4) {
            throw new IllegalArgumentException("Zoomify path must contain 4 parts separated by /: "
                + "http://some-server.com:NNNN/some-prefix/pyramidId/tileGroupXXX/z-x-y.jpg; "
                + "but actual path consists of " + components.length + " parts: http://some-server.com:NNNN/" + path);
        }
        // path[0] is the command prefix "pp-tms"
        final String pyramidId = components[1];
        final String[] zxy = components[3].split("-");
        final int z = Integer.parseInt(zxy[0]);
        final int x = Integer.parseInt(zxy[1]);
        final int y = Integer.parseInt(TmsHttpPyramidCommand.removeExtension(zxy[2]));
        final String configuration = pyramidIdToConfiguration(pyramidId);
//        System.out.println("tms-Configuration: " + configuration);
        final PlanePyramid pyramid = httpPyramidService.getPyramidPool().getHttpPlanePyramid(configuration);
        final PlanePyramidImageRequest imageRequest =
            zoomifyToImageRequest(x, y, z, configuration, pyramid.information());
        httpPyramidService.createReadImageTask(request, response, pyramid, imageRequest);
    }

    @Override
    public boolean isSubFoldersAllowed() {
        return true;
    }

    public int getTileDim() {
        return tileDim;
    }

    public ZoomifyHttpPyramidCommand setTileDim(int tileDim) {
        if (tileDim < 16) {
            throw new IllegalArgumentException("tileDim=" + tileDim + ", but it must be >=16");
        }
        if ((tileDim & (tileDim - 1)) != 0) {
            throw new IllegalArgumentException("tileDim=" + tileDim + ", but it must be a power of 2");
        }
        this.tileDim = tileDim;
        return this;
    }

    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
        return httpPyramidService.pyramidIdToConfiguration(pyramidId);
    }

    private PlanePyramidImageRequest zoomifyToImageRequest(
        int x,
        int y,
        int z,
        String pyramidConfiguration,
        PlanePyramidInformation info)
    {
        int maxZ = 0;
        final long zeroLevelDimX = info.getZeroLevelDimX();
        final long zeroLevelDimY = info.getZeroLevelDimY();
        for (long dim = Math.max(zeroLevelDimX, zeroLevelDimY); dim > tileDim; dim /= 2) {
            maxZ++;
        }
        double compression = 1;
        for (int i = maxZ; i > z; i--) {
            compression *= 2;
        }
        final long tileDim = Math.round(this.tileDim * compression);
        final long zeroLevelFromX = x * tileDim;
        final long zeroLevelFromY = y * tileDim;
        final long zeroLevelToX = Math.min(zeroLevelFromX + tileDim, zeroLevelDimX);
        final long zeroLevelToY = Math.min(zeroLevelFromY + tileDim, zeroLevelDimY);
        return new PlanePyramidImageRequest(
            pyramidConfiguration, compression, zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY);
    }
}
