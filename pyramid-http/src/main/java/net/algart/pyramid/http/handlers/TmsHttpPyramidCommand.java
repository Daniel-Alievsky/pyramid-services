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

public class TmsHttpPyramidCommand implements HttpPyramidCommand {
    private static final int DEFAULT_TMS_TILE_DIM = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.tmsTileDim", 256));
    private static final boolean DEFAULT_INVERSE_Y_DIRECTION = getBooleanProperty(
        "net.algart.pyramid.http.tmsInverseYDirection", false);

    private final HttpPyramidService httpPyramidService;
    private volatile int tmsTileDim = DEFAULT_TMS_TILE_DIM;
    private volatile boolean inverseYDirection = DEFAULT_INVERSE_Y_DIRECTION;

    public TmsHttpPyramidCommand(HttpPyramidService httpPyramidService) {
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
        final String[] tmsComponents = path.split("/");
        if (tmsComponents.length != 5) {
            throw new IllegalArgumentException("TMS path must contain 5 parts separated by / (\"" + path + "\")");
        }
        // path[0] is the command prefix "pp-tms"
        final String pyramidId = tmsComponents[1];
        final int z = Integer.parseInt(tmsComponents[2]);
        final int x = Integer.parseInt(tmsComponents[3]);
        final int y = Integer.parseInt(removeFileExtension(tmsComponents[4]));
        final String configuration = pyramidIdToConfiguration(pyramidId);
//        System.out.println("tms-Configuration: " + configuration);
        final PlanePyramid pyramid = httpPyramidService.getPyramidPool().getHttpPlanePyramid(configuration);
        final PlanePyramidImageRequest imageRequest = tmsToImageRequest(x, y, z, configuration, pyramid.information());
        httpPyramidService.createReadImageTask(request, response, pyramid, imageRequest);
    }

    @Override
    public boolean isSubFoldersAllowed() {
        return true;
    }

    public int getTmsTileDim() {
        return tmsTileDim;
    }

    public TmsHttpPyramidCommand setTmsTileDim(int tmsTileDim) {
        if (tmsTileDim < 16) {
            throw new IllegalArgumentException("tmsTileDim=" + tmsTileDim + ", but it must be >=16");
        }
        if ((tmsTileDim & (tmsTileDim - 1)) != 0) {
            throw new IllegalArgumentException("tmsTileDim=" + tmsTileDim + ", but it must be a power of 2");
        }
        this.tmsTileDim = tmsTileDim;
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

    private PlanePyramidImageRequest tmsToImageRequest(
        int x,
        int y,
        int z,
        String pyramidConfiguration,
        PlanePyramidInformation info)
    {
        int maxZ = 0;
        for (long dim = Math.max(info.getZeroLevelDimX(), info.getZeroLevelDimY()); dim > tmsTileDim; dim /= 2) {
            maxZ++;
        }
        double compression = 1;
        for (int i = maxZ; i > z; i--) {
            compression *= 2;
        }
        final long tileDim = Math.round(tmsTileDim * compression);
        final long fromX = x * tileDim;
        final long fromY = inverseYDirection ? info.getZeroLevelDimY() - (y + 1) * tileDim : y * tileDim;
        final long toX = fromX + tileDim;
        final long toY = fromY + tileDim;
        return new PlanePyramidImageRequest(pyramidConfiguration, compression, fromX, fromY, toX, toY);
    }

    private static String removeFileExtension(String fileName) {
        int p = fileName.lastIndexOf('.');
        if (p == -1) {
            return fileName;
        }
        return fileName.substring(0, p);
    }

    private static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
        if (defaultValue) {
            return !"false".equalsIgnoreCase(System.getProperty(propertyName));
        } else {
            return Boolean.getBoolean(propertyName);
        }
    }
}

