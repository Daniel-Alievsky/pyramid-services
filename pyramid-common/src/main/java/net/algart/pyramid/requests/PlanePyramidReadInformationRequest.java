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
 * furnished to do so, subject to the following conditions:‏
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
import net.algart.pyramid.PlanePyramidInformation;

import java.io.IOException;

public final class PlanePyramidReadInformationRequest extends PlanePyramidRequest {
    public PlanePyramidReadInformationRequest(String pyramidUniqueId) {
        super(pyramidUniqueId);
    }

    @Override
    public int priority() {
        return READ_INFORMATION_PRIORITY;
    }

    @Override
    public PlanePyramidInformation readData(PlanePyramid pyramid) throws IOException {
        return pyramid.readInformation();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " (pyramid " + getPyramidUniqueId() + ")";
    }

    @Override
    protected boolean equalsIgnoringPyramidUniqueId(PlanePyramidRequest o) {
        return true;
    }

    @Override
    protected int hashCodeIgnoringPyramidUniqueId() {
        return PlanePyramidReadInformationRequest.class.getName().hashCode();
    }
}
