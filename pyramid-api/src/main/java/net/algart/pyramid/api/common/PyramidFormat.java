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

package net.algart.pyramid.api.common;

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonString;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PyramidFormat implements Comparable<PyramidFormat> {
    private final String formatName;
    private final String fileRegExp;
    private final String fileInFolderRegExp;
    private final List<String> accompanyingFiles;
    private final int recognitionPriority;
    // - fileInFolderRegExp and accompanyingFiles are not used by current version of services,
    // but may be used by other systems like uploaders

    private PyramidFormat(
        String formatName,
        String fileRegExp,
        String fileInFolderRegExp,
        List<String> accompanyingFiles,
        int recognitionPriority)
    {
        this.formatName = Objects.requireNonNull(formatName, "Null formatName");
        this.fileRegExp = Objects.requireNonNull(fileRegExp, "Null fileRegExp");
        this.fileInFolderRegExp = fileInFolderRegExp;
        this.accompanyingFiles =  Objects.requireNonNull(accompanyingFiles, "Null accompanyingFiles");
        this.recognitionPriority = recognitionPriority;
    }

    public static PyramidFormat getInstance(JsonObject json) {
        final String formatName = getRequiredString(json,
            PyramidConstants.FORMAT_NAME_IN_PYRAMID_FACTORY_CONFIGURATION_JSON);
        final String fileRegExp = json.getString(
            PyramidConstants.FILE_REG_EXP_IN_PYRAMID_FACTORY_CONFIGURATION_JSON, "");
        final String fileInFolderRegExp = json.getString("fileInFolderRegExp", null);
        final JsonArray jsonAccompanyingFiles = json.getJsonArray("accompanyingFiles");
        final List<String> accompanyingFiles = new ArrayList<>();
        if (jsonAccompanyingFiles != null) {
            for (int k = 0, n = jsonAccompanyingFiles.size(); k < n; k++) {
                accompanyingFiles.add(jsonAccompanyingFiles.getString(k));
            }
        }
        final int recognitionPriority = json.getInt("recognitionPriority", 0);
        return new PyramidFormat(formatName, fileRegExp, fileInFolderRegExp, accompanyingFiles, recognitionPriority);
    }

    public String getFormatName() {
        return formatName;
    }

    /**
     * The regular expression, determining possible file names for this image format (without path).
     * In particular, it is used for automatic search of the pyramid file by {@link StandardPyramidDataConfiguration}
     * when {@link PyramidConstants#PYRAMID_DATA_CONFIG_FILE_NAME} configuration file is absent or incorrect.
     *
     * <p>Typical example: "(.*)(\.tif$|\.tiff)$". If this value is absent in JSON, the default value is "";
     * it means that there are no preferred file names for this format (format must be detected by other way).</p>
     *
     * <p>Note: regular expression must contain only lowercase characters (we always check the filenames
     * lowercase).</p>
     *
     * <p>Never returns <tt>null</tt>.</p>
     *
     * @return regular expression for possible file names, expected for this image format.
     */
    public String getFileRegExp() {
        return fileRegExp;
    }

    /**
     * The regular expression, that can be used (if present) for determining image format, represented by folder
     * (instead of usual file or pair file + folder). If the image is stored in a folder and this regexp is
     * not <tt>null</tt>, the folder must contain some files with names matched this regexp.
     *
     * <p>Note: regular expression must contain only lowercase characters (we always check the filenames
     * lowercase).</p>
     *
     * <p>Must return <tt>null</tt> if the main image file is not a folder.</p>
     *
     * @return regular expression for names of files, containing in the folder, representing this image format.
     */
    public String getFileInFolderRegExp() {
        return fileInFolderRegExp;
    }

    /**
     * List of file or folder names, that should be added to the main file, if this format is multi-file and
     * is not a single folder. These names usually contain replacement characters like "$1", because
     * they are used together with {@link #getFileRegExp()} as possible replacements.
     *
     * <p>Typical example for formats "file + folder": "$1", when {@link #getFileRegExp()} returns "(.*)\.xxx".
     * It means that the image is stored in a pair:<br>
     *     someFolder<br>
     *     someFolder.xxx</p>
     *
     * <p>Never returns <tt>null</tt>, but returns an empty list for single-file or single-folder formats.</p>
     *
     * <p>Not used by current version of the services.</p>
     *
     * @return names or replacements for all files that contain image data in addition to the mail image file.
     */
    public List<String> getAccompanyingFiles() {
        return Collections.unmodifiableList(accompanyingFiles);
    }

    /**
     * The priority of this format for recognition procedure.
     * For example, if we have some large <tt>.tiff</tt>-file, we can assign  the priority 500 to LOCI BioFormats
     * pyramid reader and 0 to the reader based on standard <tt>javax.imageio.ImageIO</tt> class.
     * Then, if the pyramid is written in some custom microscope format, supported by LOCI,
     * it will be detected correctly, but if it is a usual planar TIFF, not supported by LOCI (and LOCI library
     * will return error while attempt to read it), this file will be processed by built-in Java library.
     *
     * <p>Not used by current version of the services.</p>
     *
     * @return priority of this format for recognition procedure.
     */
    public int getRecognitionPriority() {
        return recognitionPriority;
    }

    public boolean matchesPath(Path pyramidDataFileOrFolder) {
        Objects.requireNonNull(pyramidDataFileOrFolder, "Null pyramidDataFileOrFolder");
        final String fileName = pyramidDataFileOrFolder.getFileName().toString().toLowerCase();
        return !fileRegExp.isEmpty() && Pattern.compile(fileRegExp).matcher(fileName).matches();
    }

    public boolean matchesFolder(Path pyramidDataFolder) throws IOException {
        Objects.requireNonNull(pyramidDataFolder, "Null pyramidDataFolder");
        if (!Files.isDirectory(pyramidDataFolder)) {
            return false;
        }
        final Pattern pattern = Pattern.compile(fileInFolderRegExp);
        try (final DirectoryStream<Path> files = Files.newDirectoryStream(pyramidDataFolder)) {
            for (Path file : files) {
                if (pattern.matcher(file.getFileName().toString().toLowerCase()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Path> accompanyingFiles(Path pyramidDataFileOrFolder) {
        Objects.requireNonNull(pyramidDataFileOrFolder, "Null pyramidDataFileOrFolder");
        if (!matchesPath(pyramidDataFileOrFolder)) {
            throw new IllegalArgumentException("pyramidDataFileOrFolder \"" + pyramidDataFileOrFolder
                + "\" does not match this " + this);
        }
        final String fileName = pyramidDataFileOrFolder.getFileName().toString().toLowerCase();
        final Matcher m = Pattern.compile(fileRegExp).matcher(fileName);
        final List<Path> result = new ArrayList<>();
        for (String replacement : accompanyingFiles) {
            final String accompanyingFileName = m.replaceFirst(replacement);
            result.add(pyramidDataFileOrFolder.getParent().resolve(accompanyingFileName));
        }
        return result;
    }

    @Override
    public String toString() {
        return "pyramid format \"" + formatName + "\", fileRegExp \"" + fileRegExp + "\""
            + (fileInFolderRegExp == null ? "" : ", fileInFolderRegExp \"" + fileInFolderRegExp + "\"")
            + (accompanyingFiles.isEmpty() ? "" : ", accompanyingFiles " + accompanyingFiles)
            + (recognitionPriority == 0 ? "" : ", recognition priority " + recognitionPriority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyramidFormat that = (PyramidFormat) o;
        if (recognitionPriority != that.recognitionPriority) {
            return false;
        }
        if (!formatName.equals(that.formatName)) {
            return false;
        }
        if (!fileRegExp.equals(that.fileRegExp)) {
            return false;
        }
        if (fileInFolderRegExp != null ? !fileInFolderRegExp.equals(that.fileInFolderRegExp)
            : that.fileInFolderRegExp != null)
        {
            return false;
        }
        return accompanyingFiles.equals(that.accompanyingFiles);
    }

    @Override
    public int hashCode() {
        int result = formatName.hashCode();
        result = 31 * result + fileRegExp.hashCode();
        result = 31 * result + (fileInFolderRegExp != null ? fileInFolderRegExp.hashCode() : 0);
        result = 31 * result + accompanyingFiles.hashCode();
        result = 31 * result + recognitionPriority;
        return result;
    }

    @Override
    public int compareTo(PyramidFormat o) {
        return recognitionPriority > o.recognitionPriority ? -1
            : recognitionPriority < o.recognitionPriority ? 1
            : formatName.compareTo(o.formatName);

    }

    static String getFileExtension(String fileName) {
        int p = fileName.lastIndexOf('.');
        if (p == -1) {
            return null;
        }
        return fileName.substring(p + 1);
    }

    private static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid factory configuration JSON: \"" + name
                + "\" value required <<<" + json + ">>>");
        }
        return result.getString();
    }
}
