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

package net.algart.pyramid.http.tests;

import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.api.http.HttpPyramidConfiguration;
import net.algart.pyramid.api.http.HttpPyramidSpecificServerConfiguration;
import net.algart.pyramid.http.control.HttpPyramidProxyControl;
import net.algart.pyramid.requests.PlanePyramidReadSpecialImageRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidProxyAccessTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 5) {
            System.out.printf("Usage: %s host projectRoot specificServerConfigurationFile pyramidId outputFolder%n",
                HttpPyramidProxyControlTest.class.getName());
            return;
        }
        final String host = args[0];
        final Path projectRoot = Paths.get(args[1]);
        final Path specificServerConfigurationFile = Paths.get(args[2]);
        final String pyramidId = args[3];
        final Path outputFolder = Paths.get(args[4]);
        final HttpPyramidConfiguration configuration = HttpPyramidConfiguration.readFromRootFolder(projectRoot);
        final HttpPyramidSpecificServerConfiguration specificServerConfiguration =
            HttpPyramidSpecificServerConfiguration.readFromFile(specificServerConfigurationFile);

        HttpPyramidProxyControl client = new HttpPyramidProxyControl(
            host,
            configuration,
            specificServerConfiguration);

        final PlanePyramidInformation information = client.information(pyramidId);
        System.out.printf("Pyramid information:%n%s%n", information);

        byte[] bytes = client.readSpecialImage(
            pyramidId,
            PlanePyramidReadSpecialImageRequest.WHOLE_SLIDE,
            null, null, false);
        Files.write(outputFolder.resolve("whole_slide." + information.getRenderingFormatName()), bytes);

        bytes = client.readRectangle(pyramidId, 64.0, 0, 0, information.getZeroLevelDimX(),
            information.getZeroLevelDimY());
        Files.write(outputFolder.resolve("compression_64." + information.getRenderingFormatName()), bytes);
    }
}
