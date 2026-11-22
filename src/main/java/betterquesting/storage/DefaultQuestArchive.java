package betterquesting.storage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;

/** Reads and writes the gzip distribution of the standard quest database JSON. */
public final class DefaultQuestArchive {

    public static final String FILE_NAME = "DefaultQuests.json.gz";
    private static final String SETTINGS = "questSettings:10";
    private static final Gson GSON = new Gson();

    private DefaultQuestArchive() {}

    public static NBTTagCompound read(Path path) throws IOException {
        try (BufferedReader reader = open(path)) {
            JsonObject document = JsonHelper.readObject(reader);
            validate(document);
            NBTTagCompound database = NBTConverter.readTypedCompound(document);
            NBTTagList quests = requireCompoundList(database, "questDatabase");
            requireCompoundList(database, "questLines");
            for (int index = 0; index < quests.tagCount(); index++) {
                NBTTagCompound quest = quests.getCompoundTagAt(index);
                if (!NBTConverter.UuidValueType.QUEST.tryReadId(quest)
                    .isPresent() && !quest.hasKey("questID", NBT.TAG_ANY_NUMERIC)) {
                    throw new IOException("Missing quest ID at questDatabase[" + index + "]");
                }
            }
            return database;
        }
    }

    public static NBTTagCompound readSettings(Path path) throws IOException {
        try (JsonReader reader = new JsonReader(open(path))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (SETTINGS.equals(reader.nextName())) {
                    JsonObject settings = GSON.fromJson(reader, JsonObject.class);
                    if (settings != null) return NBTConverter.readTypedCompound(settings);
                    break;
                }
                reader.skipValue();
            }
            throw new IOException("Missing settings in the default quest database");
        } catch (JsonParseException | IllegalStateException | NumberFormatException failure) {
            throw new IOException("Invalid default quest database settings", failure);
        }
    }

    public static void write(Path path, NBTTagCompound database) throws IOException {
        JsonObject document = NBTConverter.NBTtoJSON_Compound(database, new JsonObject(), true);
        validate(document);
        JsonObject settingsFirst = new JsonObject();
        settingsFirst.add(SETTINGS, document.remove(SETTINGS));
        for (Map.Entry<String, JsonElement> field : document.entrySet()) {
            settingsFirst.add(field.getKey(), field.getValue());
        }

        Path target = path.toAbsolutePath();
        Path staged = Files.createTempFile(
            target.getParent(),
            target.getFileName()
                .toString(),
            ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(staged)), StandardCharsets.UTF_8))) {
                GSON.toJson(settingsFirst, writer);
            }
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (JsonParseException failure) {
            throw new IOException("Could not write the default quest database", failure);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static BufferedReader open(Path path) throws IOException {
        java.io.InputStream input = Files.newInputStream(path);
        try {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static boolean isObject(JsonObject document, String name) {
        JsonElement value = document.get(name);
        return value != null && value.isJsonObject();
    }

    private static void validate(JsonObject database) throws IOException {
        if (database == null || !isObject(database, SETTINGS)
            || !isObject(database, "questDatabase:9")
            || !isObject(database, "questLines:9")) {
            throw new IOException("Missing sections in the default quest database");
        }
    }

    private static NBTTagList requireCompoundList(NBTTagCompound database, String key) throws IOException {
        if (database.getTag(key) instanceof NBTTagList list
            && (list.tagCount() == 0 || list.func_150303_d() == NBT.TAG_COMPOUND)) {
            return list;
        }
        throw new IOException(key + " must contain a list of objects");
    }
}
