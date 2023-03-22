package betterquesting.api.questing;

import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.IUuidDatabase;
import net.minecraft.nbt.NBTTagList;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public interface IQuestLineDatabase extends IUuidDatabase<IQuestLine>, INBTPartial<NBTTagList, UUID>
{
	IQuestLine createNew(UUID lineID);
	
	/**
	 * Deletes quest from all quest lines
	 */
	void removeQuest(UUID questID);
	
	int getOrderIndex(UUID lineID);
	void setOrderIndex(UUID lineID, int index);

    /**
     * Sorry for the confusing naming! This method is basically the same as
     * {@link #orderedEntries()}, except that it returns a list instead of a stream.
     *
     * <p>Contents are ordered by the quest line display order.
     */
	List<Map.Entry<UUID, IQuestLine>> getOrderedEntries();

    /**
     * Clears the database, and then sets its entries to {@code entries}.
     * {@code entries} should be ordered, and will determine the quest line display order.
     */
    void setOrderedEntries(Collection<Entry<UUID, IQuestLine>> entries);

    /**
     * Sorry for the confusing naming! This method is basically the same as
     * {@link #getOrderedEntries()}, except that it returns a stream instead of a list.
     *
     * <p>Contents are ordered by the quest line display order.
     */
    @Override
    default Stream<Map.Entry<UUID, IQuestLine>> orderedEntries() {
        return getOrderedEntries().stream();
    }
}
