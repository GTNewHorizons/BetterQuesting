package betterquesting.api.questing;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;

import betterquesting.api2.utils.ParticipantInfo;

public final class QuestActionContext {

    public final EntityPlayer player;
    public final UUID actorID;
    public final long timestamp;

    public final List<UUID> completionParticipants;
    public final List<UUID> rewardClaimParticipants;

    @Nullable
    public final Integer taskID;

    @Nonnull
    public final Collection<UUID> taskParticipants;

    private QuestActionContext(@Nonnull EntityPlayer player, @Nonnull UUID actorID, long timestamp,
        @Nonnull List<UUID> completionParticipants, @Nonnull List<UUID> rewardClaimParticipants,
        @Nullable Integer taskID, @Nonnull Collection<UUID> taskParticipants) {
        this.player = player;
        this.actorID = actorID;
        this.timestamp = timestamp;
        this.completionParticipants = completionParticipants;
        this.rewardClaimParticipants = rewardClaimParticipants;
        this.taskID = taskID;
        this.taskParticipants = taskParticipants;
    }

    @Nonnull
    public static QuestActionContext forPlayer(@Nonnull EntityPlayer player, boolean shareRewardClaims) {
        ParticipantInfo participantInfo = new ParticipantInfo(player);

        return new QuestActionContext(
            player,
            participantInfo.UUID,
            System.currentTimeMillis(),
            participantInfo.ALL_UUIDS,
            shareRewardClaims ? participantInfo.ALL_UUIDS : Collections.singletonList(participantInfo.UUID),
            null,
            Collections.singletonList(participantInfo.UUID));
    }

    @Nonnull
    public QuestActionContext withTask(@Nonnull Integer taskID, @Nonnull Collection<UUID> taskParticipants) {
        return new QuestActionContext(
            player,
            actorID,
            timestamp,
            completionParticipants,
            rewardClaimParticipants,
            taskID,
            taskParticipants);
    }
}
