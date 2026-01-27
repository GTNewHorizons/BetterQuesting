package betterquesting.api2.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import com.google.common.collect.ImmutableSet;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetCacheSync;
import betterquesting.questing.QuestDatabase;
import bq_standard.integration.vendingmachine.VmAdapter;

public class QuestCache implements IExtendedEntityProperties {

    public static final ResourceLocation LOC_QUEST_CACHE = new ResourceLocation("betterquesting", "quest_cache");

    // Quests that are visible to the player
    private final HashSet<UUID> visibleQuests = new HashSet<>();

    // Quests that are currently being undertaken. NOTE: Quests can be locked but still processing data if configured to
    // do so
    private final HashSet<UUID> activeQuests = new HashSet<>();

    // Quests and their scheduled time of being reset
    private final TreeSet<QResetTime> resetSchedule = new TreeSet<>(
        (o1, o2) -> o1.questID.equals(o2.questID) ? 0 : Long.compare(o1.time, o2.time));

    // Quests with pending auto claims (usually should be empty unless a condition needs to be met)
    private final HashSet<UUID> autoClaims = new HashSet<>();

    // Quests that need to be sent to the client to update progression (NOT for edits. Handle that elsewhere)
    private final HashSet<UUID> markedDirty = new HashSet<>();

    @Override
    public void init(Entity entity, World world) {}

    /**
     * I don't think that it's currently necessary for this method to return a copy, but let's do so
     * anyway in case of future concurrency changes.
     */
    public synchronized ImmutableSet<UUID> getActiveQuests() {
        return ImmutableSet.copyOf(activeQuests);
    }

    /**
     * I don't think that it's currently necessary for this method to return a copy, but let's do so
     * anyway in case of future concurrency changes.
     */
    public synchronized ImmutableSet<UUID> getVisibleQuests() {
        return ImmutableSet.copyOf(visibleQuests);
    }

    /**
     * I don't think that it's currently necessary for this method to return a copy, but let's do so
     * anyway in case of future concurrency changes.
     */
    public synchronized ImmutableSet<UUID> getPendingAutoClaims() {
        return ImmutableSet.copyOf(autoClaims);
    }

    public synchronized QResetTime[] getScheduledResets() // Already sorted by time
    {
        return resetSchedule.toArray(new QResetTime[0]);
    }

    public synchronized void markQuestDirty(UUID questID) {
        markedDirty.add(questID);
    }

    public synchronized void markQuestClean(UUID questID) {
        markedDirty.remove(questID);
    }

    public synchronized void cleanAllQuests() {
        markedDirty.clear();
    }

    /**
     * This method must return a copy of {@code markedDirty}, because {@code markedDirty} gets
     * cleared every tick. Returning it directly means introducing a potential race condition where
     * any callers that run code on separate threads may run after {@code markedDirty} gets cleared.
     */
    public synchronized ImmutableSet<UUID> getDirtyQuests() {
        return ImmutableSet.copyOf(markedDirty);
    }

    // TODO: Ensure this is thread safe because we're likely going to run this in the background
    // NOTE: Only run this when the quests completion and claim states change. Use markQuestDirty() for progression
    // changes that need syncing
    public synchronized void updateCache(@Nonnull EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        Set<Map.Entry<UUID, IQuest>> questDB = QuestingAPI.getAPI(ApiReference.QUEST_DB)
            .entrySet();

        List<UUID> tmpVisible = new ArrayList<>();
        List<UUID> tmpActive = new ArrayList<>();
        List<QResetTime> tmpReset = new ArrayList<>();
        List<UUID> tmpAutoClaim = new ArrayList<>();

        // For Vending Machine Integration, populate and push to
        List<UUID> tradeAllowed = new ArrayList<>();

        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, IQuest> entry : questDB) {
            if (entry.getValue()
                .isUnlocked(uuid)
                || entry.getValue()
                    .getProperty(NativeProps.LOCKED_PROGRESS)) // Unlocked or actively processing progression data
            {
                int repeat = entry.getValue()
                    .getProperty(NativeProps.REPEAT_TIME);
                NBTTagCompound ue = entry.getValue()
                    .getCompletionInfo(uuid);

                if ((ue == null && entry.getValue()
                    .getTasks()
                    .size() <= 0) || entry.getValue()
                        .canSubmit(player)) // Can be active without completion in the case of locked progress. Also
                                            // account for taskless quests
                {
                    tmpActive.add(entry.getKey());
                } else if (ue != null) // These conditions only trigger after first completion
                {
                    if (repeat >= 0 && entry.getValue()
                        .hasClaimed(uuid)) {
                        long altTime = ue.getLong("timestamp");
                        if (altTime > currentTime) altTime = currentTime;
                        if (repeat > 1 && !entry.getValue()
                            .getProperty(NativeProps.REPEAT_REL)) altTime -= (altTime % repeat);
                        tmpReset.add(new QResetTime(entry.getKey(), altTime + (repeat * 50)));
                    }

                    if (!entry.getValue()
                        .hasClaimed(uuid) && entry.getValue()
                            .getProperty(NativeProps.AUTO_CLAIM)) {
                        tmpAutoClaim.add(entry.getKey());
                    }
                }
            }

            if (isQuestShown(entry.getValue(), uuid, player)) {
                tmpVisible.add(entry.getKey());
            }

            if (entry.getValue()
                .isComplete(uuid)
                || entry.getValue()
                    .canClaimBasically(player)) {
                tradeAllowed.add(entry.getKey());
            }
        }

        visibleQuests.clear();
        visibleQuests.addAll(tmpVisible);

        activeQuests.clear();
        activeQuests.addAll(tmpActive);

        resetSchedule.clear();
        resetSchedule.addAll(tmpReset);

        autoClaims.clear();
        autoClaims.addAll(tmpAutoClaim);

        if (BetterQuesting.isVmLoaded) {
            // These are only updated serverside, since updateCache is only checked there
            VmAdapter.addBqConditions(uuid, tradeAllowed);
            VmAdapter.sendPlayerSatisfiedCache();
        }

        if (player instanceof EntityPlayerMP) {
            NetCacheSync.sendSync((EntityPlayerMP) player);
        }
    }

    @Override
    public synchronized void saveNBTData(NBTTagCompound tags) {

        tags.setTag("visibleQuests", NBTConverter.UuidValueType.QUEST.writeIds(getVisibleQuests()));
        tags.setTag("activeQuests", NBTConverter.UuidValueType.QUEST.writeIds(getActiveQuests()));
        tags.setTag("autoClaims", NBTConverter.UuidValueType.QUEST.writeIds(getPendingAutoClaims()));
        tags.setTag("markedDirty", NBTConverter.UuidValueType.QUEST.writeIds(getDirtyQuests()));

        NBTTagList tagSchedule = new NBTTagList();
        for (QResetTime entry : getScheduledResets()) {
            NBTTagCompound tagEntry = NBTConverter.UuidValueType.QUEST.writeId(entry.questID);
            tagEntry.setLong("time", entry.time);
            tagSchedule.appendTag(tagEntry);
        }
        tags.setTag("resetSchedule", tagSchedule);
    }

    @Override
    public synchronized void loadNBTData(NBTTagCompound nbt) {
        visibleQuests.clear();
        activeQuests.clear();
        resetSchedule.clear();
        autoClaims.clear();
        markedDirty.clear();

        visibleQuests.addAll(NBTConverter.UuidValueType.QUEST.readIds(nbt, "visibleQuests"));
        activeQuests.addAll(NBTConverter.UuidValueType.QUEST.readIds(nbt, "activeQuests"));
        autoClaims.addAll(NBTConverter.UuidValueType.QUEST.readIds(nbt, "autoClaims"));
        markedDirty.addAll(NBTConverter.UuidValueType.QUEST.readIds(nbt, "markedDirty"));

        NBTTagList tagList = nbt.getTagList("resetSchedule", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tagEntry = tagList.getCompoundTagAt(i);
            NBTConverter.UuidValueType.QUEST.tryReadId(tagEntry)
                .ifPresent(uuid -> resetSchedule.add(new QResetTime(uuid, tagEntry.getLong("time"))));
        }
    }

    public class QResetTime implements Comparable<QResetTime> {

        public final UUID questID;
        public final long time;

        private QResetTime(UUID questID, long time) {
            this.questID = questID;
            this.time = time;
        }

        @Override
        public int compareTo(QResetTime o) {
            return Long.compare(o.time, time);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof QResetTime)) {
                return false;
            }
            return ((QResetTime) o).questID.equals(questID);
        }
    }

    // TODO: Make this based on a fixed state stored on the quest instead of calculated on demand
    // TODO: Also make this thread safe
    public static boolean isQuestShown(IQuest quest, UUID uuid, EntityPlayer player) {
        if (quest == null || uuid == null) {
            return false;
        }

        EnumQuestVisibility vis = quest.getProperty(NativeProps.VISIBILITY);

        if (QuestingAPI.getAPI(ApiReference.SETTINGS)
            .canUserEdit(player) || vis == EnumQuestVisibility.ALWAYS) // Always shown or in edit mode
        {
            return true;
        } else if (vis == EnumQuestVisibility.HIDDEN) {
            return false;
        } else if (vis == EnumQuestVisibility.SECRET) {
            return quest.isComplete(uuid) || quest.isUnlocked(uuid);
        } else if (BQ_Settings.viewMode) {
            return true;
        } else if (vis == EnumQuestVisibility.UNLOCKED) {
            return quest.isComplete(uuid) || quest.isUnlocked(uuid);
        } else if (vis == EnumQuestVisibility.NORMAL) {
            if (quest.isComplete(uuid) || quest.isUnlocked(uuid)) // Complete or pending
            {
                return true;
            }

            // Previous quest is underway and this one is visible but still locked (foreshadowing)
            return QuestDatabase.INSTANCE.getAll(quest.getRequirements())
                .allMatch(q -> q.isUnlocked(uuid));
        } else if (vis == EnumQuestVisibility.COMPLETED) {
            return quest.isComplete(uuid);
        } else if (vis == EnumQuestVisibility.CHAIN) {
            if (quest.getRequirements()
                .isEmpty()) {
                return true;
            }

            return QuestDatabase.INSTANCE.getAll(quest.getRequirements())
                .anyMatch(q -> isQuestShown(q, uuid, player));
        }

        return true;
    }
}
