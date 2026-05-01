package betterquesting.questing.mutation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.cache.QuestCache;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.sync.QuestChangeSet;

public final class QuestMutationService {

    private QuestMutationService() {}

    /**
     * Marks an already-validated task complete for the given players and reports the
     * affected quest/player pairs.
     * <p>
     * This method intentionally does not perform task-specific validation. Callers are
     * responsible for checking whether the task can be completed by this action.
     */
    @Nonnull
    public static QuestProgressResult setTaskComplete(@Nullable UUID questID, @Nullable IQuest quest,
        @Nullable ITask task, @Nonnull EntityPlayer player, @Nonnull Collection<UUID> playerIDs) {
        QuestProgressResult result = new QuestProgressResult();

        if (questID == null || quest == null || task == null) {
            return result;
        }

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        boolean wasComplete = quest.isComplete(playerID);

        for (UUID targetPlayerID : playerIDs) {
            if (targetPlayerID == null) {
                continue;
            }

            task.setComplete(targetPlayerID);
            result.markChanged(targetPlayerID, questID);
        }

        result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));

        return result;
    }

    @Nonnull
    public static QuestChangeSet claimReward(@Nullable UUID questID, @Nullable IQuest quest,
        @Nonnull EntityPlayer player, boolean forceChoice, boolean includeSharedParticipants) {
        QuestChangeSet changes = new QuestChangeSet();

        if (questID == null || quest == null) {
            return changes;
        }

        boolean canClaim = forceChoice ? quest.canClaim(player, true) : quest.canClaim(player);
        if (!canClaim) {
            return changes;
        }

        quest.claimReward(player, forceChoice);

        List<UUID> participants = QuestParticipantResolver
            .resolvePlayerProgressParticipants(player, includeSharedParticipants);

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        long timestamp = System.currentTimeMillis();

        for (UUID participant : participants) {
            if (participant == null) {
                continue;
            }

            if (!participant.equals(playerID)) {
                quest.setClaimed(participant, timestamp);
            }

            changes.markQuestDirty(participant, questID);
        }

        return changes;
    }

    @Nonnull
    public static QuestProgressResult detectQuest(@Nullable UUID questID, @Nullable IQuest quest,
        @Nonnull EntityPlayer player) {
        QuestProgressResult result = new QuestProgressResult();

        if (questID == null || quest == null) {
            return result;
        }

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        boolean wasComplete = quest.isComplete(playerID);

        quest.detect(player);

        result.markChanged(playerID, questID);
        result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));

        return result;
    }

    @Nonnull
    public static QuestProgressResult processActiveQuestProgress(@Nonnull EntityPlayer player,
        @Nonnull Map<UUID, IQuest> activeQuests) {
        QuestProgressResult result = new QuestProgressResult();
        UUID playerID = QuestingAPI.getQuestingUUID(player);

        for (Map.Entry<UUID, IQuest> entry : activeQuests.entrySet()) {
            UUID questID = entry.getKey();
            IQuest quest = entry.getValue();

            if (!quest.isUnlocked(playerID)) {
                continue;
            }

            boolean wasComplete = quest.isComplete(playerID);

            if (quest.canSubmit(player)) {
                quest.update(player);
            }

            result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));
        }

        return result;
    }

    @Nonnull
    public static QuestProgressResult processScheduledResets(@Nonnull EntityPlayer player,
        @Nonnull QuestCache.QResetTime[] pendingResets) {
        QuestProgressResult result = new QuestProgressResult();
        UUID playerID = QuestingAPI.getQuestingUUID(player);
        long totalTime = System.currentTimeMillis();

        for (QuestCache.QResetTime resetTime : pendingResets) {
            IQuest quest = QuestDatabase.INSTANCE.get(resetTime.questID);

            if (totalTime >= resetTime.time && !quest.canSubmit(player)) {
                if (quest.getProperty(NativeProps.GLOBAL)) {
                    quest.resetUser(null, false);
                } else {
                    quest.resetUser(playerID, false);
                }

                result.markReset(playerID, resetTime.questID);
            } else {
                break;
            }
        }

        return result;
    }

    @Nonnull
    public static QuestProgressResult processAutoClaims(@Nonnull EntityPlayer player,
        @Nonnull Map<UUID, IQuest> pendingAutoClaims, boolean includeSharedParticipants) {
        QuestProgressResult result = new QuestProgressResult();

        for (Map.Entry<UUID, IQuest> entry : pendingAutoClaims.entrySet()) {
            QuestChangeSet claimChanges = claimReward(
                entry.getKey(),
                entry.getValue(),
                player,
                false,
                includeSharedParticipants);

            if (!claimChanges.isEmpty()) {
                result.markChanged(entry.getKey(), claimChanges);
            }
        }

        return result;
    }

    @Nonnull
    private static QuestProgressResult propagateCompletionIfNeeded(@Nonnull UUID questID, @Nonnull IQuest quest,
        @Nonnull EntityPlayer player, boolean wasComplete) {
        QuestProgressResult result = new QuestProgressResult();

        UUID playerID = QuestingAPI.getQuestingUUID(player);

        if (wasComplete || !quest.isComplete(playerID) || quest.canSubmit(player)) {
            return result;
        }

        NBTTagCompound completionInfo = quest.getCompletionInfo(playerID);
        long completionTime = completionInfo != null ? completionInfo.getLong("timestamp") : System.currentTimeMillis();

        result.markCompleted(playerID, questID);

        for (UUID participant : QuestParticipantResolver.resolveQuestCompletionParticipants(player)) {
            if (participant == null) {
                continue;
            }

            if (!quest.isComplete(participant)) {
                quest.setComplete(participant, completionTime);
            }

            result.markChanged(participant, questID);
        }

        return result;
    }
}
