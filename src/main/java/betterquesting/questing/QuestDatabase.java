package betterquesting.questing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.storage.UuidDatabase;
import betterquesting.core.BetterQuesting;

public class QuestDatabase extends UuidDatabase<IQuest> implements IQuestDatabase {

    public static final QuestDatabase INSTANCE = new QuestDatabase();

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
                // This block is needed for old questbook data.
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
    public synchronized NBTTagList writeProgressToNBT(NBTTagList json, List<UUID> users) {
        for (Map.Entry<UUID, IQuest> entry : entrySet()) {
            NBTTagCompound jq = entry.getValue()
                .writeProgressToNBT(new NBTTagCompound(), users);
            NBTConverter.UuidValueType.QUEST.writeId(entry.getKey(), jq);
            json.appendTag(jq);
        }

        return json;
    }

    @Override
    public synchronized void readProgressFromNBT(NBTTagList json, boolean merge) {
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
                // This block is needed for old player progress data.
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
}
