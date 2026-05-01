package betterquesting.api.questing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestAction {

    private final QuestActionType type;
    @Nullable
    private final QuestActionContext context;
    @Nullable
    private final Collection<UUID> targetPlayers;
    private final long completionTime;
    private final boolean markClaimed;
    private final boolean fullReset;

    private QuestAction(@Nonnull QuestActionType type, @Nullable QuestActionContext context,
        @Nullable Collection<UUID> targetPlayers, long completionTime, boolean markClaimed, boolean fullReset) {
        this.type = type;
        this.context = context;
        this.targetPlayers = targetPlayers == null ? null
            : Collections.unmodifiableList(new ArrayList<>(targetPlayers));
        this.completionTime = completionTime;
        this.markClaimed = markClaimed;
        this.fullReset = fullReset;
    }

    private static QuestAction forPlayer(@Nonnull QuestActionType type, @Nonnull QuestActionContext context) {
        return new QuestAction(type, context, null, 0, false, false);
    }

    @Nonnull
    public QuestActionType getType() {
        return type;
    }

    @Nonnull
    public QuestActionContext requireContext() {
        if (context == null) {
            throw new IllegalStateException(type + " does not have a player action context");
        }

        return context;
    }

    @Nonnull
    public Collection<UUID> getTargetPlayersOrEmpty() {
        return targetPlayers == null ? Collections.emptyList() : targetPlayers;
    }

    public boolean targetsAllPlayers() {
        return targetPlayers == null;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public boolean shouldMarkClaimed() {
        return markClaimed;
    }

    public boolean isFullReset() {
        return fullReset;
    }

    @Nonnull
    public static QuestAction updateProgress(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.UPDATE_PROGRESS, context);
    }

    @Nonnull
    public static QuestAction detect(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.DETECT, context);
    }

    @Nonnull
    public static QuestAction claimReward(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.CLAIM_REWARD, context);
    }

    @Nonnull
    public static QuestAction forceClaimReward(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.FORCE_CLAIM_REWARD, context);
    }

    @Nonnull
    public static QuestAction completeTask(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.COMPLETE_TASK, context);
    }

    @Nonnull
    public static QuestAction reset(@Nonnull QuestActionContext context) {
        return forPlayer(QuestActionType.RESET, context);
    }

    @Nonnull
    public static QuestAction backfillCompletion(@Nonnull Collection<UUID> targetPlayers, long completionTime,
        boolean markClaimed) {
        return new QuestAction(
            QuestActionType.BACKFILL_COMPLETION,
            null,
            targetPlayers,
            completionTime,
            markClaimed,
            false);
    }

    @Nonnull
    public static QuestAction resetUsers(@Nullable Collection<UUID> targetPlayers, boolean fullReset) {
        return new QuestAction(QuestActionType.RESET_USERS, null, targetPlayers, 0, false, fullReset);
    }

    @Nonnull
    public static QuestAction setCompleteForEdit(@Nonnull Collection<UUID> targetPlayers, long completionTime) {
        return new QuestAction(
            QuestActionType.SET_COMPLETE_FOR_EDIT,
            null,
            targetPlayers,
            completionTime,
            false,
            false);
    }
}
