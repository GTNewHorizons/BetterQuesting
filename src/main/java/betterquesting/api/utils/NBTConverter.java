package betterquesting.api.utils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

import betterquesting.api.api.QuestingAPI;

public class NBTConverter {

    /**
     * Enum holding the different types of values that are persisted via UUID.
     */
    public enum UuidValueType {

        QUEST("questID"),
        QUEST_LINE("questLineID"),;

        private final String idFieldName;
        private final String highIdFieldName;
        private final String lowIdFieldName;

        UuidValueType(String idFieldName) {
            this.idFieldName = idFieldName;
            this.highIdFieldName = idFieldName + "High";
            this.lowIdFieldName = idFieldName + "Low";
        }

        public NBTTagCompound writeId(UUID uuid) {
            NBTTagCompound tag = new NBTTagCompound();
            writeId(uuid, tag);
            return tag;
        }

        public void writeId(UUID uuid, NBTTagCompound tag) {
            tag.setLong(highIdFieldName, uuid.getMostSignificantBits());
            tag.setLong(lowIdFieldName, uuid.getLeastSignificantBits());
        }

        public void tryWriteId(@Nullable UUID uuid, NBTTagCompound tag) {
            if (uuid != null) {
                writeId(uuid, tag);
            }
        }

        /** Use this method in cases where the player needs to edit the NBT manually. */
        public void writeIdString(@Nullable UUID uuid, NBTTagCompound tag) {
            tag.setString(idFieldName, uuid == null ? "" : UuidConverter.encodeUuid(uuid));
        }

        public NBTTagList writeIds(Collection<UUID> uuids) {
            NBTTagList tagList = new NBTTagList();
            uuids.forEach(uuid -> tagList.appendTag(writeId(uuid)));
            return tagList;
        }

        public Optional<UUID> tryReadId(NBTTagCompound tag) {
            if (tag.hasKey(highIdFieldName, 99) && tag.hasKey(lowIdFieldName, 99)) {
                return Optional.of(readId(tag));
            } else {
                return Optional.empty();
            }
        }

        public UUID readId(NBTTagCompound tag) {
            return new UUID(tag.getLong(highIdFieldName), tag.getLong(lowIdFieldName));
        }

        /** Use this method in cases where the player needs to edit the NBT manually. */
        public Optional<UUID> tryReadIdString(NBTTagCompound tag) {
            if (!tag.hasKey(idFieldName, Constants.NBT.TAG_STRING)) {
                return Optional.empty();
            }

            String questId = tag.getString(idFieldName);
            if (questId.isEmpty()) {
                return Optional.empty();
            }

            if (questId.length() > 24) {
                // Handle old data, from before we started encoding UUIDs.
                return Optional.of(UUID.fromString(questId));
            }

            return Optional.of(UuidConverter.decodeUuid(questId));
        }

        public List<UUID> readIds(NBTTagCompound tag, String key) {
            return readIds(tag.getTagList(key, Constants.NBT.TAG_COMPOUND));
        }

        public List<UUID> readIds(NBTTagList tagList) {
            return getTagList(tagList).stream()
                .map(NBTTagCompound.class::cast)
                .map(this::readId)
                .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static Field f_tagList;

    /**
     * Convert NBT tags to a JSON object
     */
    private static void NBTtoJSON_Base(NBTBase value, boolean format, JsonWriter out) throws IOException {
        if (value == null || value.getId() == 0) out.beginObject()
            .endObject();
        else if (value instanceof NBTPrimitive) out.value(NBTConverter.getNumber(value));
        else if (value instanceof NBTTagString) out.value(((NBTTagString) value).func_150285_a_());
        else if (value instanceof NBTTagByteArray) {
            out.beginArray();
            for (byte b : ((NBTTagByteArray) value).func_150292_c()) {
                out.value(b);
            }
            out.endArray();
        } else if (value instanceof NBTTagIntArray) {
            out.beginArray();
            for (int b : ((NBTTagIntArray) value).func_150302_c()) {
                out.value(b);
            }
            out.endArray();
        } else if (value instanceof NBTTagList) {
            List<NBTBase> tagList = getTagList((NBTTagList) value);
            if (format) {
                out.beginObject();
                for (int i = 0; i < tagList.size(); i++) {
                    NBTBase tag = tagList.get(i);
                    out.name(i + ":" + tag.getId());
                    NBTtoJSON_Base(tag, true, out);
                }
                out.endObject();
            } else {
                out.beginArray();
                for (NBTBase tag : tagList) {
                    NBTtoJSON_Base(tag, false, out);
                }
            }
        } else if (value instanceof NBTTagCompound) {
            NBTtoJSON_Compound((NBTTagCompound) value, out, format);
        } else {
            // idk man what is this
            out.beginObject()
                .endObject();
        }
    }

    @SuppressWarnings("unchecked")
    public static void NBTtoJSON_Compound(NBTTagCompound parent, JsonWriter out, boolean format) throws IOException {
        out.beginObject();

        if (parent != null) for (String key : (Set<String>) parent.func_150296_c()) {
            NBTBase tag = parent.getTag(key);

            if (format) {
                out.name(key + ":" + tag.getId());
                NBTtoJSON_Base(tag, true, out);
            } else {
                out.name(key);
                NBTtoJSON_Base(tag, false, out);
            }
        }
        out.endObject();
    }

    /**
     * Convert NBT tags to a JSON object
     */
    private static JsonElement NBTtoJSON_Base(NBTBase tag, boolean format) {
        if (tag == null) {
            return new JsonObject();
        }

        if (tag.getId() >= 1 && tag.getId() <= 6) {
            return new JsonPrimitive(getNumber(tag));
        }
        if (tag instanceof NBTTagString) {
            return new JsonPrimitive(((NBTTagString) tag).func_150285_a_());
        } else if (tag instanceof NBTTagCompound) {
            return NBTtoJSON_Compound((NBTTagCompound) tag, new JsonObject(), format);
        } else if (tag instanceof NBTTagList) {
            if (format) {
                JsonObject jAry = new JsonObject();

                List<NBTBase> tagList = getTagList((NBTTagList) tag);

                for (int i = 0; i < tagList.size(); i++) {
                    jAry.add(
                        i + ":"
                            + tagList.get(i)
                                .getId(),
                        NBTtoJSON_Base(tagList.get(i), true));
                }

                return jAry;
            } else {
                JsonArray jAry = new JsonArray();

                List<NBTBase> tagList = getTagList((NBTTagList) tag);

                for (NBTBase t : tagList) {
                    jAry.add(NBTtoJSON_Base(t, false));
                }

                return jAry;
            }
        } else if (tag instanceof NBTTagByteArray) {
            JsonArray jAry = new JsonArray();

            for (byte b : ((NBTTagByteArray) tag).func_150292_c()) {
                jAry.add(new JsonPrimitive(b));
            }

            return jAry;
        } else if (tag instanceof NBTTagIntArray) {
            JsonArray jAry = new JsonArray();

            for (int i : ((NBTTagIntArray) tag).func_150302_c()) {
                jAry.add(new JsonPrimitive(i));
            }

            return jAry;
        } else {
            return new JsonObject(); // No valid types found. We'll just return this to prevent a NPE
        }
    }

    @SuppressWarnings("unchecked")
    public static JsonObject NBTtoJSON_Compound(NBTTagCompound parent, JsonObject jObj, boolean format) {
        if (parent == null) {
            return jObj;
        }

        final TreeSet<String> sortedKeys = new TreeSet<>(parent.func_150296_c());
        for (String key : sortedKeys) {
            NBTBase tag = parent.getTag(key);

            if (format) {
                jObj.add(key + ":" + tag.getId(), NBTtoJSON_Base(tag, true));
            } else {
                jObj.add(key, NBTtoJSON_Base(tag, false));
            }
        }

        return jObj;
    }

    /**
     * Convert JsonObject to a NBTTagCompound
     */
    public static NBTTagCompound JSONtoNBT_Object(JsonObject jObj, NBTTagCompound tags, boolean format) {
        return readCompound(jObj, tags, format, null);
    }

    /** Converts typed JSON without discarding malformed fields. */
    public static NBTTagCompound readTypedCompound(JsonObject json) throws IOException {
        try {
            return readCompound(json, new NBTTagCompound(), true, "$");
        } catch (IllegalArgumentException failure) {
            throw new IOException(failure.getMessage(), failure);
        }
    }

    private static NBTTagCompound readCompound(JsonObject jObj, NBTTagCompound tags, boolean format, String path) {
        if (jObj == null) {
            return tags;
        }

        for (Entry<String, JsonElement> entry : jObj.entrySet()) {
            String key = entry.getKey();

            if (!format) {
                tags.setTag(key, readElement(entry.getValue(), (byte) 0, false, null));
            } else {
                // Optimized key parsing without String.split()
                byte id = 0;
                String keyToUse = key;

                try {
                    int lastColonIndex = key.lastIndexOf(':');
                    if (lastColonIndex != -1) {
                        id = Byte.parseByte(key.substring(lastColonIndex + 1));
                        keyToUse = key.substring(0, lastColonIndex); // Simple colon cut
                    }
                } catch (Exception e) { // Catch all exceptions
                    // Invalid ID format, use original key and id=0
                    if (tags.hasKey(key) && path == null) {
                        QuestingAPI.getLogger()
                            .log(Level.WARN, "JSON/NBT formatting conflict on key '" + key + "'. Skipping...");
                        continue;
                    }
                }

                if (path != null && tags.hasKey(keyToUse)) {
                    throw new IllegalArgumentException(path + "." + key + ": duplicate NBT key");
                }
                tags.setTag(keyToUse, readElement(entry.getValue(), id, true, path == null ? null : path + "." + key));
            }
        }

        return tags;
    }

    /**
     * Tries to interpret the tagID from the JsonElement's contents
     */
    private static NBTBase readElement(JsonElement jObj, byte id, boolean format, String path) {
        if (jObj == null) {
            return new NBTTagString();
        }

        byte tagID = id <= 0 ? fallbackTagID(jObj) : id;

        try {
            if (tagID == 1 && (id <= 0 || jObj.getAsJsonPrimitive()
                .isBoolean())) // Edge case for BQ2 legacy files
            {
                return new NBTTagByte(jObj.getAsBoolean() ? (byte) 1 : (byte) 0);
            } else if (tagID >= 1 && tagID <= 6) {
                return path == null ? instanceNumber(jObj.getAsNumber(), tagID) : readStrictNumber(jObj, tagID);
            } else if (tagID == 8) {
                if (path != null && !jObj.getAsJsonPrimitive()
                    .isString()) {
                    throw new IllegalArgumentException("expected a string");
                }
                return new NBTTagString(jObj.getAsString());
            } else if (tagID == 10) {
                return readCompound(jObj.getAsJsonObject(), new NBTTagCompound(), format, path);
            } else if (tagID == 7) // Byte array
            {
                JsonArray jAry = jObj.getAsJsonArray();

                byte[] bAry = new byte[jAry.size()];

                for (int i = 0; i < jAry.size(); i++) {
                    bAry[i] = path == null ? jAry.get(i)
                        .getAsByte()
                        : ((NBTTagByte) readElement(jAry.get(i), (byte) 1, format, path + "[" + i + "]"))
                            .func_150290_f();
                }

                return new NBTTagByteArray(bAry);
            } else if (tagID == 11) {
                JsonArray jAry = jObj.getAsJsonArray();

                int[] iAry = new int[jAry.size()];

                for (int i = 0; i < jAry.size(); i++) {
                    iAry[i] = path == null ? jAry.get(i)
                        .getAsInt()
                        : ((NBTTagInt) readElement(jAry.get(i), (byte) 3, format, path + "[" + i + "]"))
                            .func_150287_d();
                }

                return new NBTTagIntArray(iAry);
            } else if (tagID == 9) {
                NBTTagList tList = new NBTTagList();

                if (jObj.isJsonArray()) {
                    JsonArray jAry = jObj.getAsJsonArray();
                    for (int i = 0; i < jAry.size(); i++) {
                        appendListElement(
                            tList,
                            jAry.get(i),
                            (byte) 0,
                            format,
                            path == null ? null : path + "[" + i + "]");
                    }
                } else if (jObj.isJsonObject()) {
                    JsonObject jAry = jObj.getAsJsonObject();

                    for (Entry<String, JsonElement> entry : jAry.entrySet()) {
                        String key = entry.getKey();
                        byte elementType = 0;
                        int suffix = key.lastIndexOf(':');
                        if (suffix >= 0) {
                            try {
                                elementType = Byte.parseByte(key.substring(suffix + 1));
                            } catch (NumberFormatException ignored) {
                                // Historical lists can also contain untyped entries
                            }
                        }
                        appendListElement(
                            tList,
                            entry.getValue(),
                            elementType,
                            format,
                            path == null ? null : path + "[" + key + "]");
                    }
                }

                if (path != null && !jObj.isJsonArray() && !jObj.isJsonObject()) {
                    throw new IllegalArgumentException("expected a list");
                }
                return tList;
            }
        } catch (Exception e) {
            if (path != null) {
                if (e instanceof InvalidNBTException invalid) throw invalid;
                String type = tagID > 0 && tagID < NBTBase.NBTTypes.length ? NBTBase.NBTTypes[tagID]
                    : Byte.toString(tagID);
                throw new InvalidNBTException(path, "invalid value for NBT type " + type, e);
            }
            QuestingAPI.getLogger()
                .log(Level.ERROR, "An error occured while parsing JsonElement to NBTBase (" + tagID + "):", e);
        }

        if (path != null) throw new InvalidNBTException(path, "unknown NBT type " + tagID, null);
        QuestingAPI.getLogger()
            .log(Level.WARN, "Unknown NBT representation for " + jObj.toString() + " (ID: " + tagID + ")");
        return new NBTTagString();
    }

    private static void appendListElement(NBTTagList list, JsonElement json, byte type, boolean format, String path) {
        NBTBase element = readElement(json, type, format, path);
        if (path != null && list.tagCount() != 0 && list.func_150303_d() != element.getId()) {
            throw new InvalidNBTException(path, "mixed NBT list element types", null);
        }
        list.appendTag(element);
    }

    private static NBTBase readStrictNumber(JsonElement json, byte type) {
        String number = json.getAsJsonPrimitive()
            .getAsString();
        if (type == 5 || type == 6) {
            NBTBase result = instanceNumber(json.getAsNumber(), type);
            if (!Double.isFinite(getNumber(result).doubleValue())) {
                throw new IllegalArgumentException("expected a finite number");
            }
            return result;
        }

        long value;
        try {
            value = Long.parseLong(number);
        } catch (NumberFormatException failure) {
            value = new BigDecimal(number).longValueExact();
        }
        NBTBase result = instanceNumber(value, type);
        if (getNumber(result).longValue() != value) {
            throw new IllegalArgumentException("value is outside the range of NBT type " + type);
        }
        return result;
    }

    private static final class InvalidNBTException extends IllegalArgumentException {

        private InvalidNBTException(String path, String reason, Throwable cause) {
            super(path + ": " + reason, cause);
        }
    }

    /**
     * Pulls the raw list out of the NBTTagList
     */
    @SuppressWarnings("unchecked")
    public static List<NBTBase> getTagList(NBTTagList tag) {
        try {
            return (ArrayList<NBTBase>) f_tagList.get(tag);
        } catch (IllegalAccessException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static Number getNumber(NBTBase tag) {
        if (tag instanceof NBTTagByte) {
            return ((NBTTagByte) tag).func_150290_f();
        } else if (tag instanceof NBTTagShort) {
            return ((NBTTagShort) tag).func_150289_e();
        } else if (tag instanceof NBTTagInt) {
            return ((NBTTagInt) tag).func_150287_d();
        } else if (tag instanceof NBTTagFloat) {
            return ((NBTTagFloat) tag).func_150288_h();
        } else if (tag instanceof NBTTagDouble) {
            return ((NBTTagDouble) tag).func_150286_g();
        } else if (tag instanceof NBTTagLong) {
            return ((NBTTagLong) tag).func_150291_c();
        } else {
            return 0;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static NBTBase instanceNumber(Number num, byte type) {
        switch (type) {
            case 1:
                return new NBTTagByte(num.byteValue());
            case 2:
                return new NBTTagShort(num.shortValue());
            case 3:
                return new NBTTagInt(num.intValue());
            case 4:
                return new NBTTagLong(num.longValue());
            case 5:
                return new NBTTagFloat(num.floatValue());
            default:
                return new NBTTagDouble(num.doubleValue());
        }
    }

    private static byte fallbackTagID(JsonElement jObj) {
        byte tagID = 0;

        if (jObj.isJsonPrimitive()) {
            JsonPrimitive prim = jObj.getAsJsonPrimitive();

            if (prim.isNumber()) {
                if (prim.getAsString()
                    .contains(".")) // Just in case we'll choose the largest possible container supporting this number
                // type (Long or Double)
                {
                    tagID = 6;
                } else {
                    tagID = 4;
                }
            } else if (prim.isBoolean()) {
                tagID = 1;
            } else {
                tagID = 8; // Non-number primitive. Assume string
            }
        } else if (jObj.isJsonArray()) {
            JsonArray array = jObj.getAsJsonArray();

            for (JsonElement entry : array) {
                if (entry.isJsonPrimitive() && tagID == 0) // Note: TagLists can only support Integers, Bytes and
                // Compounds (Strings can be stored but require special
                // handling)
                {
                    try {
                        for (JsonElement element : array) {
                            // Make sure all entries can be bytes
                            if (element.getAsLong() != element.getAsByte()) // In case casting works but overflows
                            {
                                throw new ClassCastException();
                            }
                        }
                        tagID = 7; // Can be used as byte
                    } catch (Exception e1) {
                        try {
                            for (JsonElement element : array) {
                                // Make sure all entries can be integers
                                if (element.getAsLong() != element.getAsInt()) // In case casting works but overflows
                                {
                                    throw new ClassCastException();
                                }
                            }
                            tagID = 11;
                        } catch (Exception e2) {
                            tagID = 9; // Is primitive however requires TagList interpretation
                        }
                    }
                } else if (!entry.isJsonPrimitive()) {
                    break;
                }
            }

            tagID = 9; // No data to judge format. Assuming tag list
        } else {
            tagID = 10;
        }

        return tagID;
    }

    static {
        try {
            // noinspection JavaReflectionMemberAccess
            f_tagList = NBTTagList.class.getDeclaredField("field_74747_a");
            f_tagList.setAccessible(true);
        } catch (Exception e1) {
            try {
                f_tagList = NBTTagList.class.getDeclaredField("tagList");
                f_tagList.setAccessible(true);
            } catch (Exception e2) {
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Unable to hook into NBTTagList!", e2);
            }
        }
    }
}
