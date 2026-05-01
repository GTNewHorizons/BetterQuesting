package betterquesting.questing.mutation;

import java.util.Collection;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.QuestAction;
import betterquesting.api.questing.QuestActionContext;
import betterquesting.api.questing.QuestMutationResult;
import betterquesting.api2.cache.QuestCache;
import betterquesting.questing.QuestDatabase;

public final class QuestMutationService {

    private QuestMutationService() {}

    public static QuestMutationResult processActiveQuestProgress(EntityPlayer player, QuestCache cache) {
        QuestActionContext context = QuestActionContext.forPlayer(player, false);
        QuestMutationResult result = new QuestMutationResult();

        for (IQuest quest : QuestDatabase.INSTANCE.filterKeys(cache.getActiveQuests())
            .values()) {
            result.merge(quest.applyAction(QuestAction.updateProgress(context)));
        }

        return result;
    }

    public static QuestMutationResult detectQuest(IQuest quest, EntityPlayer player) {
        QuestActionContext context = QuestActionContext.forPlayer(player, false);
        return quest.applyAction(QuestAction.detect(context));
    }

    public static QuestMutationResult claimReward(IQuest quest, EntityPlayer player, boolean forceChoice,
        boolean includeSharedParticipants) {
        QuestActionContext context = QuestActionContext.forPlayer(player, includeSharedParticipants);
        return quest
            .applyAction(forceChoice ? QuestAction.forceClaimReward(context) : QuestAction.claimReward(context));
    }

    public static QuestMutationResult setTaskComplete(IQuest quest, int taskID, EntityPlayer player,
        Collection<UUID> playerIDs) {
        QuestActionContext context = QuestActionContext.forPlayer(player, false)
            .withTask(taskID, playerIDs);

        return quest.applyAction(QuestAction.completeTask(context));
    }

    public static QuestMutationResult processAutoClaims(EntityPlayer player, QuestCache cache,
        boolean includeSharedParticipants) {
        QuestActionContext context = QuestActionContext.forPlayer(player, includeSharedParticipants);
        QuestMutationResult result = new QuestMutationResult();

        for (IQuest quest : QuestDatabase.INSTANCE.filterKeys(cache.getPendingAutoClaims())
            .values()) {
            result.merge(quest.applyAction(QuestAction.claimReward(context)));
        }

        return result;
    }

    public static QuestMutationResult processScheduledResets(EntityPlayer player, QuestCache cache) {
        QuestActionContext context = QuestActionContext.forPlayer(player, false);
        QuestMutationResult result = new QuestMutationResult();

        for (QuestCache.QResetTime resetTime : cache.getScheduledResets()) {
            if (context.timestamp < resetTime.time) {
                break;
            }

            IQuest quest = QuestDatabase.INSTANCE.get(resetTime.questID);
            if (quest != null) {
                result.merge(quest.applyAction(QuestAction.reset(context)));
            }
        }

        return result;
    }
}
