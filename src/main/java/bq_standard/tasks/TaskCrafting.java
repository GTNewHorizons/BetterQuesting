package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.tasks.PanelTaskCrafting;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.factory.FactoryTaskCrafting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.IntSupplier;

public class TaskCrafting implements ITask
{
	private final Set<UUID> completeUsers = new TreeSet<>();
	public final List<BigItemStack> requiredItems = new ArrayList<>();
	public final TreeMap<UUID, int[]> userProgress = new TreeMap<>();
	public boolean partialMatch = true;
	public boolean ignoreNBT = true;
	public boolean allowAnvil = false;
	public boolean allowSmelt = true;
	public boolean allowCraft = true;
	
	@Override
	public ResourceLocation getFactoryID()
	{
		return FactoryTaskCrafting.INSTANCE.getRegistryName();
	}
	
	@Override
	public boolean isComplete(UUID uuid)
	{
		return completeUsers.contains(uuid);
	}
	
	@Override
	public void setComplete(UUID uuid)
	{
        ProgressUtil.setComplete(uuid, completeUsers);
	}
	
	@Override
	public String getUnlocalisedName()
	{
		return "bq_standard.task.crafting";
	}
	
	@Override
	public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest)
	{
	    pInfo.ALL_UUIDS.forEach((uuid) -> {
            if(isComplete(uuid)) return;
            
            int[] tmp = getUsersProgress(uuid);
            for(int i = 0; i < requiredItems.size(); i++)
            {
                BigItemStack rStack = requiredItems.get(i);
                if(tmp[i] < rStack.stackSize) return;
            }
            setComplete(uuid);
        });
	    
	    pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
	}
	
	public void onItemCraft(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack stack, IntSupplier realStackSizeSupplier)
    {
        if(!allowCraft) return;
        onItemInternal(pInfo, quest, stack, realStackSizeSupplier);
    }
	
	public void onItemSmelt(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack stack)
    {
        if(!allowSmelt) return;
        onItemInternal(pInfo, quest, stack);
    }
	
	public void onItemAnvil(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack stack)
    {
        if(!allowAnvil) return;
        onItemInternal(pInfo, quest, stack);
    }

	private void onItemInternal(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack stack) {
		onItemInternal(pInfo, quest, stack, null);
	}

	private void onItemInternal(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack stack, IntSupplier realStackSizeSupplier)
	{
		// ignore null stack
		// ignore negatively sized stack only if it's indeed the real stack size
	    if(stack == null || (stack.stackSize <= 0 && realStackSizeSupplier == null)) return;
		
        final List<Tuple2<UUID, int[]>> progress = getBulkProgress(pInfo.ALL_UUIDS);
        boolean changed = false;
		int realStackSizeCache = realStackSizeSupplier == null ? Math.max(0, stack.stackSize) : -1;
        
		for(int i = 0; i < requiredItems.size(); i++)
		{
			final BigItemStack rStack = requiredItems.get(i);
			final int index = i;
			
			if(ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch) || ItemComparison.OreDictionaryMatch(rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch))
			{
				int realStackSize;
				if(realStackSizeCache < 0)
				{
					realStackSize = realStackSizeSupplier.getAsInt();
					if (realStackSize <= 0)
						// bruh
						return;
					realStackSizeCache = realStackSize;
				} else
				{
					realStackSize = realStackSizeCache;
				}
				progress.stream()
						.filter(e -> e.getSecond()[index] < rStack.stackSize)
						.forEach(e -> e.getSecond()[index] = Math.min(e.getSecond()[index] + realStackSize, rStack.stackSize));
			    changed = true;
			}
		}
		
		if(changed)
        {
		    setBulkProgress(progress);
            detect(pInfo, quest);
        }
	}
	
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt)
	{
		nbt.setBoolean("partialMatch", partialMatch);
		nbt.setBoolean("ignoreNBT", ignoreNBT);
		nbt.setBoolean("allowCraft", allowCraft);
		nbt.setBoolean("allowSmelt", allowSmelt);
		nbt.setBoolean("allowAnvil", allowAnvil);
		
		NBTTagList itemArray = new NBTTagList();
		for(BigItemStack stack : this.requiredItems)
		{
			itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound()));
		}
		nbt.setTag("requiredItems", itemArray);
		
		return nbt;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		partialMatch = nbt.getBoolean("partialMatch");
		ignoreNBT = nbt.getBoolean("ignoreNBT");
		if(nbt.hasKey("allowCraft")) allowCraft = nbt.getBoolean("allowCraft");
		if(nbt.hasKey("allowSmelt")) allowSmelt = nbt.getBoolean("allowSmelt");
		if(nbt.hasKey("allowAnvil")) allowAnvil = nbt.getBoolean("allowAnvil");
		
		requiredItems.clear();
		NBTTagList iList = nbt.getTagList("requiredItems", 10);
		for(int i = 0; i < iList.tagCount(); i++)
		{
		    requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
		}
	}
	
	@Override
	public void readProgressFromNBT(NBTTagCompound nbt, boolean merge)
	{
		if(!merge)
        {
            completeUsers.clear();
            userProgress.clear();
        }
		
		NBTTagList cList = nbt.getTagList("completeUsers", 8);
		for(int i = 0; i < cList.tagCount(); i++)
		{
			try
			{
				completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
			} catch(Exception e)
			{
				BQ_Standard.logger.log(Level.ERROR, "Unable to load UUID for task", e);
			}
		}
		
		NBTTagList pList = nbt.getTagList("userProgress", 10);
		for(int n = 0; n < pList.tagCount(); n++)
		{
			NBTTagCompound pTag = pList.getCompoundTagAt(n);
			UUID uuid;
			try
			{
				uuid = UUID.fromString(pTag.getString("uuid"));
			} catch(Exception e)
			{
				BQ_Standard.logger.log(Level.ERROR, "Unable to load user progress for task", e);
				continue;
			}
			
			int[] data = new int[requiredItems.size()];
			List<NBTBase> dJson = NBTConverter.getTagList(pTag.getTagList("data", 3));
			for(int i = 0; i < data.length && i < dJson.size(); i++)
			{
				try
				{
					data[i] = ((NBTPrimitive)dJson.get(i)).func_150287_d();
				} catch(Exception e)
				{
					BQ_Standard.logger.log(Level.ERROR, "Incorrect task progress format", e);
				}
			}
			
			userProgress.put(uuid, data);
		}
	}
	
	@Override
	public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, List<UUID> users)
	{
		NBTTagList jArray = new NBTTagList();
		NBTTagList progArray = new NBTTagList();
		
		if(users != null)
        {
            users.forEach((uuid) -> {
                if(completeUsers.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));
                
                int[] data = userProgress.get(uuid);
                if(data != null)
                {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                    for(int i : data) pArray.appendTag(new NBTTagInt(i));
                    pJson.setTag("data", pArray);
                    progArray.appendTag(pJson);
                }
            });
        } else
        {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));
            
            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
			    pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList(); // TODO: Why the heck isn't this just an int array?!
                for(int i : data) pArray.appendTag(new NBTTagInt(i));
                pJson.setTag("data", pArray);
                progArray.appendTag(pJson);
            });
        }
		
		nbt.setTag("completeUsers", jArray);
		nbt.setTag("userProgress", progArray);
		
		return nbt;
	}

	@Override
	public void resetUser(@Nullable UUID uuid)
	{
        ProgressUtil.resetUser(uuid, completeUsers, userProgress);
	}
 
	@Override
	public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> context)
	{
	    return new PanelTaskCrafting(rect, this);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest)
	{
		return null;
	}
	
	private void setUserProgress(UUID uuid, int[] progress)
	{
        ProgressUtil.setUserProgress(uuid, userProgress, progress);
	}
	
	public int[] getUsersProgress(UUID uuid)
	{
		int[] progress = userProgress.get(uuid);
		return progress == null || progress.length != requiredItems.size()? new int[requiredItems.size()] : progress;
	}
	
	private List<Tuple2<UUID, int[]>> getBulkProgress(@Nonnull List<UUID> uuids)
    {
        if(uuids.size() <= 0) return Collections.emptyList();
        List<Tuple2<UUID, int[]>> list = new ArrayList<>();
        uuids.forEach((key) -> list.add(new Tuple2<>(key, getUsersProgress(key))));
        return list;
    }
    
    private void setBulkProgress(@Nonnull List<Tuple2<UUID, int[]>> list)
    {
        list.forEach((entry) -> setUserProgress(entry.getFirst(), entry.getSecond()));
    }
}
