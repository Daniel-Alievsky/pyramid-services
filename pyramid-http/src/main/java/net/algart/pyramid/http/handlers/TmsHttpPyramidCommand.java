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
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.http.HttpPyramidCommand;
import net.algart.pyramid.http.HttpPyramidService;
import net.algart.pyramid.requests.PlanePyramidImageRequest;
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
        super(httpPyramidService);
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
        if (components.length != 5) {
            throw new IllegalArgumentException("TMS path must contain 5 parts separated by /: "
                + "http://some-server.com:NNNN/some-prefix/pyramidId/z/x/y.jpg; "
                + "but actual path consists of " + components.length + " parts: http://some-server.com:NNNN/" + path);
        }
        // path[0] is the command prefix "pp-tms"
        final String pyramidId = components[1];
        final int z = Integer.parseInt(components[2]);
        final int x = Integer.parseInt(components[3]);
        final int y = Integer.parseInt(removeExtension(components[4]));
        final String configuration = pyramidIdToConfiguration(pyramidId);
//        System.out.println("tms-Configuration: " + configuration);
        final PlanePyramid pyramid = httpPyramidService.getPyramidPool().getHttpPlanePyramid(configuration);
        final PlanePyramidRequest pyramidRequest =
            tmsToImageRequest(x, y, z, configuration, pyramid.readInformation());
        httpPyramidService.createReadImageTask(request, response, pyramid, pyramidRequest);
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

    private PlanePyramidRequest tmsToImageRequest(
        int x,
        int y,
        int z,
        String pyramidConfiguration,
        PlanePyramidInformation info)
    {
        int maxZ = 0;
        for (long dim = Math.max(info.getZeroLevelDimX(), info.getZeroLevelDimY()); dim > tileDim; dim /= 2) {
            maxZ++;
        }
        double compression = 1;
        for (int i = maxZ; i > z; i--) {
            compression *= 2;
        }
        final long tileDim = Math.round(this.tileDim * compression);
        final long zeroLevelFromX = x * tileDim;
        final long zeroLevelFromY = inverseYDirection ? info.getZeroLevelDimY() - (y + 1) * tileDim : y * tileDim;
        final long zeroLevelToX = zeroLevelFromX + tileDim;
        final long zeroLevelToY = zeroLevelFromY + tileDim;
        return new PlanePyramidImageRequest(
            pyramidConfiguration, compression, zeroLevelFromX, zeroLevelFromY, zeroLevelToX, zeroLevelToY);
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
}

