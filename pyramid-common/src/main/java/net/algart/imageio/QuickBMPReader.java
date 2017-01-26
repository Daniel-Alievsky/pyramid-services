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

package net.algart.imageio;

import javax.imageio.IIOException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

// In a case of unsupported version of BMP format, this class throws UnsupportedOperationException.
// In a case of format error, this class throws IIOException.
public class QuickBMPReader {
    private final InputStream inputStream;
    private int fileSize;
    private int dataOffset;
    private int width;
    private int height;
    private int bandCount;
    private int step;
    private int dataSize;
    private byte[] packedBMPData;

    public QuickBMPReader(InputStream inputStream) throws IOException, UnsupportedOperationException {
        if (inputStream == null) {
            throw new NullPointerException("Null inputStream");
        }
        this.inputStream = inputStream;
        decodeBMP();
    }


    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBandCount() {
        return bandCount;
    }

    public boolean isGrayscale() {
        return bandCount == 1;
    }

    public boolean hasAlpha() {
        return bandCount == 4;
    }

    public byte[] getRedBytes(byte[] result) {
        return getChannelBytes(result, 2);
    }

    public byte[] getGreenBytes(byte[] result) {
        return getChannelBytes(result, 1);
    }

    public byte[] getBlueBytes(byte[] result) {
        return getChannelBytes(result, 0);
    }

    public byte[] getAlphaBytes(byte[] result) {
        return getChannelBytes(result, 3);
    }

    public byte[] getGrayscaleBytes(byte[] result) {
        if (bandCount > 1) {
            throw new IllegalStateException("This image is not grayscale");
        }
        return getChannelBytes(result, 0);
    }

    public byte[] getPackedBytes(byte[] result) {
        return isGrayscale() ? getGrayscaleBytes(result) : hasAlpha() ? getRGBABytes(result) : getRGBBytes(result);
    }

    public byte[] getRGBBytes(byte[] result) {
        if (result == null) {
            result = new byte[3 * width * height];
        }
        for (int y = 0; y < height; y++) {
            int resultOfs = (height - 1 - y) * 3 * width;
            final int resultOfsNext = resultOfs + 3 * width;
            int bmpOfs = y * step;
            if (bandCount == 1) {
                for (; resultOfs < resultOfsNext; resultOfs += 3, bmpOfs++) {
                    result[resultOfs] = packedBMPData[bmpOfs];
                    result[resultOfs + 1] = packedBMPData[bmpOfs];
                    result[resultOfs + 2] = packedBMPData[bmpOfs];
                }
            } else {
                for (; resultOfs < resultOfsNext; resultOfs += 3, bmpOfs += bandCount) {
                    result[resultOfs] = packedBMPData[bmpOfs + 2];
                    result[resultOfs + 1] = packedBMPData[bmpOfs + 1];
                    result[resultOfs + 2] = packedBMPData[bmpOfs];
                }
            }
        }
        return result;
    }

    public byte[] getRGBABytes(byte[] result) {
        if (result == null) {
            result = new byte[4 * width * height];
        }
        for (int y = 0; y < height; y++) {
            int resultOfs = (height - 1 - y) * 4 * width;
            final int resultOfsNext = resultOfs + 4 * width;
            int bmpOfs = y * step;
            if (bandCount == 1) {
                for (; resultOfs < resultOfsNext; resultOfs += 4, bmpOfs++) {
                    result[resultOfs] = packedBMPData[bmpOfs];
                    result[resultOfs + 1] = packedBMPData[bmpOfs];
                    result[resultOfs + 2] = packedBMPData[bmpOfs];
                    result[resultOfs + 3] = (byte) 0xFF;
                }
            } else {
                for (; resultOfs < resultOfsNext; resultOfs += 4, bmpOfs += bandCount) {
                    result[resultOfs] = packedBMPData[bmpOfs + 2];
                    result[resultOfs + 1] = packedBMPData[bmpOfs + 1];
                    result[resultOfs + 2] = packedBMPData[bmpOfs];
                    result[resultOfs + 3] = bandCount == 4 ? packedBMPData[bmpOfs + 3] : (byte) 0xFF;
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "QuickBMPReader: " +
            "width=" + width +
            ", height=" + height +
            ", bandCount=" + bandCount +
            ", dataSize=" + dataSize
            + (packedBMPData.length != dataSize ? " (actually " + packedBMPData.length + ")" : "");
    }

    private byte[] getChannelBytes(byte[] result, int channel) {
        if (channel >= bandCount) {
            throw new IllegalArgumentException("No channel #" + channel + ": there are only "
                + bandCount + " channels");
        }
        if (result == null) {
            result = new byte[width * height];
        }
        for (int y = 0; y < height; y++) {
            int resultOfs = (height - 1 - y) * width;
            final int resultOfsNext = resultOfs + width;
            int bmpOfs = y * step;
            if (bandCount == 1) {
                System.arraycopy(packedBMPData, bmpOfs, result, resultOfs, width);
            } else {
                for (; resultOfs < resultOfsNext; resultOfs++, bmpOfs += bandCount) {
                    result[resultOfs] = packedBMPData[bmpOfs + channel];
                }
            }
        }
        return result;
    }

    private void decodeBMP() throws IOException {
        byte[] bmpFileHeaderAndInfoSize = new byte[QuickBMPWriter.BMP_FILE_HEADER_LENGTH + 4];
        readFully(bmpFileHeaderAndInfoSize);
        decodeFileHeader(bmpFileHeaderAndInfoSize);
        this.dataSize = this.fileSize - this.dataOffset;
        if (this.dataSize < 0) {
            throw new IIOException("Invalid BMP format (negative data size = " + dataSize + ")");
        }
        final int bmpInfoSize = decodeInfoSize(bmpFileHeaderAndInfoSize);
        byte[] bmpInfo = new byte[bmpInfoSize];
        readFully(bmpInfo, 4, bmpInfo.length - 4);
        // first 4 bytes are ignored
        decodeInfo(bmpInfoSize, bmpInfo);
        final int bmpColorTableSize = bmpColorTableSize();
        byte[] bmpColorTable = new byte[bmpColorTableSize];
        readFully(bmpColorTable);
        decodeColorTable(bmpColorTable);
        final int bmpHeaderPlusAllInfoSize =
            QuickBMPWriter.BMP_FILE_HEADER_LENGTH + bmpInfoSize + bmpColorTableSize;
        if (dataOffset < bmpHeaderPlusAllInfoSize) {
            throw new IIOException("Invalid BMP: data offset is less than the summary header size");
        }
        final long longStep = ((long) width * bandCount + 3) / 4 * 4;
        if (longStep > Integer.MAX_VALUE || dataOffset + (long) height * longStep > Integer.MAX_VALUE) {
            // very improbable
            throw new IIOException("Too large BMP: >=2^31 bytes");
        }
        this.step = (int) longStep;
        if (dataSize < height * step) {
            throw new IIOException("Too small BMP: data size < height * step");
        }
        skipBytes(dataOffset - bmpHeaderPlusAllInfoSize);
        this.packedBMPData = new byte[height * step];
        readFully(packedBMPData);
    }

    private void decodeFileHeader(byte[] bmpFileHeader) throws IIOException {
        if (bmpFileHeader[0] != (byte) 'B' || bmpFileHeader[1] != (byte) 'M') {
            throw new IIOException("Invalid BMP format (not BMP)");
        }
        this.fileSize = readIntLE(bmpFileHeader, 0x2);
        this.dataOffset = readIntLE(bmpFileHeader, 0xA);
    }

    private int decodeInfoSize(byte[] bmpFileHeaderAndInfoSize) {
        final int result = readIntLE(bmpFileHeaderAndInfoSize, 0xE);
        switch (result) {
            case QuickBMPWriter.BMP_INFO_VERSION_3_LENGTH:
            case QuickBMPWriter.BMP_INFO_VERSION_4_LENGTH:
                return result;
        }
        throw new UnsupportedOperationException("Unsupported BMP format: unknown BMP info length = " + result
            + "; only versions 3 and 4 are supported");
    }

    private void decodeInfo(int bmpInfoSize, byte[] bmpInfo) throws IIOException {
        this.width = readIntLE(bmpInfo, 0x4);
        this.height = readIntLE(bmpInfo, 0x8);
        if (this.height <= 0) {
            throw new UnsupportedOperationException("Negative height (top-down bitmap) is not supported");
        }
        final int numberOfPlanes = readWodr16LE(bmpInfo, 0xC);
        if (numberOfPlanes != 1) {
            throw new UnsupportedOperationException("Invalid number of color planes (must be 1)");
        }
        final int bitCount = readWodr16LE(bmpInfo, 0xE);
        switch (bitCount) {
            case 8:
            case 24:
            case 32: {
                bandCount = bitCount / 8;
                if (bandCount == 4 && bmpInfoSize < QuickBMPWriter.BMP_INFO_VERSION_4_LENGTH) {
                    throw new UnsupportedOperationException("Alpha channel is not supported for BMP version < 4");
                }
                break;
            }
            default:
                throw new UnsupportedOperationException("Unsupported bit count = " + bitCount
                    + " (only 8/24/32-bit images supported)");
        }
        final int compression = readIntLE(bmpInfo, 0x10);
        switch (bmpInfoSize) {
            case QuickBMPWriter.BMP_INFO_VERSION_3_LENGTH: {
                if (compression != 0) {
                    throw new UnsupportedOperationException("Unsupported compression = " + compression
                        + " for BMP version 3 (compression 0 required)");
                }
                break;
            }
            case QuickBMPWriter.BMP_INFO_VERSION_4_LENGTH: {
                if (compression != 0 && compression != 3) {
                    throw new UnsupportedOperationException("Unsupported compression = " + compression
                        + " for BMP version 4 (compression 0 or 3 required)");
                }
                break;
            }
        }
        // we ignore data size at 0x14 and further information of BMP version 3
        if (bandCount == 4) {
            assert bmpInfoSize >= QuickBMPWriter.BMP_INFO_VERSION_4_LENGTH;
            final int redMask = readIntLE(bmpInfo, 0x28);
            final int greenMask = readIntLE(bmpInfo, 0x2C);
            final int blueMask = readIntLE(bmpInfo, 0x30);
            final int alphaMask = readIntLE(bmpInfo, 0x34);
            final int colorSpace = readIntLE(bmpInfo, 0x38);
            if (colorSpace != 0x73524742) {
                throw new UnsupportedOperationException("Unsupported color space 0x"
                    + Integer.toHexString(colorSpace) + " (only sRGB is supported)");
            }
            if (!(redMask == 0x00FF0000 && greenMask == 0x0000FF00
                && blueMask == 0x000000FF && alphaMask == 0xFF000000))
            {
                throw new UnsupportedOperationException("Unsupported set of channels masks"
                    + " (only 0x00FF0000/0x0000FF00/0x000000FF/0x000000FF for R/G/B/A are supported)");
            }
        } else if (hasColorTable()) {
            final int colorsUsed = readIntLE(bmpInfo, 0x20);
            if (colorsUsed != 256 && colorsUsed != 0) {
                throw new UnsupportedOperationException("Unsupported number of colors " + colorsUsed
                    + " in the palette; required is 256 or 0 (default)");
            }
        }
    }

    private boolean hasColorTable() {
        // really trivial gray-scale palette
        return bandCount == 1;
    }

    private int bmpColorTableSize() {
        return hasColorTable() ? 256 * 4 : 0;
    }

    // This method in current version really does not decode anything,
    // it just checks that the table is grayscale
    private void decodeColorTable(byte[] bmpColorTable) {
        for (int k = 0, ofs = 0; ofs < bmpColorTable.length; k++, ofs += 4) {
            final int required = k | (k << 8) | (k << 16);
            // - grayscale; highest byte is reserved and must be 0
            final int actual = readIntLE(bmpColorTable, ofs);
            if ((actual & 0xFFFFFF) != required) {
                throw new UnsupportedOperationException("Unsupported color table: grayscale palette required,"
                    + " but the color #" + k + " is not grayscale (0x" + Integer.toHexString(actual) + ")");
            }
        }

    }

    public void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    // See DataInputStream.readFully
    private void readFully(byte b[], int off, int len) throws IOException {
        assert len >= 0;
        int n = 0;
        while (n < len) {
            int count = inputStream.read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException();
            }
            n += count;
        }
    }

    public final int skipBytes(int n) throws IOException {
        //TODO!! little more accurate (use readFully into temp buffer)
        int total = 0;
        int cur = 0;

        while ((total < n) && ((cur = (int) inputStream.skip(n - total)) > 0)) {
            total += cur;
        }
        return total;
    }

    private static int readWodr16LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
