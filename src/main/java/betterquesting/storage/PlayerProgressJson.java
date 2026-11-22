package betterquesting.storage;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants.NBT;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.NBTConverter.UuidValueType;
import betterquesting.api.utils.UuidConverter;

/**
 * Stores one player's progress as editable JSON. The reader obtains the player UUID
 * from the filename for compact records. Typed NBT preserves fields owned by add-ons.
 */
public final class PlayerProgressJson {

    private static final int VERSION = 3;
    private static final String INDENT = "  ";
    private static final Set<String> QUEST_KEYS = keys("questID", "questIDHigh", "questIDLow", "completed", "tasks");
    private static final Set<String> COMPLETION_KEYS = keys("uuid", "timestamp", "claimed");
    private static final Set<String> TASK_KEYS = keys("taskID", "index", "completeUsers", "userProgress");
    private static final Set<String> ARRAY_TASKS = keys(
        "bq_standard:retrieval",
        "bq_standard:optional_retrieval",
        "bq_standard:crafting",
        "bq_standard:fluid",
        "bq_standard:block_break");
    private static final Set<String> INTEGER_TASKS = keys(
        "bq_standard:hunt",
        "bq_standard:interact_item",
        "bq_standard:interact_entity");
    private static final String XP_TASK = "bq_standard:xp";
    private static final Set<String> COMPLETION_TASKS = keys(
        "bq_standard:checkbox",
        "bq_standard:location",
        "bq_standard:meeting",
        "bq_standard:scoreboard");

    private PlayerProgressJson() {}

    public static NBTTagList read(Reader reader, UUID player) throws IOException {
        return readRecord(reader, player).progress();
    }

    public static ReadResult readRecord(Reader reader, UUID player) throws IOException {
        try {
            JsonObject data = JsonHelper.readObject(reader);
            if (!data.has("version")) {
                return finishRead(readTyped(data), true);
            }

            require(integer(data.get("version")) == VERSION, "unsupported progress version");
            if (data.has("data")) {
                require(
                    data.entrySet()
                        .size() == 2,
                    "invalid typed progress fields");
                return finishRead(readTyped(data.getAsJsonObject("data")), false);
            }
            require(
                data.entrySet()
                    .size() == 3 && data.has("taskTypes")
                    && data.has("quests"),
                "invalid progress fields");
            List<String> types = new ArrayList<>();
            for (JsonElement type : data.getAsJsonArray("taskTypes")) {
                require(
                    type.isJsonPrimitive() && type.getAsJsonPrimitive()
                        .isString(),
                    "invalid task type");
                types.add(type.getAsString());
            }
            NBTTagList result = new NBTTagList();
            String owner = player.toString();
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("quests")
                .entrySet()) {
                result.appendTag(
                    readQuest(
                        entry.getKey(),
                        entry.getValue()
                            .getAsJsonObject(),
                        types,
                        owner));
            }
            return finishRead(result, false);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid player quest progress: " + failure.getMessage(), failure);
        }
    }

    private static ReadResult finishRead(NBTTagList progress, boolean needsRewrite) throws IOException {
        // Keep reading numeric IDs until supported per-player saves have been rewritten with UUIDs
        for (int index = 0; index < progress.tagCount(); index++) {
            NBTTagCompound quest = progress.getCompoundTagAt(index);
            if (!quest.hasKey("questID", NBT.TAG_INT) || UuidValueType.QUEST.tryReadId(quest)
                .isPresent()) continue;

            int legacyId = quest.getInteger("questID");
            require(legacyId >= 0, "invalid numeric quest ID: " + legacyId);
            UuidValueType.QUEST.writeId(UuidConverter.convertLegacyId(legacyId), quest);
            quest.removeTag("questID");
            needsRewrite = true;
        }
        return new ReadResult(progress, needsRewrite);
    }

    private static NBTTagList readTyped(JsonObject data) throws IOException {
        String listKey = data.has("questProgress:9") ? "questProgress:9" : "questProgress";
        for (Map.Entry<String, JsonElement> field : data.entrySet()) {
            if (!listKey.equals(field.getKey())) {
                throw new IOException("Unexpected player progress field $." + field.getKey());
            }
        }
        JsonElement value = data.get(listKey);
        require(value != null && (value.isJsonObject() || value.isJsonArray()), "missing progress list");
        int recordCount = value.isJsonObject() ? value.getAsJsonObject()
            .entrySet()
            .size()
            : value.getAsJsonArray()
                .size();
        NBTTagCompound typed = NBTConverter.readTypedCompound(data);
        require(
            isOptionalList(typed, "questProgress", NBT.TAG_COMPOUND) && typed.hasKey("questProgress"),
            "invalid typed progress list");
        NBTTagList progress = typed.getTagList("questProgress", NBT.TAG_COMPOUND);
        require(progress.tagCount() == recordCount, "invalid typed quest record");
        return progress;
    }

    private static NBTTagCompound readQuest(String id, JsonObject record, List<String> types, String owner)
        throws IOException {
        NBTTagCompound quest = new NBTTagCompound();
        writeQuestId(id, quest);
        require(id.equals(questId(quest)), "invalid quest ID");
        if (record.has("data")) {
            require(
                record.entrySet()
                    .size() == 1,
                "invalid typed quest fields");
            NBTTagCompound typed = NBTConverter.readTypedCompound(record.getAsJsonObject("data"));
            require(id.equals(questId(typed)), "typed quest ID does not match key");
            return typed;
        }
        require(!(record.has("completedAt") && record.has("claimedAt")), "conflicting completion state");
        for (Map.Entry<String, JsonElement> entry : record.entrySet()) {
            switch (entry.getKey()) {
                case "completedAt":
                case "claimedAt":
                    NBTTagCompound completion = new NBTTagCompound();
                    completion.setString("uuid", owner);
                    completion.setLong("timestamp", wholeNumber(entry.getValue()));
                    completion.setBoolean(
                        "claimed",
                        entry.getKey()
                            .equals("claimedAt"));
                    NBTTagList completed = new NBTTagList();
                    completed.appendTag(completion);
                    quest.setTag("completed", completed);
                    break;
                case "tasks":
                    NBTTagList tasks = new NBTTagList();
                    for (Map.Entry<String, JsonElement> task : entry.getValue()
                        .getAsJsonObject()
                        .entrySet()) {
                        int index = Integer.parseInt(task.getKey());
                        require(
                            Integer.toString(index)
                                .equals(task.getKey()),
                            "invalid task index");
                        tasks.appendTag(readTask(index, task.getValue(), types, owner));
                    }
                    quest.setTag("tasks", tasks);
                    break;
                default:
                    throw new IOException("Unknown compact quest field");
            }
        }
        return quest;
    }

    private static NBTTagCompound readTask(int index, JsonElement entry, List<String> types, String owner)
        throws IOException {
        if (entry.isJsonObject()) {
            NBTTagCompound task = NBTConverter.readTypedCompound(entry.getAsJsonObject());
            require(
                task.hasKey("index", NBT.TAG_INT) && task.getInteger("index") == index,
                "typed task index does not match key");
            require(task.hasKey("taskID", NBT.TAG_STRING), "typed task is missing type");
            return task;
        }
        JsonArray tuple = entry.getAsJsonArray();
        require(tuple.size() >= 2 && tuple.size() <= 3, "invalid task tuple");
        String type = types.get(integer(tuple.get(0)));
        require(isKnownTask(type), "unknown compact task type");
        int done = integer(tuple.get(1));
        require(done == 0 || done == 1, "invalid task completion state");
        NBTTagCompound task = new NBTTagCompound();
        task.setInteger("index", index);
        task.setString("taskID", type);
        if (done == 1) {
            NBTTagList completed = new NBTTagList();
            completed.appendTag(new NBTTagString(owner));
            task.setTag("completeUsers", completed);
        }
        if (tuple.size() == 3) {
            NBTTagCompound progress = new NBTTagCompound();
            progress.setString("uuid", owner);
            if (ARRAY_TASKS.contains(type)) {
                JsonArray array = tuple.get(2)
                    .getAsJsonArray();
                int[] counters = new int[array.size()];
                for (int i = 0; i < counters.length; i++) counters[i] = integer(array.get(i));
                progress.setIntArray("data", counters);
            } else if (XP_TASK.equals(type)) {
                progress.setLong("value", wholeNumber(tuple.get(2)));
            } else {
                require(INTEGER_TASKS.contains(type), "unexpected task counters");
                progress.setInteger("value", integer(tuple.get(2)));
            }
            NBTTagList progressList = new NBTTagList();
            progressList.appendTag(progress);
            task.setTag("userProgress", progressList);
        }
        return task;
    }

    public static void write(JsonWriter writer, NBTTagList progress, UUID player) throws IOException {
        String owner = player.toString();
        Map<String, NBTTagCompound> quests = new LinkedHashMap<>();
        Map<String, Integer> types = new LinkedHashMap<>();
        for (int i = 0; i < progress.tagCount(); i++) {
            NBTTagCompound quest = progress.getCompoundTagAt(i);
            String id = questId(quest);
            if (id == null || quests.put(id, quest) != null) {
                NBTTagCompound legacy = new NBTTagCompound();
                legacy.setTag("questProgress", progress);
                writer.beginObject();
                writer.name("version")
                    .value(VERSION);
                writer.name("data");
                NBTConverter.NBTtoJSON_Compound(legacy, writer, true);
                writer.endObject();
                return;
            }
            if (!compactQuest(quest, owner)) continue;
            NBTTagList tasks = quest.getTagList("tasks", NBT.TAG_COMPOUND);
            for (int taskIndex = 0; taskIndex < tasks.tagCount(); taskIndex++) {
                NBTTagCompound task = tasks.getCompoundTagAt(taskIndex);
                if (compactTask(task, owner) && hasTaskProgress(task)) {
                    types.computeIfAbsent(task.getString("taskID"), unused -> types.size());
                }
            }
        }

        writer.setIndent(INDENT);
        writer.beginObject();
        writer.name("version")
            .value(VERSION);
        writer.name("taskTypes")
            .beginArray();
        writer.setIndent("");
        for (String type : types.keySet()) writer.value(type);
        writer.endArray();
        writer.setIndent(INDENT);
        writer.name("quests")
            .beginObject();
        for (Map.Entry<String, NBTTagCompound> entry : quests.entrySet()) {
            writeQuest(writer, entry.getKey(), entry.getValue(), types, owner);
        }
        writer.endObject();
        writer.endObject();
    }

    private static void writeQuest(JsonWriter writer, String id, NBTTagCompound quest, Map<String, Integer> types,
        String owner) throws IOException {
        boolean compact = compactQuest(quest, owner);
        NBTTagList tasks = quest.getTagList("tasks", NBT.TAG_COMPOUND);
        NBTTagList completed = quest.getTagList("completed", NBT.TAG_COMPOUND);
        if (compact && completed.tagCount() == 0 && !hasSavedTasks(tasks, owner)) return;

        writer.name(id)
            .beginObject();
        writer.setIndent("");
        if (!compact) {
            writer.name("data");
            NBTConverter.NBTtoJSON_Compound(quest, writer, true);
        } else {
            if (completed.tagCount() == 1) {
                NBTTagCompound completion = completed.getCompoundTagAt(0);
                writer.name(completion.getBoolean("claimed") ? "claimedAt" : "completedAt")
                    .value(completion.getLong("timestamp"));
            }
            if (hasSavedTasks(tasks, owner)) {
                writer.name("tasks")
                    .beginObject();
                for (int i = 0; i < tasks.tagCount(); i++) {
                    writeTask(writer, tasks.getCompoundTagAt(i), types, owner);
                }
                writer.endObject();
            }
        }
        writer.endObject();
        writer.setIndent(INDENT);
    }

    private static void writeTask(JsonWriter writer, NBTTagCompound task, Map<String, Integer> types, String owner)
        throws IOException {
        boolean compact = compactTask(task, owner);
        if (compact && !hasTaskProgress(task)) return;
        writer.name(Integer.toString(task.getInteger("index")));
        if (!compact) {
            NBTConverter.NBTtoJSON_Compound(task, writer, true);
            return;
        }

        String type = task.getString("taskID");
        writer.beginArray();
        writer.value(types.get(type));
        writer.value(
            task.getTagList("completeUsers", NBT.TAG_STRING)
                .tagCount());
        NBTTagList data = task.getTagList("userProgress", NBT.TAG_COMPOUND);
        if (data.tagCount() == 1) {
            NBTTagCompound row = data.getCompoundTagAt(0);
            if (ARRAY_TASKS.contains(type)) {
                writer.beginArray();
                for (int value : row.getIntArray("data")) writer.value(value);
                writer.endArray();
            } else if (XP_TASK.equals(type)) {
                writer.value(row.getLong("value"));
            } else {
                writer.value(row.getInteger("value"));
            }
        }
        writer.endArray();
    }

    private static boolean compactQuest(NBTTagCompound quest, String owner) {
        if (!onlyKeys(quest, QUEST_KEYS) || !isOptionalList(quest, "completed", NBT.TAG_COMPOUND)
            || !isOptionalList(quest, "tasks", NBT.TAG_COMPOUND)
            || (quest.hasKey("questID") && (quest.hasKey("questIDHigh") || quest.hasKey("questIDLow")))) return false;
        NBTTagList completed = quest.getTagList("completed", NBT.TAG_COMPOUND);
        if (completed.tagCount() > 1) return false;
        if (completed.tagCount() == 1) {
            NBTTagCompound entry = completed.getCompoundTagAt(0);
            if (!onlyKeys(entry, COMPLETION_KEYS) || !owner.equals(entry.getString("uuid"))
                || !entry.hasKey("timestamp", NBT.TAG_LONG)
                || !entry.hasKey("claimed", NBT.TAG_BYTE)
                || (entry.getByte("claimed") != 0 && entry.getByte("claimed") != 1)) return false;
        }
        NBTTagList tasks = quest.getTagList("tasks", NBT.TAG_COMPOUND);
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < tasks.tagCount(); i++) {
            NBTTagCompound task = tasks.getCompoundTagAt(i);
            if (!task.hasKey("index", NBT.TAG_INT) || !task.hasKey("taskID", NBT.TAG_STRING)
                || !indices.add(task.getInteger("index"))) return false;
        }
        return true;
    }

    private static boolean compactTask(NBTTagCompound task, String owner) {
        String type = task.getString("taskID");
        if (!isKnownTask(type) || !onlyKeys(task, TASK_KEYS)
            || !isOptionalList(task, "completeUsers", NBT.TAG_STRING)
            || !isOptionalList(task, "userProgress", NBT.TAG_COMPOUND)) return false;
        NBTTagList done = task.getTagList("completeUsers", NBT.TAG_STRING);
        if (done.tagCount() > 1 || (done.tagCount() == 1 && !owner.equals(done.getStringTagAt(0)))) return false;
        NBTTagList progress = task.getTagList("userProgress", NBT.TAG_COMPOUND);
        if (progress.tagCount() == 0) return true;
        if (progress.tagCount() != 1) return false;
        NBTTagCompound row = progress.getCompoundTagAt(0);
        String field = ARRAY_TASKS.contains(type) ? "data" : "value";
        int fieldType = ARRAY_TASKS.contains(type) ? NBT.TAG_INT_ARRAY
            : XP_TASK.equals(type) ? NBT.TAG_LONG : NBT.TAG_INT;
        return !COMPLETION_TASKS.contains(type) && owner.equals(row.getString("uuid"))
            && row.hasKey(field, fieldType)
            && row.func_150296_c()
                .size() == 2;
    }

    private static boolean hasTaskProgress(NBTTagCompound task) {
        return task.getTagList("completeUsers", NBT.TAG_STRING)
            .tagCount() != 0
            || task.getTagList("userProgress", NBT.TAG_COMPOUND)
                .tagCount() != 0;
    }

    private static boolean hasSavedTasks(NBTTagList tasks, String owner) {
        for (int i = 0; i < tasks.tagCount(); i++) {
            NBTTagCompound task = tasks.getCompoundTagAt(i);
            if (!compactTask(task, owner) || hasTaskProgress(task)) return true;
        }
        return false;
    }

    private static boolean isKnownTask(String type) {
        return ARRAY_TASKS.contains(type) || INTEGER_TASKS.contains(type)
            || COMPLETION_TASKS.contains(type)
            || XP_TASK.equals(type);
    }

    private static boolean isOptionalList(NBTTagCompound record, String key, int elementType) {
        if (!record.hasKey(key)) return true;
        if (!record.hasKey(key, NBT.TAG_LIST)) return false;
        NBTTagList list = (NBTTagList) record.getTag(key);
        return list.tagCount() == 0 || list.func_150303_d() == elementType;
    }

    private static boolean onlyKeys(NBTTagCompound value, Set<String> allowed) {
        return allowed.containsAll(value.func_150296_c());
    }

    private static Set<String> keys(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }

    private static String questId(NBTTagCompound quest) {
        if (hasQuestUuid(quest)) {
            UUID id = UuidValueType.QUEST.readId(quest);
            long low = id.getLeastSignificantBits();
            if (id.getMostSignificantBits() == 0 && low >= 0 && low <= Integer.MAX_VALUE) return "~" + low;
            return UuidConverter.encodeUuidStripPadding(id);
        }
        return quest.hasKey("questID", NBT.TAG_INT) ? Integer.toString(quest.getInteger("questID")) : null;
    }

    private static boolean hasQuestUuid(NBTTagCompound quest) {
        return quest.hasKey("questIDHigh", NBT.TAG_LONG) && quest.hasKey("questIDLow", NBT.TAG_LONG);
    }

    private static void writeQuestId(String id, NBTTagCompound quest) throws IOException {
        if (id.startsWith("~")) {
            int low = Integer.parseInt(id.substring(1));
            require(low >= 0, "invalid compact quest UUID");
            UuidValueType.QUEST.writeId(new UUID(0L, low), quest);
        } else if (id.length() <= 11) {
            // Older compact records used a numeric key without the UUID marker
            quest.setInteger("questID", Integer.parseInt(id));
        } else {
            require(id.length() == 22, "invalid quest UUID length");
            UUID decoded = UuidConverter.decodeUuid(id);
            require(
                UuidConverter.encodeUuidStripPadding(decoded)
                    .equals(id),
                "invalid compact quest UUID");
            UuidValueType.QUEST.writeId(decoded, quest);
        }
    }

    private static int integer(JsonElement value) throws IOException {
        return Math.toIntExact(wholeNumber(value));
    }

    private static long wholeNumber(JsonElement value) throws IOException {
        require(
            value != null && value.isJsonPrimitive()
                && value.getAsJsonPrimitive()
                    .isNumber(),
            "invalid integer");
        return value.getAsBigDecimal()
            .longValueExact();
    }

    private static void require(boolean valid, String message) throws IOException {
        if (!valid) throw new IOException(message);
    }

    public static final class ReadResult {

        private final NBTTagList progress;
        private final boolean needsRewrite;

        private ReadResult(NBTTagList progress, boolean needsRewrite) {
            this.progress = progress;
            this.needsRewrite = needsRewrite;
        }

        public NBTTagList progress() {
            return progress;
        }

        public boolean needsRewrite() {
            return needsRewrite;
        }
    }
}
