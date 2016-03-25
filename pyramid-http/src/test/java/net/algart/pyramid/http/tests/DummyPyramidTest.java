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

import net.algart.pyramid.*;
import net.algart.pyramid.http.HttpPyramidService;
import net.algart.pyramid.http.HttpPyramidServiceSimpleLauncher;

import javax.imageio.ImageIO;
import javax.json.Json;
import javax.json.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Start this test and type in the browser URL something like this:
 * <a href="http://localhost:81/pp-read-rectangle?pyramidId={&quot;color&quot;:&quot;FFFF00&quot;}&amp;compression=1&amp;fromX=0&amp;fromY=0&amp;toX=1000&amp;toY=1000">
 *     an example</a>
 */
public class DummyPyramidTest {
    private static final Logger LOG = Logger.getLogger(DummyPyramidTest.class.getName());

    private static final int DIM_X = 10000;
    private static final int DIM_Y = 10000;
    private static final int USED_CPU_COUNT = Math.min(4, Runtime.getRuntime().availableProcessors());

    public static void main(String[] args) throws Exception {
        System.setProperty(
            "net.algart.pyramid.http.port",
            "81");
        System.setProperty(
            "net.algart.pyramid.http.planePyramidFactory",
            MyPyramidFactory.class.getName());
        new HttpPyramidServiceSimpleLauncher() {
            @Override
            protected HttpPyramidService newService(PlanePyramidFactory factory, int port) throws IOException {
                return new HttpPyramidService(factory, port) {
                    @Override
                    public String pyramidIdToConfig(String pyramidId) throws IOException {
                        return pyramidId;
                        // So, pyramidId shoild contain JSON with path to the pyramid;
                        // VERY UNSAFE for end-user server, but convenient for testing
                    }
                };
            }
        }.doMain(args);
    }

    public static class MyPyramidFactory implements PlanePyramidFactory {
        @Override
        public void initializeConfiguration(Object configuration) {
        }

        @Override
        public PlanePyramid newPyramid(String configJson) throws Exception {
            LOG.info("Creating pyramid on JSON " + configJson);
            final JsonObject config = Json.createReader(new StringReader(configJson)).readObject();
            final Color color = Color.decode("#" + config.getString("color"));
            LOG.info("Creating pyramid with color " + color);
            return new MyPyramid(color);
        }
    }

    public static class MyPyramid implements PlanePyramid {
        private final Color color;

        MyPyramid(Color color) {
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public String uniqueId() {
            return String.valueOf(color.hashCode());
        }

        @Override
        public PlanePyramidInformation information() {
            return new PlanePyramidInformation(3, DIM_X, DIM_Y, byte.class, null);
        }

        @Override
        public void loadResources() {
        }

        @Override
        public void freeResources() {
        }

        @Override
        public PlanePyramidImageData readImageData(PlanePyramidImageRequest readImageRequest) throws IOException {
            final long fromX = readImageRequest.getFromX();
            final long fromY = readImageRequest.getFromY();
            final long toX = readImageRequest.getToX();
            final long toY = readImageRequest.getToY();
            return new PlanePyramidImageData(makePngBytes((int) (toX - fromX), (int) (toY - fromY)));
        }

        @Override
        public boolean isRawBytes() {
            return false;
        }


        @Override
        public String format() {
            return "png";
        }

        @Override
        public boolean isTimeout() {
            return false;
        }

        @Override
        public boolean isCacheable() {
            return false;
        }

        byte[] makePngBytes(int width, int height) {
            System.out.printf("Making image for color %s...%n", color);
            long t1 = System.nanoTime();
            final BufferedImage image = makeImage(width, height);
            long t2 = System.nanoTime();
            final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "PNG", byteStream);
                byteStream.close();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            long t3 = System.nanoTime();
            LOG.info(String.format(Locale.US,
                "Image made in %.3f ms and converted to PNG in %.3f ms "
                    + "(%d bytes, %d active threads)%n",
                (t2 - t1) * 1e-6, (t3 - t2) * 1e-6,
                byteStream.size(), USED_CPU_COUNT));
            return byteStream.toByteArray();
        }

        private BufferedImage makeImage(int width, int height) {
            final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            Random rnd = new Random(0);
            for (int y = 0; y < result.getHeight(); y++) {
                for (int x = 0; x < result.getWidth(); x++) {
                    double mult = rnd.nextDouble();
                    result.setRGB(x, y, new Color(
                        (int) (color.getRed() * mult),
                        (int) (color.getGreen() * mult),
                        (int) (color.getBlue() * mult)).getRGB());
                }
            }
            return result;
        }
    }
}
