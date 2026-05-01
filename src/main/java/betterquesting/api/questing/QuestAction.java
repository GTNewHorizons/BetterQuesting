package betterquesting.api.questing;

import javax.annotation.Nonnull;

public final class QuestAction {

    public final QuestActionType type;
    public final QuestActionContext context;

    private QuestAction(@Nonnull QuestActionType type, @Nonnull QuestActionContext context) {
        this.type = type;
        this.context = context;
    }

    @Nonnull
    public static QuestAction updateProgress(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.UPDATE_PROGRESS, context);
    }

    @Nonnull
    public static QuestAction detect(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.DETECT, context);
    }

    @Nonnull
    public static QuestAction claimReward(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.CLAIM_REWARD, context);
    }

    @Nonnull
    public static QuestAction forceClaimReward(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.FORCE_CLAIM_REWARD, context);
    }

    @Nonnull
    public static QuestAction completeTask(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.COMPLETE_TASK, context);
    }

    @Nonnull
    public static QuestAction reset(@Nonnull QuestActionContext context) {
        return new QuestAction(QuestActionType.RESET, context);
    }
}
