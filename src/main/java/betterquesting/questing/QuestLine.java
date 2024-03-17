package betterquesting.questing;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.IPropertyContainer;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.storage.UuidDatabase;
import betterquesting.storage.PropertyContainer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class QuestLine extends UuidDatabase<IQuestLineEntry> implements IQuestLine
{
	private IPropertyContainer info = new PropertyContainer();
	
	private IQuestLineDatabase parentDB;
	
	public QuestLine()
	{
		parentDB = QuestingAPI.getAPI(ApiReference.LINE_DB);
		
		setupProps();
	}
	
	private void setupProps()
	{
		this.setupValue(NativeProps.NAME, "New Quest Line");
		this.setupValue(NativeProps.DESC, "No Description");
		this.setupValue(NativeProps.ICON, new BigItemStack(Items.book));
		this.setupValue(NativeProps.VISIBILITY, EnumQuestVisibility.NORMAL);
		this.setupValue(NativeProps.BG_IMAGE);
		this.setupValue(NativeProps.BG_SIZE);
	}
	
	private <T> void setupValue(IPropertyType<T> prop)
	{
		this.setupValue(prop, prop.getDefault());
	}
	
	private <T> void setupValue(IPropertyType<T> prop, T def)
	{
		info.setProperty(prop, info.getProperty(prop, def));
	}
	
	@Override
    public IQuestLineEntry createNew(UUID uuid)
    {
        IQuestLineEntry qle = new QuestLineEntry(0, 0, 24, 24);
        this.put(uuid, qle);
        return qle;
    }
	
	@Override
	public String getUnlocalisedName()
	{
		String def = "New Quest Line";
		
		if(!info.hasProperty(NativeProps.NAME))
		{
			info.setProperty(NativeProps.NAME, def);
			return def;
		}
		
		return info.getProperty(NativeProps.NAME, def);
	}
	
	@Override
	public String getUnlocalisedDescription()
	{
		String def = "No Description";
		
		if(!info.hasProperty(NativeProps.DESC))
		{
			info.setProperty(NativeProps.DESC, def);
			return def;
		}
		
		return info.getProperty(NativeProps.DESC, def);
	}
	
	@Override
	public Map.Entry<UUID, IQuestLineEntry> getEntryAt(int x, int y)
	{
		for (Map.Entry<UUID, IQuestLineEntry> entry : entrySet())
		{
			int i1 = entry.getValue().getPosX();
			int j1 = entry.getValue().getPosY();
			int i2 = i1 + entry.getValue().getSizeX();
			int j2 = j1 + entry.getValue().getSizeY();
			
			if(x >= i1 && x < i2 && y >= j1 && y < j2)
			{
				return entry;
			}
		}
		
		return null;
	}
	
	@Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt)
    {
        return writeToNBT(nbt, false);
    }
    
    @Override
    public void readFromNBT(NBTTagCompound nbt)
    {
        readFromNBT(nbt, false);
    }
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound json, @Nullable List<Integer> subset)
	{
        if (subset != null)
        {
            throw new UnsupportedOperationException("subset not supported");
        }

        return writeToNBT(json, false);
	}

    /**
     * The reason why we want to skip writing quests is that, when exporting the quest database, we
     * want to try to avoid merge conflicts. The fact that quests are exported in a sequential order
     * (as an {@code NBTTagList}) makes this format particularly prone to merge conflicts.
     *
     * <p>Instead of using the exported NBT to find quests belonging to a quest line, we will find
     * them by subdirectory within the exported quests directory.
     */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound json, boolean skipQuests)
    {
        json.setTag("properties", info.writeToNBT(new NBTTagCompound()));

        if (!skipQuests) {
            NBTTagList jArr = new NBTTagList();

            orderedEntries().forEach(entry ->
            {
                NBTTagCompound qle = entry.getValue().writeToNBT(new NBTTagCompound());
                NBTConverter.UuidValueType.QUEST.writeId(entry.getKey(), qle);
                jArr.appendTag(qle);
            });

            json.setTag("quests", jArr);
        }

        return json;
    }

	@Override
	public void readFromNBT(NBTTagCompound json, boolean merge)
	{
		info.readFromNBT(json.getCompoundTag("properties"));
		
		if (!merge)
        {
            clear();
        }
		
		NBTTagList qList = json.getTagList("quests", 10);
		for (int i = 0; i < qList.tagCount(); i++)
		{
			NBTTagCompound qTag = qList.getCompoundTagAt(i);

            Optional<UUID> questIDOptional = NBTConverter.UuidValueType.QUEST.tryReadId(qTag);
            UUID questID;
            if (questIDOptional.isPresent())
            {
                questID = questIDOptional.get();
            }
            else if (qTag.hasKey("id", 99))
            {
                // This block is needed for old questbook data.
                questID = UuidConverter.convertLegacyId(qTag.getInteger("id"));
            }
            else
            {
                continue;
            }

			put(questID, new QuestLineEntry(qTag));
		}
		
		this.setupProps();
	}
    
    @Override
    public <T> T getProperty(IPropertyType<T> prop)
    {
        return info.getProperty(prop);
    }
    
    @Override
    public <T> T getProperty(IPropertyType<T> prop, T def)
    {
        return info.getProperty(prop, def);
    }
    
    @Override
    public boolean hasProperty(IPropertyType<?> prop)
    {
        return info.hasProperty(prop);
    }
    
    @Override
    public <T> void setProperty(IPropertyType<T> prop, T value)
    {
        info.setProperty(prop, value);
    }
    
    @Override
    public void removeProperty(IPropertyType<?> prop)
    {
        info.removeProperty(prop);
    }
    
    @Override
    public void removeAllProps()
    {
        info.removeAllProps();
    }
}
