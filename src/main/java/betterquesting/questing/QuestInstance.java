package betterquesting.questing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;

import com.google.common.collect.Maps;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.QuestAction;
import betterquesting.api.questing.QuestActionContext;
import betterquesting.api.questing.QuestMutationResult;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.IDatabaseNBT;
import betterquesting.api2.utils.DirtyPlayerMarker;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.rewards.RewardStorage;
import betterquesting.questing.tasks.TaskStorage;
import betterquesting.storage.PropertyContainer;
import betterquesting.storage.QuestSettings;
import bq_standard.rewards.RewardChoice;

public class QuestInstance implements IQuest {

    private final TaskStorage tasks = new TaskStorage();
    private final RewardStorage rewards = new RewardStorage();

    private final HashMap<UUID, NBTTagCompound> completeUsers = new HashMap<>();
    private Set<UUID> preRequisites = new HashSet<>();
    private HashMap<UUID, RequirementType> prereqTypes = new HashMap<>();

    private final PropertyContainer qInfo = new PropertyContainer();

    public QuestInstance() {
        this.setupProps();
    }

    private void setupProps() {
        setupValue(NativeProps.NAME, "New Quest");
        setupValue(NativeProps.DESC, "No Description");

        setupValue(NativeProps.ICON, new BigItemStack(Items.nether_star));

        setupValue(NativeProps.SOUND_COMPLETE);
        setupValue(NativeProps.SOUND_UPDATE);
        // setupValue(NativeProps.SOUND_UNLOCK);

        setupValue(NativeProps.LOGIC_QUEST, EnumLogic.AND);
        setupValue(NativeProps.LOGIC_TASK, EnumLogic.AND);

        setupValue(NativeProps.REPEAT_TIME, -1);
        setupValue(NativeProps.REPEAT_REL, true);
        setupValue(NativeProps.LOCKED_PROGRESS, false);
        setupValue(NativeProps.AUTO_CLAIM, false);
        setupValue(NativeProps.SILENT, false);
        setupValue(NativeProps.MAIN, false);
        setupValue(NativeProps.GLOBAL_SHARE, false);
        setupValue(NativeProps.SIMULTANEOUS, false);
        setupValue(NativeProps.VISIBILITY, EnumQuestVisibility.NORMAL);
    }

    private <T> void setupValue(IPropertyType<T> prop) {
        this.setupValue(prop, prop.getDefault());
    }

    private <T> void setupValue(IPropertyType<T> prop, T def) {
        qInfo.setProperty(prop, qInfo.getProperty(prop, def));
    }

    @Nonnull
    @Override
    public QuestMutationResult applyAction(@Nonnull QuestAction action) {
        switch (action.getType()) {
            case UPDATE_PROGRESS:
                return updateProgress(action.requireContext());
            case DETECT:
                return detect(action.requireContext());
            case CLAIM_REWARD:
                return claimReward(action.requireContext(), false);
            case FORCE_CLAIM_REWARD:
                return claimReward(action.requireContext(), true);
            case COMPLETE_TASK:
                return completeTask(action.requireContext());
            case RESET:
                return resetDue(action.requireContext());
            case BACKFILL_COMPLETION:
                return backfillCompletion(action);
            case RESET_USERS:
                return resetUsers(action);
            case SET_COMPLETE_FOR_EDIT:
                return setCompleteForEdit(action);
            default:
                return new QuestMutationResult();
        }
    }

    @Nonnull
    private QuestMutationResult updateProgress(@Nonnull QuestActionContext context) {
        QuestMutationResult result = new QuestMutationResult();

        if (!isUnlocked(context.actorID)) {
            return result;
        }

        boolean wasComplete = isComplete(context.actorID);

        if (canSubmit(context.player)) {
            boolean resetProgress = updateTaskCompletion(context);
            if (resetProgress) {
                result.markDirty(context.actorID, getQuestID());
            }
        }

        result.merge(propagateCompletionIfNeeded(context, wasComplete));

        return result;
    }

    @Nonnull
    private QuestMutationResult detect(@Nonnull QuestActionContext context) {
        QuestMutationResult result = new QuestMutationResult();

        QuestCache qc = (QuestCache) context.player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc == null) {
            // Preserve legacy behavior: detect actions only mutate players with a quest cache.
            // The returned mutation result owns sync dirty marking.
            return result;
        }

        if (isComplete(context.actorID) && (qInfo.getProperty(NativeProps.REPEAT_TIME) < 0 || rewards.size() <= 0)) {
            return result;
        }

        if (!canSubmit(context.player)) {
            return result;
        }

        if (!isUnlocked(context.actorID) && !QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE)) {
            return result;
        }

        UUID questID = getQuestID();
        boolean wasComplete = isComplete(context.actorID);
        boolean taskProgressChanged = detectTaskProgress(context);

        if (isTaskLogicComplete(context.actorID)) {
            if (QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE)) {
                setComplete(context.actorID, context.timestamp);
                result.merge(propagateCompletionIfNeeded(context, wasComplete));
            } else {
                result.markDirty(context.actorID, questID);
            }
        } else if (taskProgressChanged && qInfo.getProperty(NativeProps.SIMULTANEOUS)) {
            resetUser(context.actorID, false);
            result.markDirty(context.actorID, questID);
        } else if (taskProgressChanged) {
            result.markDirty(context.actorID, questID);
        }

        return result;
    }

    @Nonnull
    private QuestMutationResult claimReward(@Nonnull QuestActionContext context, boolean forceChoice) {
        QuestMutationResult result = new QuestMutationResult();

        if (!(forceChoice ? canClaim(context.player, true) : canClaim(context.player))) {
            return result;
        }

        UUID questID = getQuestID();

        grantRewards(context.player, forceChoice, questID);

        setClaimed(context.actorID, context.timestamp);
        result.markDirty(context.actorID, questID);

        for (UUID participant : context.rewardClaimParticipants) {
            if (participant.equals(context.actorID)) {
                continue;
            }

            setClaimed(participant, context.timestamp);
            result.markDirty(participant, questID);
        }

        return result;
    }

    @Nonnull
    private QuestMutationResult completeTask(@Nonnull QuestActionContext context) {
        QuestMutationResult result = new QuestMutationResult();

        if (context.taskID == null) {
            return result;
        }

        ITask task = tasks.getValue(context.taskID);
        if (task == null) {
            return result;
        }

        UUID questID = getQuestID();
        boolean wasComplete = isComplete(context.actorID);

        for (UUID participant : context.taskParticipants) {
            task.setComplete(participant);
            result.markDirty(participant, questID);
        }

        result.merge(propagateCompletionIfNeeded(context, wasComplete));

        return result;
    }

    @Nonnull
    private QuestMutationResult resetDue(@Nonnull QuestActionContext context) {
        QuestMutationResult result = new QuestMutationResult();

        if (canSubmit(context.player)) {
            return result;
        }

        UUID questID = getQuestID();

        if (qInfo.getProperty(NativeProps.GLOBAL)) {
            resetUser(null, false);
            result.markReset(context.actorID, questID);
            result.markDirtyForAllOnlinePlayers(questID);
        } else {
            resetUser(context.actorID, false);
            result.markReset(context.actorID, questID);
        }

        return result;
    }

    @Nonnull
    private QuestMutationResult backfillCompletion(@Nonnull QuestAction action) {
        QuestMutationResult result = new QuestMutationResult();

        UUID questID = getQuestID();

        for (UUID target : action.getTargetPlayersOrEmpty()) {
            if (target == null) {
                continue;
            }

            boolean changed = false;
            if (!isComplete(target)) {
                setComplete(target, action.getCompletionTime());
                changed = true;
            }

            if (action.shouldMarkClaimed()) {
                setClaimed(target, action.getCompletionTime());
                changed = true;
            }

            if (changed) {
                result.markDirty(target, questID);
            }
        }

        return result;
    }

    @Nonnull
    private QuestMutationResult resetUsers(@Nonnull QuestAction action) {
        QuestMutationResult result = new QuestMutationResult();
        UUID questID = getQuestID();

        if (action.targetsAllPlayers()) {
            resetUser(null, action.isFullReset());
            result.markDirtyForAllOnlinePlayers(questID);

            return result;
        }

        for (UUID target : action.getTargetPlayersOrEmpty()) {
            if (target == null) {
                continue;
            }

            resetUser(target, action.isFullReset());
            result.markReset(target, questID);
        }

        return result;
    }

    @Nonnull
    private QuestMutationResult setCompleteForEdit(@Nonnull QuestAction action) {
        QuestMutationResult result = new QuestMutationResult();

        UUID questID = getQuestID();

        for (UUID target : action.getTargetPlayersOrEmpty()) {
            if (target == null) {
                continue;
            }

            if (isComplete(target)) {
                setClaimed(target, action.getCompletionTime());
            } else {
                setComplete(target, action.getCompletionTime());
                completeEnoughTasksForClaim(target);
            }

            result.markDirty(target, questID);
        }

        return result;
    }

    private boolean updateTaskCompletion(@Nonnull QuestActionContext context) {
        int done = 0;

        for (DBEntry<ITask> entry : tasks.getEntries()) {
            ITask task = entry.getValue();

            if (task.isComplete(context.actorID) || task.ignored(context.actorID)) {
                done++;
            }
        }

        if (tasks.size() <= 0 || qInfo.getProperty(NativeProps.LOGIC_TASK)
            .getResult(done, tasks.size())) {
            setComplete(context.actorID, context.timestamp);
            return false;
        }

        if (done > 0 && qInfo.getProperty(NativeProps.SIMULTANEOUS)) {
            resetUser(context.actorID, false);
            return true;
        }

        return false;
    }

    private void completeEnoughTasksForClaim(@Nonnull UUID target) {
        int done = 0;

        if (!qInfo.getProperty(NativeProps.LOGIC_TASK)
            .getResult(done, tasks.size())) {
            for (DBEntry<ITask> task : tasks.getEntries()) {
                task.getValue()
                    .setComplete(target);
                done++;

                if (qInfo.getProperty(NativeProps.LOGIC_TASK)
                    .getResult(done, tasks.size())) {
                    break;
                }
            }
        }
    }

    private boolean detectTaskProgress(@Nonnull QuestActionContext context) {
        boolean changed = false;

        ParticipantInfo partInfo = new ParticipantInfo(context.player);
        Map.Entry<UUID, IQuest> mapEntry = Maps.immutableEntry(getQuestID(), this);

        for (DBEntry<ITask> entry : tasks.getEntries()) {
            ITask task = entry.getValue();

            if (task.isComplete(context.actorID)) {
                continue;
            }

            task.detect(partInfo, mapEntry);

            if (task.isComplete(context.actorID)) {
                changed = true;
            }
        }

        return changed;
    }

    private boolean isTaskLogicComplete(@Nonnull UUID playerID) {
        int done = 0;
        int numTasks = tasks.size();

        for (DBEntry<ITask> entry : tasks.getEntries()) {
            ITask task = entry.getValue();

            if (task.isComplete(playerID)) {
                done++;
            }

            if (task.ignored(playerID)) {
                numTasks--;

                if (task.isComplete(playerID)) {
                    done--;
                }
            }
        }

        return numTasks <= 0 || qInfo.getProperty(NativeProps.LOGIC_TASK)
            .getResult(done, numTasks);
    }

    /**
     * Applies the live party-completion rule for this quest.
     *
     * This is the core LAN/multiplayer fix: once a mutation newly completes this
     * quest for the actor, completion is propagated immediately to party
     * participants. This only updates completion state; it does not grant rewards.
     */
    @Nonnull
    private QuestMutationResult propagateCompletionIfNeeded(@Nonnull QuestActionContext context, boolean wasComplete) {
        QuestMutationResult result = new QuestMutationResult();

        if (wasComplete || !isComplete(context.actorID) || canSubmit(context.player)) {
            return result;
        }

        UUID questID = getQuestID();
        NBTTagCompound completionInfo = getCompletionInfo(context.actorID);
        long completionTime = completionInfo != null ? completionInfo.getLong("timestamp") : context.timestamp;

        result.markCompleted(context.actorID, questID);

        for (UUID participant : context.completionParticipants) {
            if (!isComplete(participant)) {
                setComplete(participant, completionTime);
            }

            result.markDirty(participant, questID);
        }

        return result;
    }

    private void grantRewards(@Nonnull EntityPlayer player, boolean forceChoice, @Nonnull UUID questID) {
        Map.Entry<UUID, IQuest> mapEntry = Maps.immutableEntry(questID, this);

        for (DBEntry<IReward> rewardEntry : rewards.getEntries()) {
            IReward reward = rewardEntry.getValue();

            if (forceChoice && reward instanceof RewardChoice choiceReward) {
                // Force a randomly selected choice reward
                choiceReward.selectRandomChoice(player);
            }

            reward.claimReward(player, mapEntry);
        }
    }

    @Nonnull
    private UUID getQuestID() {
        return QuestDatabase.INSTANCE.lookupKey(this);
    }

    @Override
    public boolean hasClaimed(UUID uuid) {
        if (rewards.size() <= 0) return true;

        synchronized (completeUsers) {
            if (qInfo.getProperty(NativeProps.GLOBAL) && !qInfo.getProperty(NativeProps.GLOBAL_SHARE)) {
                for (NBTTagCompound entry : completeUsers.values()) {
                    if (entry.getBoolean("claimed")) {
                        return true;
                    }
                }

                return false;
            }

            NBTTagCompound entry = getCompletionInfo(uuid);
            return entry != null && entry.getBoolean("claimed");
        }
    }

    @Override
    public boolean canClaimBasically(EntityPlayer player) {
        UUID pID = QuestingAPI.getQuestingUUID(player);
        NBTTagCompound entry = getCompletionInfo(pID);

        return entry != null && !hasClaimed(pID) && !canSubmit(player);
    }

    @Override
    public boolean canClaim(EntityPlayer player, boolean forceChoice) {
        if (!canClaimBasically(player)) return false;

        Map.Entry<UUID, IQuest> mapEntry = Maps.immutableEntry(getQuestID(), this);
        for (DBEntry<IReward> rew : rewards.getEntries()) {
            IReward unwrapped = rew.getValue();
            if (unwrapped instanceof RewardChoice && forceChoice) continue;
            if (!unwrapped.canClaim(player, mapEntry)) return false;
        }

        return true;
    }

    @Override
    public boolean canSubmit(EntityPlayer player) {
        if (player == null) return false;

        UUID playerID = QuestingAPI.getQuestingUUID(player);

        synchronized (completeUsers) {
            NBTTagCompound entry = this.getCompletionInfo(playerID);
            if (entry == null) return true;

            if (!entry.getBoolean("claimed") && getProperty(NativeProps.REPEAT_TIME) >= 0) {
                if (tasks.size() <= 0) return true;

                int done = 0;

                for (DBEntry<ITask> tsk : tasks.getEntries()) {
                    if (tsk.getValue()
                        .isComplete(playerID)
                        || tsk.getValue()
                            .ignored(playerID)) {
                        done += 1;
                    }
                }

                return !qInfo.getProperty(NativeProps.LOGIC_TASK)
                    .getResult(done, tasks.size());
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean isUnlocked(UUID uuid) {
        if (preRequisites.isEmpty()) {
            return true;
        }

        int complete = (int) QuestDatabase.INSTANCE.getAll(preRequisites)
            .filter(quest -> quest.isComplete(uuid))
            .count();
        return qInfo.getProperty(NativeProps.LOGIC_QUEST)
            .getResult(complete, preRequisites.size());
    }

    @Override
    public boolean isUnlockable(UUID uuid) {
        if (preRequisites.isEmpty()) {
            return true;
        }

        EnumLogic questLogic = qInfo.getProperty(NativeProps.LOGIC_QUEST);
        if (questLogic.isTrivial()) return true;

        int complete = (int) QuestDatabase.INSTANCE.getAll(preRequisites)
            .filter(quest -> quest.isComplete(uuid))
            .count();
        return questLogic.isUnlockable(complete, preRequisites.size());
    }

    /**
     * Returns true if the quest has been completed at least once.
     */
    @Override
    public boolean isComplete(UUID uuid) {
        if (qInfo.getProperty(NativeProps.GLOBAL)) {
            return completeUsers.size() > 0;
        } else {
            return getCompletionInfo(uuid) != null;
        }
    }

    @Override
    public EnumQuestState getState(EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        if (this.isComplete(uuid)) {
            if (canClaimBasically(player)) {
                return EnumQuestState.UNCLAIMED;
            } else if (this.getProperty(NativeProps.REPEAT_TIME) > -1 && !this.hasClaimed(uuid)) {
                return EnumQuestState.REPEATABLE;
            }
            return EnumQuestState.COMPLETED;
        } else if (this.isUnlocked(uuid)) {
            return EnumQuestState.UNLOCKED;
        }

        return EnumQuestState.LOCKED;
    }

    @Override
    public NBTTagCompound getCompletionInfo(UUID uuid) {
        synchronized (completeUsers) {
            return completeUsers.get(uuid);
        }
    }

    private void setCompletionInfo(UUID uuid, @Nullable NBTTagCompound nbt) {
        if (uuid == null) return;

        synchronized (completeUsers) {
            if (nbt == null) {
                completeUsers.remove(uuid);
            } else {
                completeUsers.put(uuid, nbt);
            }

            DirtyPlayerMarker.markDirty(uuid);
        }
    }

    private void setComplete(UUID uuid, long timestamp) {
        if (uuid == null) return;

        synchronized (completeUsers) {
            NBTTagCompound entry = this.getCompletionInfo(uuid);

            if (entry == null) {
                entry = new NBTTagCompound();
                completeUsers.put(uuid, entry);
            }

            entry.setBoolean("claimed", false);
            entry.setLong("timestamp", timestamp);

            DirtyPlayerMarker.markDirty(uuid);
        }

        // Optional/ignored tasks are ignored by completion logic, but leaving
        // them unchecked after quest completion is confusing in the UI.
        for (DBEntry<ITask> entry : tasks.getEntries()) {
            ITask task = entry.getValue();
            if (task != null && task.ignored(uuid)) {
                task.setComplete(uuid);
            }
        }
    }

    private void setClaimed(UUID uuid, long timestamp) {
        synchronized (completeUsers) {
            NBTTagCompound entry = this.getCompletionInfo(uuid);

            if (entry != null) {
                entry.setBoolean("claimed", true);
                entry.setLong("timestamp", timestamp);
            } else {
                entry = new NBTTagCompound();
                entry.setBoolean("claimed", true);
                entry.setLong("timestamp", timestamp);
                completeUsers.put(uuid, entry);
            }

            DirtyPlayerMarker.markDirty(uuid);
        }
    }

    /**
     * Resets task progress and claim status.
     *
     * If fullReset is true, completion status is removed entirely.
     * Otherwise completion remains but claim state is made available again.
     */
    private void resetUser(@Nullable UUID uuid, boolean fullReset) {
        synchronized (completeUsers) {
            HashSet<UUID> dirtyPlayers = new HashSet<>();
            if (uuid == null) {
                dirtyPlayers.addAll(completeUsers.keySet());
            } else {
                dirtyPlayers.add(uuid);
            }

            if (fullReset) {
                if (uuid == null) {
                    completeUsers.clear();
                } else {
                    completeUsers.remove(uuid);
                }
            } else {
                if (uuid == null) {
                    completeUsers.forEach((key, value) -> {
                        value.setBoolean("claimed", false);
                        value.setLong("timestamp", 0);
                    });
                } else {
                    NBTTagCompound entry = getCompletionInfo(uuid);
                    if (entry != null) {
                        entry.setBoolean("claimed", false);
                        entry.setLong("timestamp", 0);
                    }
                }
            }

            DirtyPlayerMarker.markDirty(dirtyPlayers);
            tasks.getEntries()
                .forEach(
                    value -> value.getValue()
                        .resetUser(uuid));
        }
    }

    @Nonnull
    private HashSet<UUID> getUsersWithCompletionDataCopy() {
        synchronized (completeUsers) {
            return new HashSet<>(completeUsers.keySet());
        }
    }

    public void getUsersWithCompletionData(Set<UUID> targetSet) {
        synchronized (completeUsers) {
            targetSet.addAll(completeUsers.keySet());
        }
    }

    @Override
    public IDatabaseNBT<ITask, NBTTagList, NBTTagList> getTasks() {
        return tasks;
    }

    @Override
    public IDatabaseNBT<IReward, NBTTagList, NBTTagList> getRewards() {
        return rewards;
    }

    @Nonnull
    @Override
    public Set<UUID> getRequirements() {
        return preRequisites;
    }

    public void setRequirements(@Nonnull Iterable<UUID> req) {
        preRequisites.clear();
        req.forEach(preRequisites::add);
        prereqTypes.keySet()
            .removeIf(key -> !preRequisites.contains(key));
    }

    @Nonnull
    @Override
    public RequirementType getRequirementType(UUID req) {
        RequirementType type = prereqTypes.get(req);
        return type == null ? RequirementType.NORMAL : type;
    }

    @Override
    public void setRequirementType(UUID req, @Nonnull RequirementType kind) {
        if (kind == RequirementType.NORMAL) prereqTypes.remove(req);
        else prereqTypes.put(req, kind);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound jObj) {
        jObj.setTag("properties", qInfo.writeToNBT(new NBTTagCompound()));
        jObj.setTag("tasks", tasks.writeToNBT(new NBTTagList(), null));
        jObj.setTag("rewards", rewards.writeToNBT(new NBTTagList(), null));

        NBTTagList tagList = new NBTTagList();
        for (UUID questID : preRequisites) {
            NBTTagCompound tag = NBTConverter.UuidValueType.QUEST.writeId(questID);

            if (prereqTypes.containsKey(questID)) {
                tag.setByte(
                    "type",
                    prereqTypes.get(questID)
                        .id());
            }

            tagList.appendTag(tag);
        }
        jObj.setTag("preRequisites", tagList);

        return jObj;
    }

    @Override
    public void readFromNBT(NBTTagCompound jObj) {
        this.qInfo.readFromNBT(jObj.getCompoundTag("properties"));
        this.tasks.readFromNBT(jObj.getTagList("tasks", 10), false);
        this.rewards.readFromNBT(jObj.getTagList("rewards", 10), false);

        // The legacy storage format used array indices to link together two separate list tags,
        // one for prerequisites, and one for prerequisite tags.
        // We need this map to recreate that link.
        Map<Integer, UUID> legacyPrerequisiteIndex = new HashMap<>();
        if (jObj.func_150299_b("preRequisites") == Constants.NBT.TAG_LIST) {
            preRequisites = new HashSet<>();

            List<NBTBase> tagList = NBTConverter
                .getTagList(jObj.getTagList("preRequisites", Constants.NBT.TAG_COMPOUND));
            for (NBTBase tag : tagList) {
                if (!(tag instanceof NBTTagCompound)) {
                    continue;
                }

                NBTTagCompound tagCompound = (NBTTagCompound) tag;
                Optional<UUID> questIDOptional = NBTConverter.UuidValueType.QUEST.tryReadId(tagCompound);
                if (!questIDOptional.isPresent()) {
                    continue;
                }

                UUID questID = questIDOptional.get();
                preRequisites.add(questID);

                if (tagCompound.hasKey("type", 99)) {
                    setRequirementType(questID, RequirementType.from(tagCompound.getByte("type")));
                }
            }
        } else if (jObj.func_150299_b("preRequisites") == Constants.NBT.TAG_INT_ARRAY) {
            // Legacy format
            preRequisites = new HashSet<>();
            int[] intArray = jObj.getIntArray("preRequisites");
            for (int i = 0; i < intArray.length; i++) {
                UUID questID = UuidConverter.convertLegacyId(intArray[i]);
                preRequisites.add(questID);
                legacyPrerequisiteIndex.put(i, questID);
            }
        }

        // This block is needed for old questbook data.
        if (jObj.func_150299_b("preRequisiteTypes") == Constants.NBT.TAG_BYTE_ARRAY) {
            byte[] byteArray = jObj.getByteArray("preRequisiteTypes");
            for (int i = 0, byteArrayLength = byteArray.length; i < byteArrayLength; i++) {
                UUID questID = legacyPrerequisiteIndex.get(i);
                if (questID == null) {
                    continue;
                }

                setRequirementType(questID, RequirementType.from(byteArray[i]));
            }
        }

        this.setupProps();
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound json, @Nullable List<UUID> users) {
        synchronized (completeUsers) {
            NBTTagList comJson = new NBTTagList();
            for (Entry<UUID, NBTTagCompound> entry : completeUsers.entrySet()) {
                if (entry.getValue() == null || entry.getKey() == null) continue;
                if (users != null && !users.contains(entry.getKey())) continue;
                NBTTagCompound tags = (NBTTagCompound) entry.getValue()
                    .copy();
                tags.setString(
                    "uuid",
                    entry.getKey()
                        .toString());
                comJson.appendTag(tags);
            }
            json.setTag("completed", comJson);
            NBTTagList tskJson = tasks.writeProgressToNBT(new NBTTagList(), users);
            json.setTag("tasks", tskJson);

            return json;
        }
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound json, boolean merge) {
        synchronized (completeUsers) {
            if (!merge) completeUsers.clear();
            NBTTagList comList = json.getTagList("completed", 10);
            for (int i = 0; i < comList.tagCount(); i++) {
                NBTTagCompound entry = (NBTTagCompound) comList.getCompoundTagAt(i)
                    .copy();

                try {
                    UUID uuid = UUID.fromString(entry.getString("uuid"));
                    completeUsers.put(uuid, entry);
                } catch (Exception e) {
                    BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for quest", e);
                }
            }

            tasks.readProgressFromNBT(json.getTagList("tasks", 10), merge);
        }
    }

    @Override
    public <T> T getProperty(IPropertyType<T> prop) {
        return qInfo.getProperty(prop);
    }

    @Override
    public <T> T getProperty(IPropertyType<T> prop, T def) {
        return qInfo.getProperty(prop, def);
    }

    @Override
    public boolean hasProperty(IPropertyType<?> prop) {
        return qInfo.hasProperty(prop);
    }

    @Override
    public <T> void setProperty(IPropertyType<T> prop, T value) {
        qInfo.setProperty(prop, value);
    }

    @Override
    public void removeProperty(IPropertyType<?> prop) {
        qInfo.removeProperty(prop);
    }

    @Override
    public void removeAllProps() {
        qInfo.removeAllProps();
    }
}
