package betterquesting.api.events;

import cpw.mods.fml.common.eventhandler.Event;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class QuestEvent extends Event
{
    private final Type type;
	private final UUID playerID;
	private final Set<UUID> questIDs;
	
	public Set<UUID> getQuestIDs()
	{
		return this.questIDs;
	}
	
	public UUID getPlayerID()
	{
		return this.playerID;
	}
	
	public Type getType()
    {
        return this.type;
    }
	
	public QuestEvent(Type type, UUID playerID, UUID questID)
	{
	    this.type = type;
		this.playerID = playerID;
	    this.questIDs = Collections.singleton(questID);
	}
	
	public QuestEvent(Type type, UUID playerID, Collection<UUID> questIDs)
	{
	    this.type = type;
		this.playerID = playerID;
	    this.questIDs = Collections.unmodifiableSet(new HashSet<>(questIDs));
	}
	
	public enum Type
    {
        COMPLETED,
        UPDATED,
        RESET
    }
}
