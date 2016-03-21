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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

enum RendererType {
    DEFAULT("Default") {
        @Override
        public RendererType toCompatible(int channelCount) {
            return this;
        }

        @Override
        public Color getBackgroundColor(String backgroundColor, boolean transparencySupported) {
            if (backgroundColor != null) {
                return Color.decode("#" + backgroundColor);
            } else {
                return transparencySupported ? TRANSPARENT_COLOR : Color.WHITE;
            }
        }

        @Override
        public MatrixToBufferedImageConverter getConverter(
            Color color,
            double opacity,
            boolean addAlpha)
        {
            return new MatrixToBufferedImageConverter.Packed3DToPackedRGB(addAlpha);
        }
    },

    ALPHA_0_GRAY("Alpha0Gray") {
        @Override
        public RendererType toCompatible(int channelCount) {
            return channelCount > 1 ? DEFAULT : this;
        }

        @Override
        public Color getBackgroundColor(String backgroundColor, boolean transparencySupported) {
            return ALPHA_0_BACKGROUND_COLOR;
        }

        @Override
        public MatrixToBufferedImageConverter getConverter(
            Color color,
            double opacity,
            boolean addAlpha)
        {
            if (opacity < 0.0 || opacity > 1.0) {
                throw new IllegalArgumentException("Illegal opacity " + opacity + " (must be in 0.0..1.0 range)");
            }
            final int opacity255 = (int) Math.round(opacity * 255.0);
            return new MatrixToBufferedImageConverter.MonochromeToIndexed(
                new double[] {0, 0, 0, 0},
                new double[] {1.0, 1.0, 1.0, 1.0}
            )
            {
                @Override
                public byte[][] palette() {
                    final byte[] intensity = new byte[256];
                    final byte[] a = new byte[256];
                    intensity[0] = 0;
                    a[0] = 0;
                    for (int i = 1; i < 256; i++) {
                        intensity[i] = (byte) (i & 0xff);
                        a[i] = (byte) opacity255;
                    }
                    return new byte[][] {intensity, intensity, intensity, a};
                }

                @Override
                protected void toDataBufferBand0Filter(byte[] src, int srcPos, byte[] dest) {
                    super.toDataBufferBand0Filter(src, srcPos, dest);
                    for (int i = 0; i < dest.length; i++) {
                        if (dest[i] == 0) {
                            dest[i] = 1;
                        }
                    }
                }
            };
        }
    },
    ALPHA_0_COLOR("Alpha0Color") {
        @Override
        public RendererType toCompatible(int channelCount) {
            return channelCount > 1 ? DEFAULT : this;
        }

        @Override
        public Color getBackgroundColor(String backgroundColor, boolean transparencySupported) {
            return ALPHA_0_BACKGROUND_COLOR;
        }

        @Override
        public MatrixToBufferedImageConverter getConverter(Color color, double opacity, boolean addAlpha) {
            if (opacity < 0.0 || opacity > 1.0) {
                throw new IllegalArgumentException("Illegal opacity " + opacity + " (must be in 0.0..1.0 range)");
            }
            final float[] rgb = color.getRGBColorComponents(null);
            return new MatrixToBufferedImageConverter.MonochromeToIndexed(
                new double[] {rgb[0], rgb[1], rgb[2], 0},
                new double[] {rgb[0], rgb[1], rgb[2], opacity}
            )
            {
                @Override
                public byte[][] palette() {
                    final byte[][] result = super.palette();
                    result[0][0] = 0;
                    result[1][0] = 0;
                    result[2][0] = 0;
                    result[3][0] = 0;
                    return result;
                }
            };
        }
    };

    private static final Color TRANSPARENT_COLOR = new Color(0, 0, 0, 0);
    private static final Color ALPHA_0_BACKGROUND_COLOR = new Color(0, 0, 0, 0);
    private static final Map<String, RendererType> JSON_NAMES = new HashMap<>();
    static {
        for (RendererType v : RendererType.values()) {
            JSON_NAMES.put(v.jsonName, v);
            JSON_NAMES.put(v.name(), v);
            // support of both names: UPPERCASE and user-friendly
        }
    }

    private final String jsonName;

    RendererType(String jsonName) {
        this.jsonName = jsonName;
    }

    public static RendererType parse(String name) {
        if (name == null) {
            return DEFAULT;
        }
        final RendererType result = JSON_NAMES.get(name);
        return result == null ? DEFAULT : result;
    }

    public abstract RendererType toCompatible(int channelCount);

    public abstract Color getBackgroundColor(String backgroundColor, boolean transparencySupported);

    public abstract MatrixToBufferedImageConverter getConverter(
        Color color,
        double opacity,
        boolean addAlpha);
}

