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

package net.algart.pyramid.http.control;

import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.api.http.HttpPyramidApiTools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

interface PyramidAccessControl {
    default PlanePyramidInformation information(String pyramidId) throws IOException {
        final HttpURLConnection connection = openGetConnection(HttpPyramidApiTools.informationPathAndQuery(pyramidId));
        try (final InputStreamReader reader = new InputStreamReader(connection.getInputStream(),
            StandardCharsets.UTF_8))
        {
            return PlanePyramidInformation.valueOf(reader);
        }
    }

    default InputStream openReadRectangleStream(
        String pyramidId,
        double compression,
        long fromX,
        long fromY,
        long toX,
        long toY)
        throws IOException
    {
        final HttpURLConnection connection = openGetConnection(HttpPyramidApiTools.readRectanglePathAndQuery(
            pyramidId, compression, fromX, fromY, toX, toY));
        return connection.getInputStream();
    }

    // In future, this method will use more efficient tools than standard Java connection
    default byte[] readRectangle(
        String pyramidId,
        double compression,
        long fromX,
        long fromY,
        long toX,
        long toY)
        throws IOException
    {
        try (InputStream inputStream = openReadRectangleStream(
            pyramidId, compression, fromX, fromY, toX, toY))
        {
            return streamToBytes(inputStream);
        }
    }

    default InputStream openReadSpecialImageStream(
        String pyramidId,
        String specialImageName,
        Integer width,
        Integer height,
        boolean savingMemory)
        throws IOException
    {
        final HttpURLConnection connection = openGetConnection(HttpPyramidApiTools.readSpecialImagePathAndQuery(
            pyramidId, specialImageName, width, height, savingMemory));
        return connection.getInputStream();
    }

    // In future, this method will use more efficient tools than standard Java connection
    default byte[] readSpecialImage(
        String pyramidId,
        String specialImageName,
        Integer width,
        Integer height,
        boolean savingMemory)
        throws IOException
    {
        try (InputStream inputStream = openReadSpecialImageStream(
            pyramidId, specialImageName, width, height, savingMemory))
        {
            return streamToBytes(inputStream);
        }
    }

    default HttpURLConnection openGetConnection(String pathAndQuery) throws IOException {
        return HttpPyramidApiTools.openConnection(connectionURI(pathAndQuery), "GET", true);
    }

    default HttpURLConnection openPostConnection(String pathAndQuery) throws IOException {
        return HttpPyramidApiTools.openConnection(connectionURI(pathAndQuery), "POST", true);
    }

    URI connectionURI(String pathAndQuery);

    static byte[] streamToBytes(InputStream inputStream) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[16384];
        int len;
        while ((len = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, len);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}
