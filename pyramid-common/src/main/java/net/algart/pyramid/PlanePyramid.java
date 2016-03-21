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

import java.io.IOException;

public interface PlanePyramid {

    /**
     * Returns some digest that must be different for different pyramids.
     * We recommend to return the same value for identical actual pyramids with identical data and behaviour
     * (results of other methods), for example, when the same pyramid on the disk is accessed
     * via different instances of this class.
     *
     * @return unique identifier of the given pyramid.
     */
    String uniqueId();

    PlanePyramidInformation information();

    void loadResources();

    void freeResources();

    PlanePyramidImageData readImageData(PlanePyramidImageRequest readImageRequest) throws IOException;

    boolean isRawBytes();

    /**
     * Returns the format of the byte sequence, returned by {@link #readImageData} method: <tt>png</tt>,
     * <tt>jpeg</tt>, etc.
     * It may be ignored if {@link #isRawBytes()} returns <tt>true</tt>.
     *
     * @return format of the image, returned as a byte sequence by {@link #readImageData}.
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
