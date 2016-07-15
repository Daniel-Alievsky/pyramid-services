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

package net.algart.pyramid.requests;

import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidImageData;

import java.io.IOException;
import java.util.Objects;

public final class PlanePyramidReadSpecialImageRequest extends PlanePyramidAnyImageRequest {
    // See constants in PlanePyramidSource.SpecialImageKind:
    public static final String WHOLE_SLIDE = "WHOLE_SLIDE";
    public static final String MAP_IMAGE = "MAP_IMAGE";
    public static final String LABEL_ONLY_IMAGE = "LABEL_ONLY_IMAGE";
    public static final String THUMBNAIL_IMAGE = "THUMBNAIL_IMAGE";

    private final String specialImageName;
    private final Integer desiredWidth;
    private final Integer desiredHeight;
    private final boolean savingMemory;

    public PlanePyramidReadSpecialImageRequest(
        String pyramidUniqueId,
        String specialImageName,
        Integer desiredWidth,
        Integer desiredHeight,
        boolean savingMemory)
    {
        super(pyramidUniqueId);
        this.specialImageName = Objects.requireNonNull(specialImageName, "Null specialImageName");
        if (desiredWidth != null && desiredWidth <= 0) {
            throw new IllegalArgumentException("Zero or negative desiredWidth = " + desiredWidth);
        }
        if (desiredHeight != null && desiredHeight <= 0) {
            throw new IllegalArgumentException("Zero or negative desiredHeight = " + desiredHeight);
        }
        this.desiredWidth = desiredWidth;
        this.desiredHeight = desiredHeight;
        this.savingMemory = savingMemory;
    }

    @Override
    public int priority() {
        return READ_SPECIAL_IMAGE_PRIORITY;
    }

    @Override
    public PlanePyramidImageData readData(PlanePyramid pyramid) throws IOException {
        return pyramid.readSpecialImage(this);
    }

    public String getSpecialImageName() {
        return specialImageName;
    }

    public Integer getDesiredWidth() {
        return desiredWidth;
    }

    public Integer getDesiredHeight() {
        return desiredHeight;
    }

    @Override
    public boolean isSavingMemoryMode() {
        return savingMemory;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " for " + specialImageName
            + (desiredWidth == null && desiredHeight == null ?
            "" :
            ", restricted by area " + (desiredWidth == null ? "???" : desiredWidth)
                + "x" + (desiredHeight == null ? "???" : desiredHeight))
            + " (pyramid " + getPyramidUniqueId() + ")";
    }

    @Override
    protected boolean equalsIgnoringPyramidUniqueId(PlanePyramidRequest o) {
        PlanePyramidReadSpecialImageRequest that = (PlanePyramidReadSpecialImageRequest) o;
        if (!specialImageName.equals(that.specialImageName)) {
            return false;
        }
        if (desiredWidth != null ? !desiredWidth.equals(that.desiredWidth) : that.desiredWidth != null) {
            return false;
        }
        return desiredHeight != null ? desiredHeight.equals(that.desiredHeight) : that.desiredHeight == null;
    }

    @Override
    protected int hashCodeIgnoringPyramidUniqueId() {
        int result = specialImageName.hashCode();
        result = 31 * result + (desiredWidth != null ? desiredWidth.hashCode() : 0);
        result = 31 * result + (desiredHeight != null ? desiredHeight.hashCode() : 0);
        return result;
    }
}
