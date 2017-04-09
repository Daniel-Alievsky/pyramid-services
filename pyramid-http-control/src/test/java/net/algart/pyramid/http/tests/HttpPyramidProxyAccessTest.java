/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 Daniel Alievsky, AlgART Laboratory (http://algart.net)
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
import net.algart.pyramid.api.common.PyramidServicesConfiguration;
import net.algart.pyramid.api.http.HttpServerConfiguration;
import net.algart.pyramid.http.control.HttpPyramidProxyControl;
import net.algart.pyramid.requests.PlanePyramidReadSpecialImageRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HttpPyramidProxyAccessTest {
    public static void main(String[] args) throws IOException {
        if (args.length < 6) {
            System.out.printf("Usage: %s "
                    + "host projectRoot serverConfigurationFile pyramidId outputFolder compression%n",
                HttpPyramidProxyControlTest.class.getName());
            return;
        }
        final String host = args[0];
        final Path projectRoot = Paths.get(args[1]);
        final Path serverConfigurationFile = Paths.get(args[2]);
        final String pyramidId = args[3];
        final Path outputFolder = Paths.get(args[4]);
        final double compression = Double.parseDouble(args[5]);
        final PyramidServicesConfiguration servicesConfiguration =
            PyramidServicesConfiguration.readFromRootFolder(projectRoot);
        final HttpServerConfiguration serverConfiguration =
            HttpServerConfiguration.readFromFile(serverConfigurationFile);

        HttpPyramidProxyControl client = new HttpPyramidProxyControl(
            host,
            servicesConfiguration,
            serverConfiguration);

        final PlanePyramidInformation information = client.information(pyramidId);
        System.out.printf("Pyramid information:%n%s%n", information);

        long t1 = System.nanoTime();
        byte[] bytes = client.readSpecialImage(
            pyramidId,
            PlanePyramidReadSpecialImageRequest.WHOLE_SLIDE,
            null, null, false);
        long t2 = System.nanoTime();
        final Path wholeSlideFile = outputFolder.resolve("whole_slide." + information.getReturnedDataFormatName());
        Files.write(wholeSlideFile, bytes);
        System.out.printf("%nSpecial image loaded in %.3f ms and saved in %s%n", (t2 - t1) * 1e-6, wholeSlideFile);

        t1 = System.nanoTime();
        bytes = client.readRectangle(pyramidId, compression, 0, 0, information.getZeroLevelDimX(),
            information.getZeroLevelDimY());
        t2 = System.nanoTime();
        final Path pyramidFile = outputFolder.resolve("compression_"
            + compression + "." + information.getReturnedDataFormatName());
        Files.write(pyramidFile, bytes);
        System.out.printf("Pyramid image loaded in %.3f ms and saved in %s%n", (t2 - t1) * 1e-6, pyramidFile);
    }
}
