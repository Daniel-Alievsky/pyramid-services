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

package net.algart.pyramid.standard;

import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidImageData;
import net.algart.pyramid.PlanePyramidImageRequest;
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.sources.ScalablePlanePyramidSource;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

class StandardPlanePyramid implements PlanePyramid {
    private static final long TIMEOUT = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.standard.pyramidTimeout", 120000));
    // - in milliseconds

    private final String pyramidConfiguration;
    private final ScalablePlanePyramidSource source;
    private final String formatName;
    private final Color backgroundColor;
    private final MatrixToBufferedImageConverter converter;
    private final boolean rawBytes;
    private final boolean cacheable;

    private volatile PlanePyramidInformation information;
    private volatile long lastAccessTime;

    StandardPlanePyramid(
        PlanePyramidSource parentSource,
        JsonObject rendererJson,
        boolean rawBytes,
        boolean cacheable,
        String pyramidConfiguration)
    {
        Objects.requireNonNull(parentSource, "Null plane pyramid source");
        Objects.requireNonNull(rendererJson, "Null renderer JSON");
        Objects.requireNonNull(pyramidConfiguration, "Null pyramid configuration");
        this.source = ScalablePlanePyramidSource.newInstance(parentSource);
        this.formatName = rendererJson.getString("format", "png");
        final boolean transparencySupported = transparencySupported(formatName);
        final JsonNumber opacity = rendererJson.getJsonNumber("opacity");
        final RendererType rendererType = RendererType.parse(rendererJson.getString("type", null))
            .toCompatible(source.bandCount());
        this.backgroundColor = rendererType.getBackgroundColor(
            rendererJson.getString("backgroundColor", null),
            transparencySupported);
        this.converter = rendererType.getConverter(
            Color.decode("#" + rendererJson.getString("color", "FF0000")),
            opacity != null ? opacity.doubleValue() : 1.0,
            transparencySupported);
        // - opacity is not used in DEFAULT renderer
        this.rawBytes = rawBytes;
        this.cacheable = cacheable;
        this.pyramidConfiguration = pyramidConfiguration;
        this.lastAccessTime = System.currentTimeMillis();
    }

    @Override
    public String pyramidConfiguration() {
        return pyramidConfiguration;
    }

    @Override
    public PlanePyramidInformation information() {
        synchronized (source) {
            if (information == null) {
                information = new PlanePyramidInformation(
                    this.source.bandCount(),
                    this.source.dimX(),
                    this.source.dimY(),
                    this.source.elementType()
                );
                //TODO!! JSON metadata zeroLevelActualRectangles, zeroLevelActualAreaBoundaries
            }
            return information;
        }
    }

    @Override
    public void loadResources() {
        source.loadResources();
    }

    @Override
    public void freeResources() {
        source.freeResources(PlanePyramidSource.FlushMethod.STANDARD);
    }


    @Override
    public PlanePyramidImageData readImageData(PlanePyramidImageRequest readImageRequest) throws IOException {
        Objects.requireNonNull(readImageRequest);
        final double compression = readImageRequest.getCompression();
        final long fromX = readImageRequest.getZeroLevelFromX();
        final long fromY = readImageRequest.getZeroLevelFromY();
        final long toX = readImageRequest.getZeroLevelToX();
        final long toY = readImageRequest.getZeroLevelToY();
        if (rawBytes) {
            //TODO!! return RGBRGB bytes/short/... froe readImage method
        }
        BufferedImage bufferedImage = source.readBufferedImage(compression, fromX, fromY, toX, toY, converter);
        if (!transparencySupported(formatName)) {
            bufferedImage = convertARGBtoBGR(bufferedImage, backgroundColor);
        }
        this.lastAccessTime = System.currentTimeMillis();
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (!ImageIO.write(bufferedImage, formatName, stream)) {
            throw new IIOException(formatName + " image format is not supported for " + bufferedImage);
        }
        stream.flush();
        return new PlanePyramidImageData(stream.toByteArray());
    }

    @Override
    public boolean isRawBytes() {
        return rawBytes;
    }

    @Override
    public String format() {
        return formatName;
    }

    @Override
    public boolean isTimeout() {
        return System.currentTimeMillis() - lastAccessTime > TIMEOUT;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public String toString() {
        return "Plane pyramid based on " + source + " (" + formatName + " format)";
    }

    private static boolean transparencySupported(String formatName) {
        return formatName.equalsIgnoreCase("png");
    }

    private static BufferedImage convertARGBtoBGR(BufferedImage bufferedImage, Color backgroundColor) {
        if (bufferedImage.getColorModel().getNumComponents() >= 4) {
            final int width = bufferedImage.getWidth();
            final int height = bufferedImage.getHeight();
            BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            final Graphics graphics = newImage.getGraphics();
            graphics.setColor(backgroundColor);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(bufferedImage, 0, 0, null);
            return newImage;
        } else {
            return bufferedImage;
        }
    }
}
