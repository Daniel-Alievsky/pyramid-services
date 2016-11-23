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

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidData;
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.api.http.HttpPyramidApiTools;
import net.algart.pyramid.api.http.HttpPyramidConstants;
import net.algart.pyramid.http.server.HttpPyramidCommand;
import net.algart.pyramid.http.server.HttpPyramidService;
import net.algart.pyramid.requests.PlanePyramidReadImageRequest;
import net.algart.pyramid.requests.PlanePyramidRequest;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import java.io.IOException;

public class ZoomifyHttpPyramidCommand extends HttpPyramidCommand {
    private static final int DEFAULT_ZOOMIFY_TILE_DIM = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.zoomifyTileDim", 256));

    private volatile int tileDim = DEFAULT_ZOOMIFY_TILE_DIM;

    public ZoomifyHttpPyramidCommand(HttpPyramidService httpPyramidService) {
        super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.ZOOMIFY);
    }

    @Override
    protected void service(Request request, Response response) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        final String[] components = path.split("/");
        if (components.length != 4) {
            throw new IllegalArgumentException("Zoomify path must contain 4 parts separated by /: "
                + "http://some-server.com:NNNN/some-prefix/"
                + HttpPyramidApiTools.appendPyramidIdForURLPath("(pyramidId)")
                + "/tileGroupXXX/z-x-y.jpg; "
                + "but actual path consists of " + components.length + " parts: http://some-server.com:NNNN/" + path);
        }
        final String pyramidId = HttpPyramidApiTools.removePrefixBeforePyramidIdFromURLPath(components[1]);
        final String[] zxy = components[3].split("-");
        final int z = Integer.parseInt(zxy[0]);
        final int x = Integer.parseInt(zxy[1]);
        final int y = Integer.parseInt(TmsHttpPyramidCommand.removeExtension(zxy[2]));
        final String configuration = pyramidIdToConfiguration(pyramidId);
//        System.out.println("tms-Configuration: " + configuration);
        response.addHeader("Access-Control-Allow-Origin", "*");
        // - Allows using AJAX-based drawing on JavaScript canvas (required by many new libraries).
        // It does not violate security, because other client can access this information in any case,
        // and Web pages cannot abuse it: it is not more dangerous than simple ability to read images.
        final PlanePyramidRequest pyramidRequest = new ZoomifyPlanePyramidRequest(configuration, x, y, z);
        httpPyramidService.createReadTask(request, response, pyramidRequest);
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

    private class ZoomifyPlanePyramidRequest extends PlanePyramidRequest {
        private final int x;
        private final int y;
        private final int z;

        ZoomifyPlanePyramidRequest(String pyramidUniqueId, int x, int y, int z) {
            super(pyramidUniqueId);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public PlanePyramidData readData(PlanePyramid pyramid) throws IOException {
            final PlanePyramidInformation info = pyramid.readInformation();
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
            final long zltDim = Math.round(tileDim * compression);
            final long zeroLevelFromX = x * zltDim;
            final long zeroLevelFromY = y * zltDim;
            final long zeroLevelToX = Math.min(zeroLevelFromX + zltDim, zeroLevelDimX);
            final long zeroLevelToY = Math.min(zeroLevelFromY + zltDim, zeroLevelDimY);
            return pyramid.readImage(new PlanePyramidReadImageRequest(
                getPyramidUniqueId(), compression, zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY));
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": "
                + "x=" + x + ", y=" + y + ", z=" + z;
        }

        @Override
        protected boolean equalsIgnoringPyramidUniqueId(PlanePyramidRequest o) {
            ZoomifyPlanePyramidRequest that = (ZoomifyPlanePyramidRequest) o;
            if (x != that.x) {
                return false;
            }
            if (y != that.y) {
                return false;
            }
            if (z != that.z) {
                return false;
            }
            return tileDim() == that.tileDim();
        }

        @Override
        protected int hashCodeIgnoringPyramidUniqueId() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + tileDim();
            return result;
        }

        private int tileDim() {
            return tileDim;
        }
    }
}
