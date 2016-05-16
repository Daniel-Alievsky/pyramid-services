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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidConstants {
    public static final String CONFIG_ROOT_DIR = System.getProperty(
        "net.algart.pyramid.http.configRoot", "/pp-links");
    public static final String CONFIG_FILE_NAME = System.getProperty(
        "net.algart.pyramid.http.configFile", "config.json");
    public static final int MAX_NUMBER_OF_PYRAMIDS_IN_POOL = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.imageCachingMemory", 256));

    public static final long IMAGE_CACHING_MEMORY = Math.max(16, Long.getLong(
        "net.algart.pyramid.http.imageCachingMemory", 256L * 1024L * 1024L));

    public static final int SERVER_WAITING_IN_QUEUE_AND_READING_TIMEOUT = 60000;
    public static final int SERVER_SENDING_TIMEOUT = 120000;

    public static final int CLIENT_CONNECTION_TIMEOUT = 30000;
    public static final int CLIENT_READ_TIMEOUT = 90000;
    // - read timeout should be greater than timeouts in SERVER_WAITING_IN_QUEUE_AND_READING_TIMEOUT class

    public static final String ALIVE_STATUS_COMMAND_PREFIX = "/pp-alive-status";
    public static final String FINISH_COMMAND_PREFIX = "/pp-finish";
    public static final String GC_COMMAND_PREFIX = "/pp-gc";
    public static final String INFORMATION_COMMAND_PREFIX = "/pp-information";
    public static final String READ_RECTANGLE_COMMAND_PREFIX = "/pp-read-rectangle";
    public static final String TMS_COMMAND_PREFIX = "/pp-tms";
    public static final String ZOOMIFY_COMMAND_PREFIX = "/pp-zoomify";
    public static final String READ_SPECIAL_IMAGE_COMMAND_PREFIX = "/pp-read-special-image";
    public static final String PYRAMID_ID_ARGUMENT_NAME = "pyramidId";
    public static final String ALIVE_RESPONSE = "Alive";

    public static String pyramidIdToConfiguration(String pyramidId) throws IOException {
        final Path path = Paths.get(CONFIG_ROOT_DIR, pyramidId, CONFIG_FILE_NAME);
        if (!Files.isRegularFile(path)) {
            throw new FileNotFoundException("File " + path.toAbsolutePath() + " does not exists");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private HttpPyramidConstants() {
    }
}
