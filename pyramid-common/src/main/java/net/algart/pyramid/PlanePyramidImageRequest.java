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

package net.algart.pyramid;

import java.util.Objects;

public final class PlanePyramidImageRequest {
    private final String pyramidUniqueId;
    private final double compression;
    private final long zeroLevelFromX;
    private final long zeroLevelFromY;
    private final long zeroLevelToX;
    private final long zeroLevelToY;

    public PlanePyramidImageRequest(
        String pyramidUniqueId,
        double compression,
        long zeroLevelFromX,
        long zeroLevelFromY,
        long zeroLevelToX,
        long zeroLevelToY)
    {
        this.pyramidUniqueId = Objects.requireNonNull(pyramidUniqueId);
        if (compression <= 0.0) {
            throw new IllegalArgumentException("Zero or negative compression = " + compression);
        }
        if (zeroLevelToX < zeroLevelFromX) {
            throw new IllegalArgumentException("Illegal zeroLevelFromX/zeroLevelToX = "
                + zeroLevelFromX + "/" + zeroLevelToX + ": zeroLevelToX < zeroLevelFromX");
        }
        if (zeroLevelToY < zeroLevelFromY) {
            throw new IllegalArgumentException("Illegal zeroLevelFromY/zeroLevelToY = "
                + zeroLevelFromY + "/" + zeroLevelToY + ": zeroLevelToY < zeroLevelFromY");
        }
        this.compression = compression;
        this.zeroLevelFromX = zeroLevelFromX;
        this.zeroLevelFromY = zeroLevelFromY;
        this.zeroLevelToX = zeroLevelToX;
        this.zeroLevelToY = zeroLevelToY;
    }

    public String getPyramidUniqueId() {
        return pyramidUniqueId;
    }

    public double getCompression() {
        return compression;
    }

    public long getZeroLevelFromX() {
        return zeroLevelFromX;
    }

    public long getZeroLevelFromY() {
        return zeroLevelFromY;
    }

    public long getZeroLevelToX() {
        return zeroLevelToX;
    }

    public long getZeroLevelToY() {
        return zeroLevelToY;
    }

    @Override
    public String toString() {
        return "PlanePyramidImageRequest: " +
            "compression " + compression +
            ", zero ךevel " + zeroLevelFromX + ".." + zeroLevelToX + "x" + zeroLevelFromY + ".." + zeroLevelToY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlanePyramidImageRequest)) {
            return false;
        }

        PlanePyramidImageRequest that = (PlanePyramidImageRequest) o;

        if (Double.compare(that.compression, compression) != 0) {
            return false;
        }
        if (zeroLevelFromX != that.zeroLevelFromX) {
            return false;
        }
        if (zeroLevelFromY != that.zeroLevelFromY) {
            return false;
        }
        if (zeroLevelToX != that.zeroLevelToX) {
            return false;
        }
        if (zeroLevelToY != that.zeroLevelToY) {
            return false;
        }
        return pyramidUniqueId.equals(that.pyramidUniqueId);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = pyramidUniqueId.hashCode();
        temp = Double.doubleToLongBits(compression);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (zeroLevelFromX ^ (zeroLevelFromX >>> 32));
        result = 31 * result + (int) (zeroLevelFromY ^ (zeroLevelFromY >>> 32));
        result = 31 * result + (int) (zeroLevelToX ^ (zeroLevelToX >>> 32));
        result = 31 * result + (int) (zeroLevelToY ^ (zeroLevelToY >>> 32));
        return result;
    }
}
