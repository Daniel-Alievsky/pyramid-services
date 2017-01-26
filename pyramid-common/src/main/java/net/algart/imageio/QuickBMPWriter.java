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

package net.algart.imageio;

public class QuickBMPWriter {
    static final int BMP_FILE_HEADER_LENGTH = 14;
    static final int BMP_INFO_VERSION_3_LENGTH = 40;
    static final int BMP_INFO_VERSION_4_LENGTH = 108;

    private final int width;
    private final int height;
    private final int bandCount;
    private final int step;
    private final int dataSize;
    private final int fileSize;
    private final byte[] data;
    private final byte[] bmpBytes;

    // Current version always converts grayscale data into rgb
    public QuickBMPWriter(int width, int height, int bandCount, byte[] data) {
        if (data == null) {
            throw new NullPointerException("Null data array");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Zero or negative width or height");
        }
        if (bandCount != 1 && bandCount != 3 && bandCount != 4) {
            throw new IllegalArgumentException("Unsupported band count = " + bandCount);
        }
        if (data.length != (long) width * (long) height * bandCount) {
            throw new IllegalArgumentException("Data array must have length width*height (grayscale) "
                + "or width*height*3 (rgb)");
        }
        this.width = width;
        this.height = height;
        this.bandCount = bandCount;
        // - note: this field must be filled for correct work of the following code
        final long longStep = ((long) width * bandCount + 3) / 4 * 4;
        if (longStep > Integer.MAX_VALUE || startDataOffset() + (long) height * longStep > Integer.MAX_VALUE) {
            // very improbable
            throw new IllegalArgumentException("Too large BMP: >=2^31 bytes");
        }
        this.step = (int) longStep;
        this.dataSize = height * step;
        this.fileSize = startDataOffset() + dataSize;
        this.data = data;
        this.bmpBytes = encodeBMP();
    }

    public byte[] getBmpBytes() {
        return bmpBytes;
    }

    @Override
    public String toString() {
        return "QuickBMPWriter: " +
            "width=" + width +
            ", height=" + height +
            ", bandCount=" + bandCount +
            ", dataSize=" + dataSize;
    }

    private int bmpInfoLength() {
        return bandCount == 4 ?
                BMP_INFO_VERSION_4_LENGTH :
                BMP_INFO_VERSION_3_LENGTH;
    }

    private boolean hasColorTable() {
        // really trivial gray-scale palette
        return bandCount == 1;
    }

    private int bmpColorTableSize() {
        return hasColorTable() ? 256 * 4 : 0;
    }

    private int startDataOffset() {
        return BMP_FILE_HEADER_LENGTH + bmpInfoLength() + bmpColorTableSize();
    }

    private byte[] encodeBMP() {
        // - zero-filled by Java
        final byte[] result = new byte[fileSize];
        final byte[] bmpFileHeader = bmpFileHeader();
        final byte[] bmpInfoAndColorTable = bmpInfoAndColorTable();
        System.arraycopy(bmpFileHeader, 0, result, 0, bmpFileHeader.length);
        System.arraycopy(bmpInfoAndColorTable, 0, result, bmpFileHeader.length, bmpInfoAndColorTable.length);
        switch (bandCount) {
            case 4:
                for (int y = 0; y < height; y++) {
                    int rgbOfs = (height - 1 - y) * bandCount * width;
                    final int rgbOfsNext = rgbOfs + bandCount * width;
                    int resultOfs = startDataOffset() + y * step;
                    for (; rgbOfs < rgbOfsNext; rgbOfs += bandCount, resultOfs += bandCount) {
                        result[resultOfs] = data[rgbOfs + 2];
                        result[resultOfs + 1] = data[rgbOfs + 1];
                        result[resultOfs + 2] = data[rgbOfs];
                        result[resultOfs + 3] = data[rgbOfs + 3];
                        // Windows standard software does not understand another order, for example,
                        // RGBA instead of BGRA, even if we specify it in channel masks
                    }
                }
                break;
            case 3: {
                for (int y = 0; y < height; y++) {
                    int rgbOfs = (height - 1 - y) * bandCount * width;
                    final int rgbOfsNext = rgbOfs + bandCount * width;
                    int resultOfs = startDataOffset() + y * step;
                    for (; rgbOfs < rgbOfsNext; rgbOfs += bandCount, resultOfs += 3) {
                        result[resultOfs] = data[rgbOfs + 2];
                        result[resultOfs + 1] = data[rgbOfs + 1];
                        result[resultOfs + 2] = data[rgbOfs];
                    }
                }
                break;
            }
            case 1: {
                for (int y = 0; y < height; y++) {
                    int rgbOfs = (height - 1 - y) * width;
                    final int rgbOfsNext = rgbOfs + width;
                    int resultOfs = startDataOffset() + y * step;
                    System.arraycopy(data, rgbOfs, result, resultOfs, width);
                }
                break;
            }
        }
        return result;
    }

    private byte[] bmpFileHeader() {
        final byte[] bmpFileHeader = new byte[BMP_FILE_HEADER_LENGTH];
        // - zero-filled by Java
        bmpFileHeader[0] = (byte) 'B';
        bmpFileHeader[1] = (byte) 'M';
        writeIntLE(bmpFileHeader, 0x2, fileSize);
        writeIntLE(bmpFileHeader, 0xA, startDataOffset());
        return bmpFileHeader;
    }

    private byte[] bmpInfoAndColorTable() {
        final byte[] info = new byte[bmpInfoLength() + bmpColorTableSize()];
        // - zero-filled by Java
        writeIntLE(info, 0x0, bmpInfoLength());
        writeIntLE(info, 0x4, width);
        writeIntLE(info, 0x8, height);
        info[0xC] = 1;  // number of color planes
        info[0xE] = (byte) (bandCount * 8); // bits per pixel
        writeIntLE(info, 0x10, bandCount == 4 ? 3 : 0); // no compression with bit masks BI_BITFIELDS / no compression
        writeIntLE(info, 0x14, dataSize);
        writeIntLE(info, 0x18, 0); // horizontral resolution (skipped)
        writeIntLE(info, 0x1C, 0); // vertical resolution (skipped)
        if (bandCount == 4) {
            writeIntLE(info, 0x28, 0x00FF0000); // channel mask for red
            writeIntLE(info, 0x2C, 0x0000FF00); // channel mask for green
            writeIntLE(info, 0x30, 0x000000FF); // channel mask for blue
            writeIntLE(info, 0x34, 0xFF000000); // channel mask for alpha
            writeIntLE(info, 0x38, 0x73524742); // color space: "sRGB"
        } else if (hasColorTable()) {
            writeIntLE(info, 0x20, 256); // used colors
            writeIntLE(info, 0x24, 0);   // important colors; 0 and 256 mean "all"
            for (int k = 0; k < 256; k++) {
                final int color = k | (k << 8) | (k << 16);
                // - grayscale; highest byte is reserved and must be 0
                writeIntLE(info, 0x28 + 4 * k, color);
            }
        }
        return info;
    }

    private static void writeIntLE(byte[] result, int offset, int value) {
        result[offset] = (byte) value;
        result[offset + 1] = (byte) (value >>> 8);
        result[offset + 2] = (byte) (value >>> 16);
        result[offset + 3] = (byte) (value >>> 24);
    }
}
