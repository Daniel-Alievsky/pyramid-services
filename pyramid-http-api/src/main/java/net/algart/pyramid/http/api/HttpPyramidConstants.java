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

package net.algart.pyramid.http.api;

public class HttpPyramidConstants {
    public static final String ALIVE_STATUS_COMMAND_PREFIX = "/pp-alive-status";
    public static final String FINISH_COMMAND_PREFIX = "/pp-finish";
    public static final String INFORMATION_COMMAND_PREFIX = "/pp-information";
    public static final String READ_RECTANGLE_COMMAND_PREFIX = "/pp-read-rectangle";
    public static final String TMS_COMMAND_PREFIX = "/pp-tms";
    public static final String ZOOMIFY_COMMAND_PREFIX = "/pp-zoomify";
    public static final String READ_SPECIAL_IMAGE_COMMAND_PREFIX = "/pp-read-special-image";
    public static final String PYRAMID_ID_ARGUMENT_NAME = "pyramidId";
    public static final String ALIVE_RESPONSE = "Alive";

    private HttpPyramidConstants() {
    }
}
