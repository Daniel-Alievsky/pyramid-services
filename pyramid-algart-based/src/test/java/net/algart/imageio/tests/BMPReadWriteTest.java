package net.algart.imageio.tests;

import net.algart.arrays.*;
import net.algart.external.BufferedImageToMatrixConverter;
import net.algart.external.MatrixToBufferedImageConverter;
import net.algart.imageio.QuickBMPReader;
import net.algart.imageio.QuickBMPWriter;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

class BMPReadWriteTest {

    private static final int NUMBER_OF_TESTS = 100;

    public static void main(String[] args) throws IOException {
        int startArgIndex = 0;
        boolean testBGR, testBGRA, testGrayscale;
        if (testBGR = startArgIndex < args.length && args[startArgIndex].equalsIgnoreCase("-BGR")) {
            startArgIndex++;
        }
        if (testBGRA = startArgIndex < args.length && args[startArgIndex].equalsIgnoreCase("-BGRA")) {
            startArgIndex++;
        }
        if (testGrayscale = startArgIndex < args.length && args[startArgIndex].equalsIgnoreCase("-grayscale")) {
            startArgIndex++;
        }
        if (args.length < startArgIndex + 2) {
            System.out.println("Usage:");
            System.out.println("    " + BMPReadWriteTest.class.getName()
                + " [-BGR] [-BGRA] [-grayscale] srcImageFile destFolder");
            return;
        }
        final File srcImageFile = new File(args[startArgIndex]);
        if (!srcImageFile.isFile()) {
            throw new FileNotFoundException("No file " + srcImageFile);
        }
        final File destFolder = new File(args[startArgIndex + 1]);
        final File destFileSource = new File(destFolder, "bufferedImage.bmp");
        final File destFileQuickBMPWriter = new File(destFolder, "quickBMPWriter.bmp");
        final File destFileQuickBMPReader = new File(destFolder, "quickBMPReader.bmp");
        destFolder.mkdirs();
        BufferedImage image;
        {
            long t1 = System.nanoTime();
            image = ImageIO.read(srcImageFile);
            long t2 = System.nanoTime();
            if (image == null) {
                throw new IIOException("Cannot read " + srcImageFile);
            }
            if (testBGR) {
                BufferedImage newImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                newImage.getGraphics().drawImage(image, 0, 0, null);
                image = newImage;
            } else if (testGrayscale) {
                BufferedImage newImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                newImage.getGraphics().drawImage(image, 0, 0, null);
                image = newImage;
            }
            System.out.printf(Locale.US, "Image %dx%d was read from %s in %.3f ms%n",
                image.getWidth(), image.getHeight(), srcImageFile, (t2 - t1) * 1e-6);
        }
        final Matrix<? extends UpdatablePArray> matrix = new BufferedImageToMatrixConverter.ToPacked3D(testBGRA)
            .toMatrix(image);
        if (matrix.elementType() != byte.class) {
            throw new IOException("Unsupported element type " + matrix.elementType() + ": bytes required");
        }
        System.out.printf("%s is loaded into AlgART matrix %s%n", srcImageFile, matrix);
        if (testBGRA && matrix.dim(0) != 4) {
            System.out.println("Warning: the source image has no alpha channel!");
        }
        final byte[] sourceData = (byte[]) Arrays.toJavaArray(matrix.array());

        byte[] bmpBytes = null;
        boolean success = false;
        for (int testCount = 0; testCount < NUMBER_OF_TESTS; testCount++) {
            long t1 = System.nanoTime();
            final ByteArrayOutputStream stream = new ByteArrayOutputStream();
            success = ImageIO.write(image, "BMP", stream);
            if (!success) {
                System.out.println("BMP image format is not supported for " + image);
                break;
            }
            stream.flush();
            bmpBytes = stream.toByteArray();
            long t2 = System.nanoTime();
            System.out.printf(Locale.US,
                "BufferedImage converted to %d bytes by AWT in %.3f ms, %.5f MB/sec%n",
                bmpBytes.length, (t2 - t1) * 1e-6, sourceData.length / 1048576.0 / ((t2 - t1) * 1e-9));
        }
        if (success) {
            Files.write(destFileSource.toPath(), bmpBytes);
            System.out.printf(Locale.US, "BMP is written into %s%n", destFileSource);
        } else {
            destFileSource.delete();
        }

        for (int testCount = 0; testCount < NUMBER_OF_TESTS; testCount++) {
            long t1 = System.nanoTime();
            final QuickBMPWriter writer = new QuickBMPWriter(
                (int) matrix.dim(1), (int) matrix.dim(2), (int) matrix.dim(0), sourceData);
            bmpBytes = writer.getBmpBytes();
            long t2 = System.nanoTime();
            System.out.printf(Locale.US,
                "AlgART matrix converted to %d bytes by QuickBMPWriter in %.6f ms, %.5f MB/sec (%s)%n",
                bmpBytes.length, (t2 - t1) * 1e-6, sourceData.length / 1048576.0 / ((t2 - t1) * 1e-9), writer);
        }
        Files.write(destFileQuickBMPWriter.toPath(), bmpBytes);
        System.out.printf(Locale.US, "BMP is written into %s%n", destFileQuickBMPWriter);

        byte[] packedBytes = null;
        for (int testCount = 0; testCount < NUMBER_OF_TESTS; testCount++) {
            final ByteArrayInputStream stream = new ByteArrayInputStream(bmpBytes);
            long t1 = System.nanoTime();
            final QuickBMPReader reader = new QuickBMPReader(stream);
            packedBytes = reader.getPackedBytes(null);
            long t2 = System.nanoTime();
            if (reader.getWidth() != matrix.dim(1)
                || reader.getHeight() != matrix.dim(2)
                || reader.getBandCount() != matrix.dim(0))
            {
                throw new AssertionError("Indalid image sizes after reading from BMP bytes (" + reader + ")");
            }
            System.out.printf(Locale.US,
                "%d bytes converted to AlgART matrix by QuickBMPReader in %.6f ms, %.5f MB/sec (%s)%n",
                bmpBytes.length, (t2 - t1) * 1e-6, sourceData.length / 1048576.0 / ((t2 - t1) * 1e-9), reader);
        }
        final Matrix<? extends UpdatablePArray> loadedMatrix =
            Matrices.matrix(SimpleMemoryModel.asUpdatableByteArray(packedBytes), matrix.dimensions());
        final BufferedImage loadedImage = new MatrixToBufferedImageConverter.Packed3DToPackedRGB(matrix.dim(0) == 4)
            .toBufferedImage(loadedMatrix);
        writeImage(loadedImage, "BMP", destFileQuickBMPReader);
        System.out.printf(Locale.US, "BMP is written into %s%n", destFileQuickBMPReader);
        if (!java.util.Arrays.equals(packedBytes, sourceData)) {
            throw new AssertionError("Loaded [A]RGB bytes differ from original!");
        }

        try {
            final QuickBMPReader reader = new QuickBMPReader(new FileInputStream(srcImageFile));
            System.out.printf(Locale.US, "Source file %s is read into %s%n", srcImageFile, reader);
        } catch (UnsupportedOperationException e) {
            System.out.printf(Locale.US, "Source file %s is not supported by QuickBMPReader:%n%s%n", srcImageFile, e);
        } catch (IOException e) {
            System.out.printf(Locale.US, "Source file %s cannot be read as BMP:%n%s%n", srcImageFile, e);
        }
    }

    private static void writeImage(BufferedImage image, String format, File file) throws IOException {
        if (!ImageIO.write(image, format, file)) {
            System.out.printf("Cannot write image into %s (%s)%n", file, image);
        }
    }
}
