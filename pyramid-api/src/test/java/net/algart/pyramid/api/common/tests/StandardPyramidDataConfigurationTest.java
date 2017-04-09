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

package net.algart.pyramid.api.common.tests;

import net.algart.pyramid.api.common.StandardPyramidDataConfiguration;
import net.algart.pyramid.api.common.UnknownPyramidDataFormatException;
import net.algart.pyramid.api.common.PyramidServicesConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class StandardPyramidDataConfigurationTest {
    public static void main(String[] args) throws IOException, UnknownPyramidDataFormatException {
        if (args.length == 0) {
            System.out.printf("Usage: %s pyramidFolder projectRoot%n",
                StandardPyramidDataConfigurationTest.class.getName());
            return;
        }
//        String fileName = "data.gif";
//        String fileRegExp = ".*gif$";
//        System.out.println(Pattern.compile(fileRegExp).matcher(fileName).matches());

        final Path pyramidFolder = Paths.get(args[0]);
        final Path projectRoot = Paths.get(args[1]);
        final PyramidServicesConfiguration servicesConfiguration =
            PyramidServicesConfiguration.readFromRootFolder(projectRoot);
        final StandardPyramidDataConfiguration configuration =
            StandardPyramidDataConfiguration.readFromPyramidFolder(pyramidFolder,
                servicesConfiguration.allSortedFormats());
        System.out.printf("Pyramid data configuration:%n  %s%n%n", configuration);
        final List<Path> files = configuration.accompanyingFiles();
        System.out.printf("Accompanying files or folders:%s%n", files.isEmpty() ? " no files" : "");
        for (Path f : files) {
            System.out.printf("  %s (%s)%n",
                f, !Files.exists(f) ? "NOT exists" : Files.isDirectory(f) ? "existing folder" : "existing file");
        }
    }
}
