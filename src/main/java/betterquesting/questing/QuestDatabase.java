package betterquesting.questing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.storage.UuidDatabase;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;

public class QuestDatabase extends UuidDatabase<IQuest> implements IQuestDatabase {

    public static final QuestDatabase INSTANCE = new QuestDatabase();
    private static final ThreadLocal<Boolean> LOADED_PROGRESS_ONLY = new ThreadLocal<>();

    @Override
    public synchronized IQuest createNew(UUID questID) {
        IQuest quest = new QuestInstance();
        put(questID, quest);
        return quest;
    }

    @Nullable
    @Override
    public IQuest put(@Nullable UUID key, @Nullable IQuest value) {
        if (value == null && BQ_Settings.logNullQuests) {
            BetterQuesting.logger.warn("A null quest was added with ID {}", key);
        }
        return super.put(key, value);
    }

    @Override
    public IQuest remove(Object key) {
        if (!(key instanceof UUID)) {
            return null;
        }
        UUID questID = (UUID) key;

        IQuest removed = super.remove(questID);
        if (removed != null) {
            for (IQuest quest : values()) {
                removeReq(quest, questID);
            }
        }
        return removed;
    }

    @Override
    public UUID removeValue(IQuest value) {
        UUID questID = super.removeValue(value);
        if (questID != null) {
            for (IQuest quest : values()) {
                removeReq(quest, questID);

            }
        }
        return questID;
    }

    private void removeReq(IQuest quest, UUID questID) {
        quest.getRequirements()
            .remove(questID);
    }

    @Override
    public synchronized NBTTagList writeToNBT(NBTTagList nbt, @Nullable List<UUID> subset) {
        orderedEntries().forEach(entry -> {
            if (subset != null && !subset.contains(entry.getKey())) {
                return;
            }

            if (entry.getValue() == null) {
                if (BQ_Settings.logNullQuests) {
                    BetterQuesting.logger.warn("Tried saving null quest with ID {}", entry.getKey());
                }
                return;
            }

            NBTTagCompound jq = new NBTTagCompound();
            entry.getValue()
                .writeToNBT(jq);
            NBTConverter.UuidValueType.QUEST.writeId(entry.getKey(), jq);
            nbt.appendTag(jq);
        });

        return nbt;
    }

    @Override
    public synchronized void readFromNBT(NBTTagList nbt, boolean merge) {
        if (!merge) {
            clear();
        }

        for (int i = 0; i < nbt.tagCount(); i++) {
            NBTTagCompound qTag = nbt.getCompoundTagAt(i);

            Optional<UUID> questIDOptional = NBTConverter.UuidValueType.QUEST.tryReadId(qTag);
            UUID questID;
            if (questIDOptional.isPresent()) {
                questID = questIDOptional.get();
            } else if (qTag.hasKey("questID", 99)) {
                // Keep numeric IDs readable until supported quest definitions have been rewritten with UUIDs
                questID = UuidConverter.convertLegacyId(qTag.getInteger("questID"));
            } else {
                continue;
            }

            IQuest quest = get(questID);
            quest = quest != null ? quest : createNew(questID);
            quest.readFromNBT(qTag);
        }
    }

    @Override
    public NBTTagList writeProgressToNBT(NBTTagList json, List<UUID> users) {
        boolean ready = users == null ? SaveLoadHandler.INSTANCE.ensureAllProgressLoaded()
            : SaveLoadHandler.INSTANCE.ensurePlayerProgressLoaded(users);
        if (!ready) throw new IllegalStateException("Could not load BetterQuesting player progress");
        return writeLoadedProgressToNBT(json, users);
    }

    /** Builds a personal progress record from the party's completions without changing its members. */
    public NBTTagList recoverPartyCompletions(UUID player, List<UUID> members) {
        NBTTagList recovered = new NBTTagList();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, IQuest> entry : entrySet()) {
            IQuest quest = entry.getValue();
            if (quest == null || members.stream()
                .noneMatch(member -> quest.getCompletionInfo(member) != null)) continue;
            NBTTagCompound completion = new NBTTagCompound();
            completion.setString("uuid", player.toString());
            completion.setLong("timestamp", now);
            completion.setBoolean("claimed", true);
            if (quest.getProperty(NativeProps.GLOBAL) && !quest.getProperty(NativeProps.GLOBAL_SHARE)) {
                completion.setBoolean(QuestInstance.REWARD_FORFEITED, true);
            }
            NBTTagList completed = new NBTTagList();
            completed.appendTag(completion);
            NBTTagCompound record = NBTConverter.UuidValueType.QUEST.writeId(entry.getKey());
            record.setTag("completed", completed);
            recovered.appendTag(record);
        }
        return recovered;
    }

    /** Takes a snapshot for definition reloads without hydrating dormant players. */
    public synchronized NBTTagList writeLoadedProgressToNBT(NBTTagList json, List<UUID> users) {
        withLoadedProgress(() -> {
            for (Map.Entry<UUID, IQuest> entry : entrySet()) {
                NBTTagCompound jq = entry.getValue()
                    .writeProgressToNBT(new NBTTagCompound(), users);
                NBTConverter.UuidValueType.QUEST.writeId(entry.getKey(), jq);
                json.appendTag(jq);
            }
        });
        return json;
    }

    @Override
    public void readProgressFromNBT(NBTTagList json, boolean merge) {
        boolean ready = merge ? SaveLoadHandler.INSTANCE.ensureReferencedProgressLoaded(json)
            : SaveLoadHandler.INSTANCE.ensureAllProgressLoaded();
        if (!ready) throw new IllegalStateException("Could not load BetterQuesting player progress");
        readLoadedProgressFromNBT(json, merge);
    }

    public synchronized void readLoadedProgressFromNBT(NBTTagList json, boolean merge) {
        withLoadedProgress(() -> readProgressTags(json, merge));
    }

    private void readProgressTags(NBTTagList json, boolean merge) {
        // Cache tagCount to avoid repeated method calls
        final int tagCount = json.tagCount();

        for (int i = 0; i < tagCount; i++) {
            NBTTagCompound qTag = json.getCompoundTagAt(i);

            // Streamlined UUID reading with early returns
            UUID questID = null;
            Optional<UUID> questIDOptional = NBTConverter.UuidValueType.QUEST.tryReadId(qTag);
            if (questIDOptional.isPresent()) {
                questID = questIDOptional.get();
            } else if (qTag.hasKey("questID", 99)) {
                // The public progress API still accepts numeric IDs during the transition to UUIDs
                questID = UuidConverter.convertLegacyId(qTag.getInteger("questID"));
            }

            // Single null check with continue
            if (questID == null) continue;

            IQuest quest = get(questID);
            if (quest != null) {
                quest.readProgressFromNBT(qTag, merge);
            }
        }
    }

    static boolean usesLoadedProgressOnly() {
        return Boolean.TRUE.equals(LOADED_PROGRESS_ONLY.get());
    }

    private static void withLoadedProgress(Runnable action) {
        if (usesLoadedProgressOnly()) {
            action.run();
            return;
        }
        LOADED_PROGRESS_ONLY.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            LOADED_PROGRESS_ONLY.remove();
        }
    }
}
