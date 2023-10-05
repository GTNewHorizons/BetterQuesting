package betterquesting.api2.utils;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.questing.party.PartyManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ParticipantInfo
{
    public final EntityPlayer PLAYER;
    public final UUID UUID;
    
    public final List<UUID> ALL_UUIDS;
    public final List<EntityPlayer> ACTIVE_PLAYERS;
    public final List<UUID> ACTIVE_UUIDS;
    
    public final DBEntry<IParty> PARTY_INSTANCE;
    
    public ParticipantInfo(@Nonnull EntityPlayer player)
    {
        this.PLAYER = player;
        this.UUID = QuestingAPI.getQuestingUUID(player);
        this.PARTY_INSTANCE = PartyManager.INSTANCE.getParty(this.UUID);
        
        MinecraftServer server = MinecraftServer.getServer();
        
        if(PARTY_INSTANCE == null || server == null || player instanceof FakePlayer)
        {
            ACTIVE_PLAYERS = Collections.singletonList(player);
            ACTIVE_UUIDS = Collections.singletonList(UUID);
            ALL_UUIDS = Collections.singletonList(UUID);
            return;
        }
        
        List<EntityPlayer> actPl = new ArrayList<>();
        List<UUID> actID = new ArrayList<>();
        List<UUID> allID = new ArrayList<>();
        
        for(UUID mem : PARTY_INSTANCE.getValue().getMembers())
        {
            allID.add(mem);
            
            EntityPlayer pMem = null;
            for(Object o : server.getConfigurationManager().playerEntityList)
            {
                if(((EntityPlayer)o).getGameProfile().getId().equals(mem))
                {
                    pMem = (EntityPlayer)o;
                }
            }
            
            if(pMem != null)
            {
                actPl.add(pMem);
                actID.add(mem);
            }
        }
        
        // Really shouldn't be modifying these lists anyway but just for safety
        this.ACTIVE_PLAYERS = Collections.unmodifiableList(actPl);
        this.ACTIVE_UUIDS = Collections.unmodifiableList(actID);
        this.ALL_UUIDS = Collections.unmodifiableList(allID);
    }
    
    public void markDirty(UUID questId) // Only marks quests dirty for the immediate participating player
    {
        QuestCache qc = (QuestCache) PLAYER.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc != null)
        {
            qc.markQuestDirty(questId);
        }
    }
    
    public void markDirtyParty(UUID questId) // Marks quests as dirty for the entire (active) party
    {
        ACTIVE_PLAYERS.forEach((value) -> {
            QuestCache qc = (QuestCache) value.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
            if (qc != null)
            {
                qc.markQuestDirty(questId);
            }
        });
    }
    
    @Nonnull
    public Set<UUID> getSharedQuests() // Returns an array of all quests which one or more participants have unlocked
    {
        return ACTIVE_PLAYERS.stream()
                .map(p -> (QuestCache) p.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString()))
                .filter(Objects::nonNull)
                .map(QuestCache::getActiveQuests)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
