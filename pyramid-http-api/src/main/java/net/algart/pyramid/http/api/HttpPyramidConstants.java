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
    public static final String HTTP_PYRAMID_SERVER_CLASS_NAME =
        "net.algart.pyramid.http.server.HttpPyramidServer";
    public static final String HTTP_PYRAMID_SERVER_SERVICE_MODE_FLAG = "--serviceMode";

    public static final String LOCAL_HOST = System.getProperty(
        "net.algart.pyramid.http.localHost", "localhost");
    public static final String CONFIG_ROOT_DIR = System.getProperty(
        "net.algart.pyramid.http.configRoot", "/pp-links");
    public static final String CONFIG_FILE_NAME = System.getProperty(
        "net.algart.pyramid.http.configFile", "config.json");
    public static final String DEFAULT_PYRAMID_PATH_NAME = System.getProperty(
        "net.algart.pyramid.http.pyramidPathName", "pyramidPath");
    public static final int MAX_NUMBER_OF_PYRAMIDS_IN_POOL = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.http.imageCachingMemory", 256));

    public static final long IMAGE_CACHING_MEMORY = Math.max(16, Long.getLong(
        "net.algart.pyramid.http.imageCachingMemory", 256L * 1024L * 1024L));

    public static final int SERVER_WAITING_IN_QUEUE_AND_READING_TIMEOUT = 60000;
    public static final int SERVER_SENDING_TIMEOUT = 120000;

    public static final int CLIENT_CONNECTION_TIMEOUT = 30000;
    public static final int CLIENT_READ_TIMEOUT = 90000;
    // - read timeout should be greater than timeouts in SERVER_WAITING_IN_QUEUE_AND_READING_TIMEOUT class

    public static final int MAX_ALLOWED_PORT = 30000;

    public static final int SYSTEM_CONNANDS_DELAY = 500;

    /**
     * Note: all command prexies start with '/' character and contains only alphanumeric characters after this
     * (maybe including also '-' and '_' characters).
     */
    public static class CommandPrefixes {
        public static final String PREXIX_START = "/pp-";
        public static final String PREXIX_START_REG_EXP = "^\\/pp\\-";
        // - must correspond to PREFIX_START
        public static final String FINISH = PREXIX_START + "finish";
        public static final String GC = PREXIX_START + "gc";
        public static final String ALIVE_STATUS = PREXIX_START + "alive-status";
        public static final String INFORMATION = PREXIX_START + "information";
        public static final String READ_RECTANGLE = PREXIX_START + "read-rectangle";
        public static final String TMS = PREXIX_START + "tms";
        public static final String ZOOMIFY = PREXIX_START + "zoomify";
        public static final String READ_SPECIAL_IMAGE = PREXIX_START + "read-special-image";

        private CommandPrefixes() {}
    }

    public static final String DEFAULT_SYSTEM_COMMANDS_FOLDER = "for-key-files";
    /**
     * Names of key files in {@link #DEFAULT_SYSTEM_COMMANDS_FOLDER} start from this prefix,
     * where %d is replaced with the port number.
     */
    public static final String SYSTEM_COMMANDS_FILE_PREFIX = ".command.%d.";

    public static final String PYRAMID_ID_ARGUMENT_NAME = "pyramidId";
    public static final String PYRAMID_ID_PREFIX_IN_PATHNAME = "~~~PyramidID~~~";
    public static final String PYRAMID_ID_POSTFIX_IN_PATHNAME = "~~~";
    public static final String ALIVE_RESPONSE = "Alive";

    private HttpPyramidConstants() {
    }
}
