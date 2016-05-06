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

import net.algart.arrays.Matrices;
import net.algart.arrays.Matrix;
import net.algart.arrays.PArray;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidData;
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.requests.PlanePyramidImageRequest;
import net.algart.pyramid.requests.PlanePyramidSpecialImageRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.algart.simagis.pyramid.PlanePyramidSource.DIM_HEIGHT;
import static net.algart.simagis.pyramid.PlanePyramidSource.DIM_WIDTH;

class StandardPlanePyramid implements PlanePyramid {
    private static final long TIMEOUT = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.standard.pyramidTimeout", 120000));
    // - in milliseconds

    private final String pyramidConfiguration;
    private final ScalablePlanePyramidSource source;
    private final String formatName;
    private final Color backgroundColor;
    private final MatrixToBufferedImageConverter converter;
    private final MatrixToBufferedImageConverter specialImageCconverter;
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
        this.specialImageCconverter = new MatrixToBufferedImageConverter.Packed3DToPackedRGB(transparencySupported);
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
    public void loadResources() {
        source.loadResources();
    }

    @Override
    public void freeResources() {
        source.freeResources(PlanePyramidSource.FlushMethod.STANDARD);
    }

    @Override
    public PlanePyramidInformation readInformation() {
        synchronized (source) {
            if (information == null) {
                information = new PlanePyramidInformation(
                    this.source.bandCount(),
                    this.source.dimX(),
                    this.source.dimY(),
                    this.source.elementType()
                );
                information.setPixelSizeInMicrons(this.source.pixelSizeInMicrons());
                information.setMagnification(this.source.magnification());
                final List<String> specialImageNames = new ArrayList<>();
                for (PlanePyramidSource.SpecialImageKind kind : PlanePyramidSource.SpecialImageKind.values()) {
                    if (source.isSpecialMatrixSupported(kind)) {
                        specialImageNames.add(kind.name());
                    }
                }
                information.setExistingSpecialImages(specialImageNames);
                information.setAdditionalMetadata(this.source.additionalMetadata());
                //TODO!! JSON metadata zeroLevelActualRectangles, zeroLevelActualAreaBoundaries
            }
            return information;
        }
    }

    @Override
    public PlanePyramidData readImage(PlanePyramidImageRequest imageRequest) throws IOException {
        Objects.requireNonNull(imageRequest);
        final double compression = imageRequest.getCompression();
        final long fromX = imageRequest.getZeroLevelFromX();
        final long fromY = imageRequest.getZeroLevelFromY();
        final long toX = imageRequest.getZeroLevelToX();
        final long toY = imageRequest.getZeroLevelToY();
        if (rawBytes) {
            //TODO!! return RGBRGB bytes/short/... froe readImage method
        }
        BufferedImage bufferedImage = source.readBufferedImage(compression, fromX, fromY, toX, toY, converter);
        this.lastAccessTime = System.currentTimeMillis();
        return bufferedImageToBytes(bufferedImage, formatName);
    }

    @Override
    public PlanePyramidData readSpecialImage(PlanePyramidSpecialImageRequest specialImageRequest) throws IOException {
        Objects.requireNonNull(specialImageRequest);
        final String name = specialImageRequest.getSpecialImageName();
        Integer width = specialImageRequest.getDesiredWidth();
        Integer height = specialImageRequest.getDesiredHeight();
        final PlanePyramidSource.SpecialImageKind kind;
        try {
            kind = PlanePyramidSource.SpecialImageKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IOException("Unknown special image name " + name);
        }
        Matrix<? extends PArray> m = source.readSpecialMatrix(kind);
        if (width != null || height != null && m.size() > 0) {
            // Resizing to one or both these sizes:
            if (height == null) {
                height = (int) (width * (double) m.dim(DIM_HEIGHT) / (double) m.dim(DIM_WIDTH));
            } else if (width == null) {
                width = (int) (height * (double) m.dim(DIM_WIDTH) / (double) m.dim(DIM_HEIGHT));
            }
            m = Matrices.asResized(Matrices.ResizingMethod.POLYLINEAR_AVERAGING, m, m.dim(0), width, height);
        }
        final BufferedImage bufferedImage = specialImageCconverter.toBufferedImage(m);
        return bufferedImageToBytes(bufferedImage, formatName);
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

    private PlanePyramidData bufferedImageToBytes(BufferedImage bufferedImage, String formatName)
        throws IOException
    {
        if (!transparencySupported(formatName)) {
            bufferedImage = convertARGBtoBGR(bufferedImage, backgroundColor);
        }
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (!ImageIO.write(bufferedImage, formatName, stream)) {
            throw new IIOException(formatName + " image format is not supported for " + bufferedImage);
        }
        stream.flush();
        return new PlanePyramidData(stream.toByteArray());
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
