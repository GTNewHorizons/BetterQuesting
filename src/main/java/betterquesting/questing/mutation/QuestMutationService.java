package betterquesting.questing.mutation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
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
    public static QuestChangeSet setTaskComplete(@Nullable UUID questID, @Nullable ITask task,
        @Nonnull Collection<UUID> playerIDs) {
        QuestChangeSet changes = new QuestChangeSet();

        if (questID == null || task == null) {
            return changes;
        }

        for (UUID playerID : playerIDs) {
            if (playerID == null) {
                continue;
            }

            task.setComplete(playerID);
            changes.markQuestDirty(playerID, questID);
        }

        return changes;
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
    public static QuestChangeSet detectQuest(@Nullable UUID questID, @Nullable IQuest quest,
        @Nonnull EntityPlayer player) {
        QuestChangeSet changes = new QuestChangeSet();

        if (questID == null || quest == null) {
            return changes;
        }

        quest.detect(player);

        UUID playerID = QuestingAPI.getQuestingUUID(player);
        changes.markQuestDirty(playerID, questID);

        return changes;
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

            if (quest.canSubmit(player)) {
                quest.update(player);
            }

            if (quest.isComplete(playerID) && !quest.canSubmit(player)) {
                result.markCompleted(playerID, questID);
            }
        }

        return result;
    }
}
