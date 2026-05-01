package betterquesting.questing.sync;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api2.cache.QuestCache;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.mutation.QuestMutationResult;

public final class QuestSyncService {

    private QuestSyncService() {}

    public static void markQuestDirty(@Nullable UUID playerID, @Nonnull UUID questID) {
        if (playerID == null) {
            return;
        }

        EntityPlayerMP player = QuestingAPI.getPlayer(playerID);
        if (player == null) {
            return;
        }

        QuestCache cache = getQuestCache(player);
        if (cache != null) {
            cache.markQuestDirty(questID);
        }
    }

    public static void markQuestDirty(@Nonnull Collection<UUID> playerIDs, @Nonnull UUID questID) {
        for (UUID playerID : playerIDs) {
            markQuestDirty(playerID, questID);
        }
    }

    public static void applyMutationResult(@Nullable QuestMutationResult result) {
        if (result == null || !result.hasChanges()) {
            return;
        }

        Map<UUID, Set<UUID>> dirtyQuestsByPlayer = result.getDirtyQuestsByPlayer();

        for (Map.Entry<UUID, Set<UUID>> entry : dirtyQuestsByPlayer.entrySet()) {
            UUID playerID = entry.getKey();

            for (UUID questID : entry.getValue()) {
                markQuestDirty(playerID, questID);
            }
        }

        for (UUID playerID : dirtyQuestsByPlayer.keySet()) {
            if (playerID == null) {
                continue;
            }

            EntityPlayerMP player = QuestingAPI.getPlayer(playerID);
            if (player == null) {
                continue;
            }

            QuestCache cache = getQuestCache(player);
            if (cache == null) {
                continue;
            }

            cache.updateCache(player);
            flushDirtyQuestProgress(player);
        }
    }

    public static void flushDirtyQuestProgress(@Nonnull EntityPlayerMP player) {
        QuestCache cache = getQuestCache(player);
        if (cache == null) {
            return;
        }

        Collection<UUID> dirtyQuests = cache.getDirtyQuests();

        try {
            if (!dirtyQuests.isEmpty()) {
                NetQuestSync.sendSync(player, dirtyQuests, false, true, true);
            }
        } finally {
            cache.cleanAllQuests();
        }
    }

    @Nullable
    private static QuestCache getQuestCache(@Nonnull EntityPlayer player) {
        return (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
    }
}
