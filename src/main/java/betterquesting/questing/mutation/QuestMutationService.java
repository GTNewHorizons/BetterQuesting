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

public final class QuestMutationService {

    private QuestMutationService() {}

    @Nonnull
    public static QuestMutationResult setTaskComplete(@Nullable UUID questID, @Nullable IQuest quest,
        @Nullable ITask task, @Nonnull EntityPlayer player, @Nonnull Collection<UUID> playerIDs) {
        QuestMutationResult result = new QuestMutationResult();

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
            result.markDirty(targetPlayerID, questID);
        }

        result.merge(propagateCompletionIfNeeded(questID, quest, player, wasComplete));

        return result;
    }

    @Nonnull
    public static QuestMutationResult claimReward(@Nullable UUID questID, @Nullable IQuest quest,
        @Nonnull EntityPlayer player, boolean forceChoice, boolean includeSharedParticipants) {
        QuestMutationResult result = new QuestMutationResult();

        if (questID == null || quest == null) {
            return result;
        }

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
            if (participant == null) {
                continue;
            }

            if (!participant.equals(playerID)) {
                quest.setClaimed(participant, timestamp);
            }

            result.markDirty(participant, questID);
        }

        return result;
    }

    @Nonnull
    public static QuestMutationResult detectQuest(@Nullable UUID questID, @Nullable IQuest quest,
        @Nonnull EntityPlayer player) {
        QuestMutationResult result = new QuestMutationResult();

        if (questID == null || quest == null) {
            return result;
        }

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
            if (participant == null) {
                continue;
            }

            if (!quest.isComplete(participant)) {
                quest.setComplete(participant, completionTime);
            }

            result.markDirty(participant, questID);
        }

        return result;
    }
}
