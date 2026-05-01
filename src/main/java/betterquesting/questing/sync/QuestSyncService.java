package betterquesting.questing.sync;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.QuestMutationResult;
import betterquesting.api2.cache.QuestCache;
import betterquesting.network.handlers.NetQuestSync;
import cpw.mods.fml.common.FMLCommonHandler;

public final class QuestSyncService {

    private QuestSyncService() {}

    public static void markQuestDirty(@Nonnull UUID playerID, @Nonnull UUID questID) {
        EntityPlayerMP player = QuestingAPI.getPlayer(playerID);
        if (player == null) {
            return;
        }

        QuestCache cache = getQuestCache(player);
        if (cache != null) {
            cache.markQuestDirty(questID);
        }
    }

    public static void markQuestDirty(@Nonnull Iterable<UUID> playerIDs, @Nonnull UUID questID) {
        for (UUID playerID : playerIDs) {
            markQuestDirty(playerID, questID);
        }
    }

    /**
     * Applies a mutation result to online clients.
     * <p>
     * Cache refresh happens before progress flushing because quest visibility,
     * active quest lists, pending auto-claims, and reward state are derived from
     * the newly mutated quest progress.
     */
    public static void applyMutationResult(@Nonnull QuestMutationResult result) {
        if (!result.hasChanges()) {
            return;
        }

        Map<UUID, Set<UUID>> dirtyQuestsByPlayer = result.getDirtyQuestsByPlayer();
        Set<UUID> globallyDirtyQuests = result.getDirtyQuestsForAllOnlinePlayers();
        HashSet<UUID> playersToRefresh = new HashSet<>();

        for (Map.Entry<UUID, Set<UUID>> entry : dirtyQuestsByPlayer.entrySet()) {
            UUID playerID = entry.getKey();

            for (UUID questID : entry.getValue()) {
                markQuestDirty(playerID, questID);
            }

            playersToRefresh.add(playerID);
        }

        if (!globallyDirtyQuests.isEmpty()) {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();
            if (server != null) {
                for (Object onlinePlayer : server.getConfigurationManager().playerEntityList) {
                    EntityPlayerMP player = (EntityPlayerMP) onlinePlayer;
                    UUID playerID = QuestingAPI.getQuestingUUID(player);
                    QuestCache cache = getQuestCache(player);
                    if (cache == null) {
                        continue;
                    }

                    for (UUID questID : globallyDirtyQuests) {
                        cache.markQuestDirty(questID);
                    }

                    playersToRefresh.add(playerID);
                }
            }
        }

        for (UUID playerID : playersToRefresh) {
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
