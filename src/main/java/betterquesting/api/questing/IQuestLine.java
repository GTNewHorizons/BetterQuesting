package betterquesting.api.questing;

import betterquesting.api.properties.IPropertyContainer;
import betterquesting.api2.storage.INBTPartial;
import betterquesting.api2.storage.IUuidDatabase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import java.util.UUID;

public interface IQuestLine extends IUuidDatabase<IQuestLineEntry>, INBTPartial<NBTTagCompound, Integer>, IPropertyContainer
{
    IQuestLineEntry createNew(UUID uuid);
    
	String getUnlocalisedName();

	String getUnlocalisedDescription();
	
	Map.Entry<UUID, IQuestLineEntry> getEntryAt(int x, int y);

    /**
     * Variant of {@link #writeToNBT(NBTBase)} which allows skipping writing quests.
     *
     * <p>The reason why we want to skip writing quests is that, when exporting the quest database,
     * we want to try to avoid merge conflicts. The fact that quests are exported to NBT in
     * sequential order (as an {@code NBTTagList}) makes this format particularly prone to merge
     * conflicts.
     */
    NBTTagCompound writeToNBT(NBTTagCompound json, boolean skipQuests);
}
