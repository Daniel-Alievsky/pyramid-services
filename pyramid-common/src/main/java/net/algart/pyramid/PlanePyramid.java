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

import net.algart.pyramid.requests.PlanePyramidImageRequest;
import net.algart.pyramid.requests.PlanePyramidRequest;
import net.algart.pyramid.requests.PlanePyramidSpecialImageRequest;

import java.io.IOException;

public interface PlanePyramid {

    /**
     * We recommend to use this key in JSON string {@link #pyramidConfiguration()} to specify the visual behaviour
     * of this pyramid (the method {@link #readImage(PlanePyramidImageRequest)}.
     * See an example of JSON in {@link PlanePyramidFactory#newPyramid(String)} method.
     */
    String RENDERER_KEY = "renderer";

    /**
     * <p>Return the pyramid configuration passed to {@link PlanePyramidFactory#newPyramid(String)} method
     * as <tt><pyramidConfiguration/tt> argument while creating this pyramid.</p>
     *
     * <p>Note: this string must be an unique pyramid digest, that must be different for different pyramids.
     * We recommend to return the same value for identical actual pyramids with identical data and behaviour
     * (results of other methods), for example, when the same pyramid on the disk is accessed
     * via different instances of this class.
     *
     * @return all information about the data source and parameters.
     */
    String pyramidConfiguration();

    void loadResources();

    void freeResources();

    PlanePyramidInformation readInformation();

    default PlanePyramidData read(PlanePyramidRequest pyramidRequest) throws IOException {
        return pyramidRequest.read(this);
    }

    PlanePyramidData readImage(PlanePyramidImageRequest imageRequest) throws IOException;

    PlanePyramidData readSpecialImage(PlanePyramidSpecialImageRequest specialImageRequest) throws IOException;

    boolean isRawBytes();

    /**
     * Returns the format of the byte sequence, returned by {@link #readImage} method: <tt>png</tt>,
     * <tt>jpeg</tt>, etc.
     * It may be ignored if {@link #isRawBytes()} returns <tt>true</tt>.
     *
     * @return format of the image, returned as a byte sequence by {@link #readImage}.
     */
    String format();

    /**
     * Returns true if the current time minus last access time is greater than some timeout.
     * Some implementation may return <tt>false</tt> always (this feature is not supported).
     *
     * @return true if there is a timeout of accesses to this pyramid.
     */
    boolean isTimeout();

    /**
     * <p>>Returns <tt>rtrue</tt> if this pyramid is never changing and can be cached by server and client.</p>
     *
     * <p>This method always works very quickly.</p>
     *
     * @return is this pyramid cacheable.
     */
    boolean isCacheable();
}
