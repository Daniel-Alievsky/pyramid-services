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

package net.algart.pyramid;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PlanePyramidInformation extends PlanePyramidData {
    private final int channelCount;
    private final long zeroLevelDimX;
    private final long zeroLevelDimY;
    private final Class<?> elementType;
    private volatile String pyramidFormatName = null;
    private volatile String renderingFormatName = null;
    private volatile Double pixelSizeInMicrons = null;
    private volatile Double magnification = null;
    private volatile Set<String> existingSpecialImages = new LinkedHashSet<>();
    private volatile String actualAreas = null;
    // - usually JSON
    private volatile String additionalMetadata = null;
    // - usually JSON

    public PlanePyramidInformation(
        int channelCount,
        long zeroLevelDimX,
        long zeroLevelDimY,
        Class<?> elementType)
    {
        Objects.requireNonNull(elementType, "Null elementType");
        if (channelCount <= 0) {
            throw new IllegalArgumentException("Zero or negative bandCount = " + channelCount);
        }
        if (zeroLevelDimX < 0) {
            throw new IllegalArgumentException("Negative zeroLevelDimX = " + zeroLevelDimX);
        }
        if (zeroLevelDimY < 0) {
            throw new IllegalArgumentException("Negative zeroLevelDimY = " + zeroLevelDimY);
        }
        this.channelCount = channelCount;
        this.zeroLevelDimX = zeroLevelDimX;
        this.zeroLevelDimY = zeroLevelDimY;
        this.elementType = elementType;
    }

    public static PlanePyramidInformation valueOf(Reader reader) {
        final JsonObject json;
        try (final JsonReader jsonReader = Json.createReader(reader)) {
            json = jsonReader.readObject();
        }
        final String elementTypeName = getRequiredString(json, "elementType");
        Class<?> elementType = elementTypeName.equals("boolean") ? boolean.class
            : elementTypeName.equals("char") ? char.class
            : elementTypeName.equals("byte") ? byte.class
            : elementTypeName.equals("short") ? short.class
            : elementTypeName.equals("int") ? int.class
            : elementTypeName.equals("long") ? long.class
            : elementTypeName.equals("float") ? float.class
            : elementTypeName.equals("double") ? double.class
            : null;
        if (elementType == null) {
            throw new JsonException("Invalid JSON: \"elementType\" value is not a name of primitive type (\""
                + elementTypeName + "\")");
        }
        final PlanePyramidInformation result = new PlanePyramidInformation(
            getRequiredInt(json, "channelCount"),
            getRequiredLong(json, "zeroLevelDimX"),
            getRequiredLong(json, "zeroLevelDimY"),
            elementType);
        final String pyramidFormatName = json.getString("pyramidFormatName", null);
        if (pyramidFormatName != null) {
            result.setPyramidFormatName(pyramidFormatName);
        }
        final String renderingFormatName = json.getString("renderingFormatName", null);
        if (renderingFormatName != null) {
            result.setRenderingFormatName(renderingFormatName);
        }
        final JsonNumber pixelSizeInMicrons = json.getJsonNumber("pixelSizeInMicrons");
        if (pixelSizeInMicrons != null) {
            result.setPixelSizeInMicrons(pixelSizeInMicrons.doubleValue());
        }
        final JsonNumber magnification = json.getJsonNumber("magnification");
        if (magnification != null) {
            result.setMagnification(magnification.doubleValue());
        }
        result.setExistingSpecialImages(toStringList(getRequiredJsonArray(json, "existingSpecialImages")));
        final JsonObject actualAreas = json.getJsonObject("actualAreas");
        if (actualAreas != null) {
            result.setActualAreas(actualAreas.toString());
        }
        final JsonObject additionalMetadata = json.getJsonObject("additionalMetadata");
        if (additionalMetadata != null) {
            result.setAdditionalMetadata(additionalMetadata.toString());
        }
        return result;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public long getZeroLevelDimX() {
        return zeroLevelDimX;
    }

    public long getZeroLevelDimY() {
        return zeroLevelDimY;
    }

    public Class<?> getElementType() {
        return elementType;
    }

    public String getRenderingFormatName() {
        return renderingFormatName;
    }

    public void setRenderingFormatName(String renderingFormatName) {
        this.renderingFormatName = renderingFormatName;
    }

    public String getPyramidFormatName() {
        return pyramidFormatName;
    }

    public void setPyramidFormatName(String pyramidFormatName) {
        this.pyramidFormatName = pyramidFormatName;
    }

    public Double getPixelSizeInMicrons() {
        return pixelSizeInMicrons;
    }

    public void setPixelSizeInMicrons(Double pixelSizeInMicrons) {
        if (pixelSizeInMicrons != null && pixelSizeInMicrons <= 0.0) {
            throw new IllegalArgumentException("Zero or negative pixelSizeInMicrons");
        }
        this.pixelSizeInMicrons = pixelSizeInMicrons;
    }

    public Double getMagnification() {
        return magnification;
    }

    public void setMagnification(Double magnification) {
        if (magnification != null && magnification <= 0.0) {
            throw new IllegalArgumentException("Zero or negative magnification");
        }
        this.magnification = magnification;
    }

    public Set<String> getExistingSpecialImages() {
        return Collections.unmodifiableSet(existingSpecialImages);
    }

    public void setExistingSpecialImages(Collection<String> existingSpecialImages) {
        Objects.requireNonNull(existingSpecialImages, "Null existingSpecialImages (use empty set instead");
        this.existingSpecialImages.clear();
        this.existingSpecialImages.addAll(existingSpecialImages);
    }

    public String getActualAreas() {
        return actualAreas;
    }

    public void setActualAreas(String actualAreas) {
        this.actualAreas = actualAreas;
    }

    /**
     * Returns additional metadata, usually JSON.
     *
     * @return additional metadata, usually JSON. May be <tt>null</tt>.
     */
    public String getAdditionalMetadata() {
        return additionalMetadata;
    }

    public void setAdditionalMetadata(String additionalMetadata) {
        this.additionalMetadata = additionalMetadata;
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public String toString() {
        JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(
            Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
        StringWriter stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = jsonWriterFactory.createWriter(stringWriter)) {
            jsonWriter.writeObject(toJson());
            return stringWriter.toString();
        }
    }

    @Override
    public boolean isShortString() {
        return true;
    }

    @Override
    public String getShortString() {
        return toJsonString();
    }

    @Override
    public byte[] getBytes() {
        return toJsonString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getContentMIMEType() {
        return "application/json; charset=utf-8";
        // - note that charset must be compatible with the charset used in getBytes method
    }

    @Override
    long estimatedMemoryInBytes() {
        return 256;
        // - some estimation for memory in Java heap, occupied by this object
    }

    private JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("channelCount", channelCount);
        builder.add("zeroLevelDimX", zeroLevelDimX);
        builder.add("zeroLevelDimY", zeroLevelDimY);
        builder.add("elementType", elementType.toString());
        if (pyramidFormatName != null) {
            builder.add("pyramidFormatName", pyramidFormatName);
        }
        if (renderingFormatName != null) {
            builder.add("renderingFormatName", renderingFormatName);
        }
        if (pixelSizeInMicrons != null) {
            builder.add("pixelSizeInMicrons", pixelSizeInMicrons);
        }
        if (magnification != null) {
            builder.add("magnification", magnification);
        }
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        for (String name : existingSpecialImages) {
            arrayBuilder.add(name);
        }
        builder.add("existingSpecialImages", arrayBuilder.build());
        if (actualAreas != null) {
            builder.add("actualAreas", toJson(actualAreas));
        }
        if (additionalMetadata != null) {
            builder.add("additionalMetadata", toJson(additionalMetadata));
        }
        return builder.build();
    }

    private  static String getRequiredString(JsonObject json, String name) {
        final JsonString result = json.getJsonString(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid information JSON: \"" + name + "\" value required");
        }
        return result.getString();
    }

    private static int getRequiredInt(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid information JSON: \"" + name + "\" value required");
        }
        return result.intValueExact();
    }

    private static long getRequiredLong(JsonObject json, String name) {
        final JsonNumber result = json.getJsonNumber(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid information JSON: \"" + name + "\" value required");
        }
        return result.longValueExact();
    }

    private static List<String> toStringList(JsonArray jsonArray) {
        final List<String> result = new ArrayList<>();
        for (JsonValue value : jsonArray) {
            result.add(((JsonString) value).getString());
        }
        return result;
    }

    private static JsonArray getRequiredJsonArray(JsonObject json, String name) {
        final JsonArray result = json.getJsonArray(name);
        if (result == null) {
            throw new JsonException("Invalid pyramid information JSON: \"" + name + "\" value required");
        }
        return result;
    }

    private static JsonObject toJson(String json) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
            return jsonReader.readObject();
        }
    }
}
