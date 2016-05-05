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

package net.algart.pyramid;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.Reader;
import java.io.StringWriter;
import java.util.*;

public final class PlanePyramidInformation {
    private final int channelCount;
    private final long zeroLevelDimX;
    private final long zeroLevelDimY;
    private final Class<?> elementType;
    private volatile Double pixelSizeInMicrons = null;
    private volatile Double magnification = null;
    private volatile Set<String> existingSpecialImages = new LinkedHashSet<>();
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
        final JsonNumber pixelSizeInMicrons = json.getJsonNumber("pixelSizeInMicrons");
        if (pixelSizeInMicrons != null) {
            result.setPixelSizeInMicrons(pixelSizeInMicrons.doubleValue());
        }
        final JsonNumber magnification = json.getJsonNumber("magnification");
        if (magnification != null) {
            result.setMagnification(magnification.doubleValue());
        }
        result.setExistingSpecialImages(toStringList(getRequiredJsonArray(json, "existingSpecialImages")));
        final JsonString additionalMetadata = json.getJsonString("additionalMetadata");
        if (additionalMetadata != null) {
            result.setAdditionalMetadata(additionalMetadata.getString());
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

    private JsonObject toJson() {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("channelCount", channelCount);
        builder.add("zeroLevelDimX", zeroLevelDimX);
        builder.add("zeroLevelDimY", zeroLevelDimY);
        builder.add("elementType", elementType.toString());
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
        if (additionalMetadata != null) {
            builder.add("additionalMetadata", additionalMetadata);
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
}
