package betterquesting.questing.mutation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.cache.QuestCache;
import betterquesting.questing.QuestDatabase;

public final class QuestMutationService {

    private QuestMutationService() {}

    /**
     * Marks an already-validated task complete for the selected players.
     * <p>
     * Task-specific validation stays with the caller. This method owns the common
     * mutation result bookkeeping and completion propagation.
     */
    @Nonnull
    public static QuestMutationResult setTaskComplete(@Nonnull UUID questID, @Nonnull IQuest quest, @Nonnull ITask task,
        @Nonnull EntityPlayer player, @Nonnull Collection<UUID> playerIDs) {
        QuestMutationResult result = new QuestMutationResult();

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        boolean wasComplete = quest.isComplete(playerID);

        for (UUID targetPlayerID : playerIDs) {
            task.setComplete(targetPlayerID);
            result.markDirty(targetPlayerID, questID);
        }

        result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));

        return result;
    }

    @Nonnull
    public static QuestMutationResult claimReward(@Nonnull UUID questID, @Nonnull IQuest quest,
        @Nonnull EntityPlayer player, boolean forceChoice, boolean includeSharedParticipants) {
        QuestMutationResult result = new QuestMutationResult();

        boolean canClaim = forceChoice ? quest.canClaim(player, true) : quest.canClaim(player);
        if (!canClaim) {
            return result;
        }

        quest.claimReward(player, forceChoice);

        List<UUID> participants = QuestParticipantResolver
            .resolvePlayerProgressParticipants(player, includeSharedParticipants);

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        long timestamp = System.currentTimeMillis();

        for (UUID participant : participants) {
            if (!participant.equals(playerID)) {
                quest.setClaimed(participant, timestamp);
            }

            result.markDirty(participant, questID);
        }

        return result;
    }

    @Nonnull
    public static QuestMutationResult detectQuest(@Nonnull UUID questID, @Nonnull IQuest quest,
        @Nonnull EntityPlayer player) {
        QuestMutationResult result = new QuestMutationResult();

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        boolean wasComplete = quest.isComplete(playerID);

        quest.detect(player);

        result.markDirty(playerID, questID);
        result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));

        return result;
    }

    @Nonnull
    public static QuestMutationResult processActiveQuestProgress(@Nonnull EntityPlayer player,
        @Nonnull QuestCache cache) {
        QuestMutationResult result = new QuestMutationResult();
        UUID playerID = QuestingAPI.getQuestingUUID(player);

        Map<UUID, IQuest> activeQuests = QuestDatabase.INSTANCE.filterKeys(cache.getActiveQuests());

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
    public static QuestMutationResult processScheduledResets(@Nonnull EntityPlayer player, @Nonnull QuestCache cache) {
        QuestMutationResult result = new QuestMutationResult();
        UUID playerID = QuestingAPI.getQuestingUUID(player);
        long totalTime = System.currentTimeMillis();

        for (QuestCache.QResetTime resetTime : cache.getScheduledResets()) {
            if (totalTime < resetTime.time) {
                break;
            }

            IQuest quest = QuestDatabase.INSTANCE.get(resetTime.questID);
            if (quest == null) {
                continue;
            }

            if (!quest.canSubmit(player)) {
                if (quest.getProperty(NativeProps.GLOBAL)) {
                    quest.resetUser(null, false);
                } else {
                    quest.resetUser(playerID, false);
                }

                result.markReset(playerID, resetTime.questID);
            }
        }

        return result;
    }

    @Nonnull
    public static QuestMutationResult processAutoClaims(@Nonnull EntityPlayer player, @Nonnull QuestCache cache,
        boolean includeSharedParticipants) {
        QuestMutationResult result = new QuestMutationResult();
        Map<UUID, IQuest> pendingAutoClaims = QuestDatabase.INSTANCE.filterKeys(cache.getPendingAutoClaims());

        for (Map.Entry<UUID, IQuest> entry : pendingAutoClaims.entrySet()) {
            result.merge(claimReward(entry.getKey(), entry.getValue(), player, false, includeSharedParticipants));
        }

        return result;
    }

    /**
     * Applies the live party-completion rule.
     * <p>
     * This is the core LAN/multiplayer fix: when a mutation newly completes a
     * quest for the actor, completion is propagated immediately to party
     * participants instead of relying on login-time repair.
     * <p>
     * This only propagates completion state. It does not grant rewards.
     */
    @Nonnull
    private static QuestMutationResult propagateCompletionIfNeeded(@Nonnull UUID questID, @Nonnull IQuest quest,
        @Nonnull EntityPlayer player, boolean wasComplete) {
        QuestMutationResult result = new QuestMutationResult();

        UUID playerID = QuestingAPI.getQuestingUUID(player);

        if (wasComplete || !quest.isComplete(playerID) || quest.canSubmit(player)) {
            return result;
        }

        NBTTagCompound completionInfo = quest.getCompletionInfo(playerID);
        long completionTime = completionInfo != null ? completionInfo.getLong("timestamp") : System.currentTimeMillis();

        result.markCompleted(playerID, questID);

        for (UUID participant : QuestParticipantResolver.resolveQuestCompletionParticipants(player)) {
            if (!quest.isComplete(participant)) {
                quest.setComplete(participant, completionTime);
            }

            result.markDirty(participant, questID);
        }

        return result;
    }
}
