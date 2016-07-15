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

public abstract class PlanePyramidData {
    private final long creationTime = System.currentTimeMillis();

    // Disable subclassing outside this package:
    PlanePyramidData() {
    }

    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns <tt>true</tt>, if this data is really a short string (maximum kilobytes, not megabytes).
     * If this method returns <tt>null</tt>, {@link #getShortString()} method returns non-<tt>null</tt> value;
     * if it returns <tt>false</tt>, {@link #getShortString()} method returns <tt>null</tt>.
     *
     * @return whether this data is a short string.
     */
    public boolean isShortString() {
        return false;
    }

    /**
     * Returns all this data, if this data is really a short string, or <tt>null</tt> in other case.
     *
     * @return this data, if it is really a short string, or <tt>null</tt> in other case.
     */
    public String getShortString() {
        return null;
    }

    /**
     * Returns this data in a form of byte sequence, suitable for sending as HTTP or other response.
     * For example, for image it can be a sequence of byte of png-file.
     *
     * @return this data in a form of byte sequence; never <tt>null</tt>.
     */
    public abstract byte[] getBytes();

    public abstract String getContentMIMEType();

    abstract long estimatedMemoryInBytes();
}
