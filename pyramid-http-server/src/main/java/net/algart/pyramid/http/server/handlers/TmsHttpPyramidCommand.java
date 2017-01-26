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

public class TmsHttpPyramidCommand extends HttpPyramidCommand {
    private static final int DEFAULT_TMS_TILE_DIM = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.tmsTileDim", 256));
    private static final boolean DEFAULT_INVERSE_Y_DIRECTION = getBooleanProperty(
        "net.algart.pyramid.http.tmsInverseYDirection", false);

    private volatile int tileDim = DEFAULT_TMS_TILE_DIM;
    private volatile boolean inverseYDirection = DEFAULT_INVERSE_Y_DIRECTION;

    public TmsHttpPyramidCommand(HttpPyramidService httpPyramidService) {
        super(httpPyramidService, HttpPyramidConstants.CommandPrefixes.TMS);
    }

    @Override
    protected void service(Request request, Response response) throws Exception {
        String path = request.getRequestURI();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        final String[] components = path.split("/");
        if (components.length != 5) {
            throw new IllegalArgumentException("TMS path must contain 5 parts separated by /: "
                + "http://some-server.com:NNNN/some-prefix/(pyramidId)/z/x/y.jpg; "
                + "but actual path consists of " + components.length + " parts: http://some-server.com:NNNN/" + path);
        }
        // path[0] is the command prefix "pp-tms"
        final String pyramidId = components[1];
        final int z = Integer.parseInt(components[2]);
        final int x = Integer.parseInt(components[3]);
        final int y = Integer.parseInt(removeExtension(components[4]));
        final String configuration = pyramidIdToConfiguration(pyramidId);
//        System.out.println("tms-Configuration: " + configuration);
        response.addHeader("Access-Control-Allow-Origin", "*");
        // - Allows using AJAX-based drawing on JavaScript canvas (required by many new libraries).
        // It does not violate security, because other client can access this information in any case,
        // and Web pages cannot abuse it: it is not more dangerous than simple ability to read images.
        final PlanePyramidRequest pyramidRequest = new TmsPlanePyramidRequest(configuration, x, y, z);
        httpPyramidService.createReadTask(request, response, pyramidRequest);
    }

    @Override
    public boolean isSubFoldersAllowed() {
        return true;
    }

    public int getTileDim() {
        return tileDim;
    }

    public TmsHttpPyramidCommand setTileDim(int tileDim) {
        if (tileDim < 16) {
            throw new IllegalArgumentException("tileDim=" + tileDim + ", but it must be >=16");
        }
        if ((tileDim & (tileDim - 1)) != 0) {
            throw new IllegalArgumentException("tileDim=" + tileDim + ", but it must be a power of 2");
        }
        this.tileDim = tileDim;
        return this;
    }

    public boolean isInverseYDirection() {
        return inverseYDirection;
    }

    public TmsHttpPyramidCommand setInverseYDirection(boolean inverseYDirection) {
        this.inverseYDirection = inverseYDirection;
        return this;
    }

    protected String pyramidIdToConfiguration(String pyramidId) throws IOException {
        return httpPyramidService.pyramidIdToConfiguration(pyramidId);
    }

    static String removeExtension(String fileName) {
        int p = fileName.lastIndexOf('.');
        if (p == -1) {
            return fileName;
        }
        return fileName.substring(0, p);
    }

    static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        if (defaultValue) {
            return !"false".equalsIgnoreCase(System.getProperty(propertyName));
        } else {
            return Boolean.getBoolean(propertyName);
        }
    }

    private class TmsPlanePyramidRequest extends PlanePyramidRequest {
        private final int x;
        private final int y;
        private final int z;

        TmsPlanePyramidRequest(String pyramidUniqueId, int x, int y, int z) {
            super(pyramidUniqueId);
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": "
                + "x=" + x + ", y=" + y + ", z=" + z;
        }

        @Override
        public PlanePyramidData readData(PlanePyramid pyramid) throws IOException {
            final PlanePyramidInformation info = pyramid.readInformation();
            int maxZ = 0;
            for (long dim = Math.max(info.getZeroLevelDimX(), info.getZeroLevelDimY()); dim > tileDim; dim /= 2) {
                maxZ++;
            }
            double compression = 1;
            for (int i = maxZ; i > z; i--) {
                compression *= 2;
            }
            final long zltDim = Math.round(tileDim * compression);
            final long zeroLevelFromX = x * zltDim;
            final long zeroLevelFromY = inverseYDirection ? info.getZeroLevelDimY() - (y + 1) * zltDim : y * zltDim;
            final long zeroLevelToX = zeroLevelFromX + zltDim;
            final long zeroLevelToY = zeroLevelFromY + zltDim;
            return pyramid.readImage(new PlanePyramidReadImageRequest(
                getPyramidUniqueId(), compression, zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY));
        }

        @Override
        protected boolean equalsIgnoringPyramidUniqueId(PlanePyramidRequest o) {
            TmsPlanePyramidRequest that = (TmsPlanePyramidRequest) o;
            if (x != that.x) {
                return false;
            }
            if (y != that.y) {
                return false;
            }
            if (z != that.z) {
                return false;
            }
            return tileDim() == that.tileDim() && inverseYDirection() == that.inverseYDirection();
        }

        @Override
        protected int hashCodeIgnoringPyramidUniqueId() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + tileDim();
            result = 31 * result + (inverseYDirection() ? 1 : 0);
            return result;
        }

        private int tileDim() {
            return tileDim;
        }

        private boolean inverseYDirection() {
            return inverseYDirection;
        }
    }
}

