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

package net.algart.pyramid.api.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

public class HttpPyramidApiTools {
    private HttpPyramidApiTools() {
    }

    public static Path keyFile(String urlPrefix, int port, Path systemCommandsFolder) {
        final String keyFileFrefix = String.format(Locale.US, HttpPyramidConstants.SYSTEM_COMMANDS_FILE_PREFIX, port);
        return systemCommandsFolder.resolve(keyFileFrefix + urlPrefix.substring(1, urlPrefix.length()));
    }

    public static boolean isUriPyramidCommand(String uriPath) {
        return uriPath.matches(HttpPyramidConstants.CommandPrefixes.PREXIX_START_REG_EXP);
    }

    public static String informationPathAndQuery(
        String pyramidId)
    {
        return HttpPyramidConstants.CommandPrefixes.INFORMATION
            + "?" + HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME
            + "=" + encodePyramidId(pyramidId);
    }

    public static String readSpecialImagePathAndQuery(
        String pyramidId,
        String specialImageName,
        Integer width,
        Integer height,
        boolean savingMemory)
    {
        return HttpPyramidConstants.CommandPrefixes.READ_SPECIAL_IMAGE
            + "?" + HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME
            + "=" + encodePyramidId(pyramidId)
            + "&specialImageName=" + specialImageName
            + (width == null ? "" : "&width=" + width)
            + (height == null ? "" : "&height=" + width)
            + (!savingMemory ? "" : "&savigMemory=true");
    }

    public static String readRectanglePathAndQuery(
        String pyramidId,
        double compression,
        long fromX,
        long fromY,
        long toX,
        long toY)
    {
        return HttpPyramidConstants.CommandPrefixes.READ_RECTANGLE
            + "?" + HttpPyramidConstants.PYRAMID_ID_PARAMETER_NAME
            + "=" + encodePyramidId(pyramidId)
            + "&compression=" + compression
            + "&fromX=" + fromX
            + "&fromY=" + fromY
            + "&toX=" + toX
            + "&toY=" + toY;
    }


    public static HttpURLConnection openConnection(URL url, String requestMethod, boolean checkStatus)
        throws IOException
    {
        final URLConnection connection = url.openConnection();
        connection.setConnectTimeout(HttpPyramidConstants.CLIENT_CONNECTION_TIMEOUT);
        connection.setReadTimeout(HttpPyramidConstants.CLIENT_READ_TIMEOUT);
        // In future, if necessary, we will maybe provide better timeouts:
        // http://stackoverflow.com/questions/3163693/java-urlconnection-timeout
        if (!(connection instanceof HttpURLConnection)) {
            throw new AssertionError("Invalid type of URL connection (not HttpURLConnection)");
        }
        final HttpURLConnection result = (HttpURLConnection) connection;
//        if (result instanceof HttpsURLConnection) {
//            ((HttpsURLConnection)result).setHostnameVerifier(new HostnameVerifier() {
//                @Override
//                public boolean verify(String s, SSLSession sslSession) {
//                    return true;
//                }
//            });
//        }
        result.setRequestMethod(requestMethod);
        if (checkStatus) {
            if (result.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Invalid response: code " + result.getResponseCode()
                    + ", message " + result.getResponseMessage());
            }
        }
        return result;
    }

    private static String encodePyramidId(String pyramidId) {
        try {
            return URLEncoder.encode(pyramidId, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 encoding must be supported always");
        }
    }
}
