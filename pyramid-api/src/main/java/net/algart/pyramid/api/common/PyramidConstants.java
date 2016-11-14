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

package net.algart.pyramid.api.common;

public class PyramidConstants {
    // The following constants usually do not change, but theoretically can be customized via
    // HttpPyramidConfiguration.GLOBAL_CONFIGURATION_FILE_NAME (json-file), "commonVmOptions" section.
    public static final String PYRAMID_PATH_NAME_IN_CONFIGURATION_JSON = System.getProperty(
        "net.algart.pyramid.api.common.pyramidPathNameInJson", "pyramidPath");
    public static final String PYRAMID_CONFIGURATION_FILE_NAME = System.getProperty(
        "net.algart.pyramid.api.common.pyramidConfigurationFileName", ".pp.json");
    public static final String FORMAT_NAME_IN_PYRAMID_CONFIGURATION_FILE_NAME = "formatName";

    // The following constants can be customized also by more simple way:
    // HttpPyramidSpecificServerConfiguration json-file
    public static final String DEFAULT_CONFIG_ROOT_DIR = System.getProperty(
        "net.algart.pyramid.api.common.configRoot", "/pp-links");
    public static final String DEFAULT_CONFIG_FILE_NAME = System.getProperty(
        "net.algart.pyramid.api.common.configFile", "config.json");
    // Note that the following constant is not used by pyramid services,
    // but can be useful for upload and user management systems
    public static final String DEFAULT_IMAGES_ROOT_DIR = System.getProperty(
        "net.algart.pyramid.api.common.imagesRoot", "/pp-images");


    private PyramidConstants() {
    }
}
