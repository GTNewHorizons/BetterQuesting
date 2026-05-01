package betterquesting.questing.mutation;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;

import betterquesting.api2.utils.ParticipantInfo;

public final class QuestParticipantResolver {

    private QuestParticipantResolver() {}

    /**
     * Resolves the UUIDs affected by a quest mutation.
     *
     * When includeSharedParticipants is false, only the acting player is affected.
     * When true, all members known through ParticipantInfo are affected, including
     * offline party members.
     */
    @Nonnull
    public static List<UUID> resolvePlayerProgressParticipants(@Nonnull EntityPlayer player,
        boolean includeSharedParticipants) {
        ParticipantInfo participantInfo = new ParticipantInfo(player);

        if (includeSharedParticipants) {
            return participantInfo.ALL_UUIDS;
        }

        return Collections.singletonList(participantInfo.UUID);
    }

    @Nonnull
    public static List<UUID> resolveQuestCompletionParticipants(@Nonnull EntityPlayer player) {
        return new ParticipantInfo(player).ALL_UUIDS;
    }
}
