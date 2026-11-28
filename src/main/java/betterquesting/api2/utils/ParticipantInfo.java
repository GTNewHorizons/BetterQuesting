package betterquesting.api2.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.party.PartyManager;

public class ParticipantInfo {

    public final @NotNull EntityPlayer PLAYER;
    public final @NotNull UUID UUID;

    public final @NotNull List<UUID> ALL_UUIDS;
    public final @NotNull List<EntityPlayer> ACTIVE_PLAYERS;
    public final @NotNull List<UUID> ACTIVE_UUIDS;

    public final @Nullable DBEntry<IParty> PARTY_INSTANCE;

    public ParticipantInfo(@NotNull EntityPlayer player) {
        this.PLAYER = player;
        this.UUID = QuestingAPI.getQuestingUUID(player);
        this.PARTY_INSTANCE = PartyManager.INSTANCE.getParty(this.UUID);

        if (!player.worldObj.isRemote && !SaveLoadHandler.INSTANCE.ensurePlayerProgressLoaded(UUID)) {
            ALL_UUIDS = Collections.emptyList();
            ACTIVE_UUIDS = Collections.emptyList();
            ACTIVE_PLAYERS = Collections.emptyList();
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();

        if (PARTY_INSTANCE == null || server == null || player instanceof FakePlayer) {
            ACTIVE_PLAYERS = Collections.singletonList(player);
            ACTIVE_UUIDS = Collections.singletonList(UUID);
            ALL_UUIDS = Collections.singletonList(UUID);
            return;
        }

        ArrayList<EntityPlayer> activePlayers = new ArrayList<>();
        ArrayList<UUID> activePlayerIds = new ArrayList<>();
        List<UUID> members = PARTY_INSTANCE.getValue()
            .getMembers();
        List<UUID> participants = player.worldObj.isRemote ? new ArrayList<>(members)
            : SaveLoadHandler.INSTANCE.loadReadablePlayerProgress(members);

        for (UUID memberId : participants) {
            for (EntityPlayerMP playerMP : server.getConfigurationManager().playerEntityList) {
                if (playerMP.getGameProfile()
                    .getId()
                    .equals(memberId)) {
                    activePlayers.add(playerMP);
                    activePlayerIds.add(memberId);
                    break;
                }
            }
        }

        // Really shouldn't be modifying these lists anyway but just for safety
        this.ACTIVE_PLAYERS = Collections.unmodifiableList(activePlayers);
        this.ACTIVE_UUIDS = Collections.unmodifiableList(activePlayerIds);
        this.ALL_UUIDS = Collections.unmodifiableList(participants);
    }

    /** Players whose progress can receive an automated item or fluid submission. */
    public static List<UUID> getSubmissionParticipants(UUID owner) {
        if (!SaveLoadHandler.INSTANCE.ensurePlayerProgressLoaded(owner)) return Collections.emptyList();
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(owner);
        return party == null ? Collections.singletonList(owner)
            : SaveLoadHandler.INSTANCE.loadReadablePlayerProgress(
                party.getValue()
                    .getMembers());
    }

    /** Only marks quests dirty for the immediate participating player */
    public void markDirty(UUID questId) {
        QuestCache qc = (QuestCache) PLAYER.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc != null) {
            qc.markQuestDirty(questId);
        }
    }

    /** Marks quests as dirty for the entire (active) party */
    public void markDirtyParty(UUID questId) {
        ACTIVE_PLAYERS.forEach((value) -> {
            QuestCache qc = (QuestCache) value.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
            if (qc != null) {
                qc.markQuestDirty(questId);
            }
        });
    }

    /** Returns an array of all quests which one or more participants have unlocked */
    @NotNull
    public Set<UUID> getSharedQuests() {
        return ACTIVE_PLAYERS.stream()
            .map(p -> (QuestCache) p.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString()))
            .filter(Objects::nonNull)
            .map(QuestCache::getActiveQuests)
            .flatMap(Set::stream)
            .filter(id -> {
                IQuest quest = QuestDatabase.INSTANCE.get(id);
                return quest != null && SaveLoadHandler.INSTANCE.canUseQuestProgress(quest);
            })
            .collect(Collectors.toCollection(HashSet::new));
    }
}
