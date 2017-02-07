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

package net.algart.pyramid.standard;

import net.algart.arrays.*;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.imageio.QuickBMPWriter;
import net.algart.math.IPoint;
import net.algart.math.IRectangularArea;
import net.algart.math.functions.LinearFunc;
import net.algart.pyramid.PlanePyramid;
import net.algart.pyramid.PlanePyramidImageData;
import net.algart.pyramid.PlanePyramidInformation;
import net.algart.pyramid.api.common.StandardPyramidDataConfiguration;
import net.algart.pyramid.requests.PlanePyramidReadImageRequest;
import net.algart.pyramid.requests.PlanePyramidReadSpecialImageRequest;
import net.algart.simagis.pyramid.PlanePyramidSource;
import net.algart.simagis.pyramid.sources.ScalablePlanePyramidSource;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.json.*;
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
    /**
     * Timeout (in milliseconds), after which the pyramid, if there are no access to it, is automatically
     * closed and removed from pool.
     */
    public static final long PYRAMID_TIMEOUT = Math.max(16, Integer.getInteger(
        "net.algart.pyramid.standard.pyramidTimeout", 30000));

    private static final boolean USE_QUICK_BMP_WRITER = true;

    private final StandardPlanePyramidFactory factory;
    private final String pyramidConfiguration;
    private final ScalablePlanePyramidSource source;
    private final String renderingFormatName;
    private final Color renderingBackgroundColor;
    private final MatrixToBufferedImageConverter converter;
    private final MatrixToBufferedImageConverter specialImageConverter;
    private final boolean rawBytes;
    private final boolean cacheable;

    private volatile PlanePyramidInformation information;
    private volatile long lastAccessTime;

    StandardPlanePyramid(
        StandardPlanePyramidFactory factory,
        PlanePyramidSource parentSource,
        StandardPyramidDataConfiguration pyramidDataConfiguration,
        JsonObject rendererJson,
        boolean rawBytes,
        boolean cacheable,
        String pyramidConfiguration)
        throws IOException
    {
        Objects.requireNonNull(factory, "Null factory");
        Objects.requireNonNull(parentSource, "Null plane pyramid source");
        Objects.requireNonNull(pyramidDataConfiguration, "Null pyramid data configuration");
        Objects.requireNonNull(rendererJson, "Null renderer JSON");
        Objects.requireNonNull(pyramidConfiguration, "Null pyramid configuration");
        this.factory = factory;
        this.source = ScalablePlanePyramidSource.newInstance(parentSource);
        this.renderingFormatName = rendererJson.getString("format", "png");
        final boolean transparencySupported = transparencySupported(renderingFormatName);
        final JsonNumber opacity = rendererJson.getJsonNumber("opacity");
        final RendererType rendererType = RendererType.parse(rendererJson.getString("type", null))
            .toCompatible(source.bandCount());
        this.renderingBackgroundColor = rendererType.getBackgroundColor(
            rendererJson.getString("backgroundColor", null),
            transparencySupported);
        this.converter = rendererType.getConverter(
            Color.decode("#" + rendererJson.getString("color", "FF0000")),
            opacity != null ? opacity.doubleValue() : 1.0,
            transparencySupported);
        // - opacity is not used in DEFAULT renderer
        this.specialImageConverter = new MatrixToBufferedImageConverter.Packed3DToPackedRGB(transparencySupported);
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
                information.setPyramidFormatName(factory.getPyramidFormat().getFormatName());
                information.setRenderingFormatName(renderingFormatName);
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
                final JsonObject actualAreas = actualAreasToJson(this.source);
                if (actualAreas != null) {
                    information.setActualAreas(actualAreas.toString());
                }
            }
            return information;
        }
    }

    @Override
    public PlanePyramidImageData readImage(PlanePyramidReadImageRequest imageRequest) throws IOException {
        Objects.requireNonNull(imageRequest);
        final double compression = imageRequest.getCompression();
        final long fromX = imageRequest.getZeroLevelFromX();
        final long fromY = imageRequest.getZeroLevelFromY();
        final long toX = imageRequest.getZeroLevelToX();
        final long toY = imageRequest.getZeroLevelToY();
        if (rawBytes) {
            //TODO!! return some form of bytes, maybe Protocol Buffers
        }
        if (USE_QUICK_BMP_WRITER && renderingFormatName.equalsIgnoreCase("bmp")) {
            // use more efficient AlgART QuickBMPWriter
//            System.out.println("QUICK!!!");
            Matrix<? extends PArray> matrix = source.readImage(compression, fromX, fromY, toX, toY);
            return matrixToBMPBytes(matrix);
        } else {
//            System.out.println("SLOW!!!");
            BufferedImage bufferedImage = source.readBufferedImage(compression, fromX, fromY, toX, toY, converter);
            return bufferedImageToBytes(bufferedImage, renderingFormatName);
        }
    }

    @Override
    public PlanePyramidImageData readSpecialImage(PlanePyramidReadSpecialImageRequest specialImageRequest)
        throws IOException
    {
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
        final BufferedImage bufferedImage = specialImageConverter.toBufferedImage(m);
        return bufferedImageToBytes(bufferedImage, renderingFormatName);
    }

    @Override
    public boolean isRawBytes() {
        return rawBytes;
    }

    @Override
    public String format() {
        return renderingFormatName;
    }

    @Override
    public boolean isTimeout() {
        return System.currentTimeMillis() - lastAccessTime > PYRAMID_TIMEOUT;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public String toString() {
        return "Plane pyramid based on " + source + " (" + renderingFormatName + " format)";
    }

    private PlanePyramidImageData bufferedImageToBytes(BufferedImage bufferedImage, String formatName)
        throws IOException
    {
        if (!transparencySupported(formatName)) {
            bufferedImage = convertARGBtoBGR(bufferedImage, renderingBackgroundColor);
        }
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        if (!ImageIO.write(bufferedImage, formatName, stream)) {
            throw new IIOException(formatName + " image format is not supported for " + bufferedImage);
        }
        stream.flush();
        this.lastAccessTime = System.currentTimeMillis();
        return new PlanePyramidImageData(stream.toByteArray(), this);
    }

    private PlanePyramidImageData matrixToBMPBytes(Matrix<? extends PArray> matrix) {
        if (matrix.elementType() != byte.class) {
            double max = matrix.array().maxPossibleValue(1.0);
            matrix = Matrices.asFuncMatrix(LinearFunc.getInstance(0.0, 255.0 / max), ByteArray.class, matrix);
        }
        final byte[] data = (byte[]) Arrays.toJavaArray(matrix.array());
        final byte[] bytes = new QuickBMPWriter(
            (int) matrix.dim(1), (int) matrix.dim(2), (int) matrix.dim(0), data)
            .getBmpBytes();
        return new PlanePyramidImageData(bytes, this);
    }

    private static JsonObject actualAreasToJson(PlanePyramidSource source) {
        final List<IRectangularArea> actualRectangles = source.zeroLevelActualRectangles();
        final List<List<List<IPoint>>> actualAreaBoundaries = source.zeroLevelActualAreaBoundaries();
        if (actualRectangles == null && actualAreaBoundaries == null) {
            return null;
        }
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        if (actualRectangles != null) {
            final JsonArrayBuilder jsonRectangles = Json.createArrayBuilder();
            for (IRectangularArea r : actualRectangles) {
                final JsonObjectBuilder jsonRectangle = Json.createObjectBuilder();
                jsonRectangle.add("minX", r.min(0));
                jsonRectangle.add("minY", r.min(1));
                jsonRectangle.add("maxX", r.max(0));
                jsonRectangle.add("maxY", r.max(1));
                jsonRectangles.add(jsonRectangle);
            }
            builder.add("actualRectangles", jsonRectangles);
        }
        if (actualAreaBoundaries != null) {
            final JsonArrayBuilder jsonActualAreaBoundaries = Json.createArrayBuilder();
            for (List<List<IPoint>> area : actualAreaBoundaries) {
                final List<IPoint> externalBoundary = area.get(0);
                // Note: this class ignores possible holes in polygonal areas
                final JsonArrayBuilder jsonExternalBoundary = Json.createArrayBuilder();
                for (IPoint p : externalBoundary) {
                    final JsonObjectBuilder jsonPoint = Json.createObjectBuilder();
                    jsonPoint.add("x", p.x());
                    jsonPoint.add("y", p.y());
                    jsonExternalBoundary.add(jsonPoint);
                }
                jsonActualAreaBoundaries.add(jsonExternalBoundary);
            }
            builder.add("actualAreaBoundaries", jsonActualAreaBoundaries);
        }
        return builder.build();
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
