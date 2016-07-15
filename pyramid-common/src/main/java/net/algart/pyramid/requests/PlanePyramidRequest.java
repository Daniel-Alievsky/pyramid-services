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
import net.algart.pyramid.PlanePyramidData;

import java.io.IOException;
import java.util.Objects;

public abstract class PlanePyramidRequest {
    public static final int READ_PYRAMID_PRIORITY = 10000;
    static final int READ_SPECIAL_IMAGE_PRIORITY = 5000;
    static final int READ_INFORMATION_PRIORITY = 6000;

    private final String pyramidUniqueId;

    protected PlanePyramidRequest(String pyramidUniqueId) {
        this.pyramidUniqueId = Objects.requireNonNull(pyramidUniqueId, "Null pyramidUniqueId");
    }

    public final String getPyramidUniqueId() {
        return pyramidUniqueId;
    }

    /**
     * <p>Returns priority of this request. The default value is {@link #READ_PYRAMID_PRIORITY},
     * that corresponds to the most popular task of reading pyramid data and should be the maximum
     * or one of maximal values.</p>
     *
     * <p>All priorities, used by this package, are multiples of 1000.
     * You may little vary the default value by adding or subtracting values less than 1000:
     * it will not change the order of priorities of basic requests.
     *
     * @return priority of this request.
     */
    public int priority() {
        return READ_PYRAMID_PRIORITY;
    }

    public abstract PlanePyramidData readData(PlanePyramid pyramid) throws IOException;

    public boolean isSavingMemoryMode() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlanePyramidRequest that = (PlanePyramidRequest) o;
        return pyramidUniqueId.equals(that.pyramidUniqueId)
            && equalsIgnoringPyramidUniqueId(that);

    }

    @Override
    public int hashCode() {
        return 31 * pyramidUniqueId.hashCode() + hashCodeIgnoringPyramidUniqueId();
    }

    // The following two methods MUST be implemented to provide correct caching requests:
    protected abstract boolean equalsIgnoringPyramidUniqueId(PlanePyramidRequest o);

    protected abstract int hashCodeIgnoringPyramidUniqueId();
}

