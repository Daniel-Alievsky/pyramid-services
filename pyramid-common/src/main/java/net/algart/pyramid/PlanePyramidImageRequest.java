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
    private final long fromX;
    private final long fromY;
    private final long toX;
    private final long toY;

    public PlanePyramidImageRequest(
        String pyramidUniqueId,
        double compression,
        long fromX,
        long fromY,
        long toX,
        long toY)
    {
        this.pyramidUniqueId = Objects.requireNonNull(pyramidUniqueId);
        if (compression <= 0.0) {
            throw new IllegalArgumentException("Zero or negative compression = " + compression);
        }
        if (toX < fromX) {
            throw new IllegalArgumentException("Illegal fromX/toX = " + fromX + "/" + toX + ": toX < fromX");
        }
        if (toY < fromY) {
            throw new IllegalArgumentException("Illegal fromY/toY = " + fromY + "/" + toY + ": toY < fromY");
        }
        this.compression = compression;
        this.fromX = fromX;
        this.fromY = fromY;
        this.toX = toX;
        this.toY = toY;
    }

    public String getPyramidUniqueId() {
        return pyramidUniqueId;
    }

    public double getCompression() {
        return compression;
    }

    public long getFromX() {
        return fromX;
    }

    public long getFromY() {
        return fromY;
    }

    public long getToX() {
        return toX;
    }

    public long getToY() {
        return toY;
    }

    @Override
    public String toString() {
        return "PlanePyramidImageRequest{" +
            "compression=" + compression +
            ", fromX=" + fromX +
            ", fromY=" + fromY +
            ", toX=" + toX +
            ", toY=" + toY +
            '}';
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
        if (fromX != that.fromX) {
            return false;
        }
        if (fromY != that.fromY) {
            return false;
        }
        if (toX != that.toX) {
            return false;
        }
        if (toY != that.toY) {
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
        result = 31 * result + (int) (fromX ^ (fromX >>> 32));
        result = 31 * result + (int) (fromY ^ (fromY >>> 32));
        result = 31 * result + (int) (toX ^ (toX >>> 32));
        result = 31 * result + (int) (toY ^ (toY >>> 32));
        return result;
    }
}
