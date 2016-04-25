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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.Objects;

public final class PlanePyramidInformation {
    private final int channelCount;
    private final long zeroLevelDimX;
    private final long zeroLevelDimY;
    private final Class<?> elementType;
    private volatile Double pixelSizeInMicrons;
    private volatile Double magnification;
    private volatile String additionalMetadata;
    // - usually JSON

    public PlanePyramidInformation(
        int channelCount,
        long zeroLevelDimX,
        long zeroLevelDimY,
        Class<?> elementType)
    {
        Objects.requireNonNull(elementType, "Null elementType");
        if (channelCount <= 0) {
            throw new IllegalArgumentException("Zero or negative bandCount = " + channelCount);
        }
        if (zeroLevelDimX < 0) {
            throw new IllegalArgumentException("Negative zeroLevelDimX = " + zeroLevelDimX);
        }
        if (zeroLevelDimY < 0) {
            throw new IllegalArgumentException("Negative zeroLevelDimY = " + zeroLevelDimY);
        }
        this.channelCount = channelCount;
        this.zeroLevelDimX = zeroLevelDimX;
        this.zeroLevelDimY = zeroLevelDimY;
        this.elementType = elementType;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public long getZeroLevelDimX() {
        return zeroLevelDimX;
    }

    public long getZeroLevelDimY() {
        return zeroLevelDimY;
    }

    public Class<?> getElementType() {
        return elementType;
    }

    public Double getPixelSizeInMicrons() {
        return pixelSizeInMicrons;
    }

    public void setPixelSizeInMicrons(Double pixelSizeInMicrons) {
        if (pixelSizeInMicrons != null && pixelSizeInMicrons <= 0.0) {
            throw new IllegalArgumentException("Zero or negative pixelSizeInMicrons");
        }
        this.pixelSizeInMicrons = pixelSizeInMicrons;
    }

    public Double getMagnification() {
        return magnification;
    }

    public void setMagnification(Double magnification) {
        if (magnification != null && magnification <= 0.0) {
            throw new IllegalArgumentException("Zero or negative magnification");
        }
        this.magnification = magnification;
    }

    /**
     * Returns additional metadata, usually JSON.
     *
     * @return additional metadata, usually JSON. May be <tt>null</tt>.
     */
    public String getAdditionalMetadata() {
        return additionalMetadata;
    }

    public void setAdditionalMetadata(String additionalMetadata) {
        this.additionalMetadata = additionalMetadata;
    }

    public JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("channelCount", channelCount);
        builder.add("zeroLevelDimX", zeroLevelDimX);
        builder.add("zeroLevelDimY", zeroLevelDimY);
        builder.add("elementType", elementType.toString());
        if (pixelSizeInMicrons != null) {
            builder.add("pixelSizeInMicrons", pixelSizeInMicrons);
        }
        if (magnification != null) {
            builder.add("magnification", magnification);
        }
        if (additionalMetadata != null) {
            builder.add("additionalMetadata", additionalMetadata);
        }
        return builder.build();
    }
}
