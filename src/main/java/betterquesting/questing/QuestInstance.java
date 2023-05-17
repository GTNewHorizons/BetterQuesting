package betterquesting.questing;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.IPropertyType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.storage.IDatabaseNBT;
import betterquesting.api2.utils.DirtyPlayerMarker;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.rewards.RewardStorage;
import betterquesting.questing.tasks.TaskStorage;
import betterquesting.storage.PropertyContainer;
import betterquesting.storage.QuestSettings;
import com.google.common.collect.Maps;
import drethic.questbook.config.QBConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.Level;
import scala.collection.parallel.Task;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class QuestInstance implements IQuest
{
	private final TaskStorage tasks = new TaskStorage();
    private boolean hasConsumableTasks = false;
	private final RewardStorage rewards = new RewardStorage();

	private final HashMap<UUID, NBTTagCompound> completeUsers = new HashMap<>();
    private Set<UUID> preRequisites = new HashSet<>();
    private HashMap<UUID, RequirementType> prereqTypes = new HashMap<>();

	private final PropertyContainer qInfo = new PropertyContainer();

	public QuestInstance()
	{
		this.setupProps();
	}

	private void setupProps()
	{
		setupValue(NativeProps.NAME, "New Quest");
		setupValue(NativeProps.DESC, "No Description");

		setupValue(NativeProps.ICON, new BigItemStack(Items.nether_star));

		setupValue(NativeProps.SOUND_COMPLETE);
		setupValue(NativeProps.SOUND_UPDATE);
		//setupValue(NativeProps.SOUND_UNLOCK);

		setupValue(NativeProps.LOGIC_QUEST, EnumLogic.AND);
		setupValue(NativeProps.LOGIC_TASK, EnumLogic.AND);

		setupValue(NativeProps.REPEAT_TIME, -1);
		setupValue(NativeProps.REPEAT_REL, true);
		setupValue(NativeProps.LOCKED_PROGRESS, false);
		setupValue(NativeProps.AUTO_CLAIM, false);
		setupValue(NativeProps.SILENT, false);
		setupValue(NativeProps.MAIN, false);
		setupValue(NativeProps.GLOBAL_SHARE, false);
		setupValue(NativeProps.SIMULTANEOUS, false);
		setupValue(NativeProps.VISIBILITY, EnumQuestVisibility.NORMAL);
	}

	private <T> void setupValue(IPropertyType<T> prop)
	{
		this.setupValue(prop, prop.getDefault());
	}

	private <T> void setupValue(IPropertyType<T> prop, T def)
	{
		qInfo.setProperty(prop, qInfo.getProperty(prop, def));
	}

	@Override
	public void update(EntityPlayer player)
	{
		UUID playerID = QuestingAPI.getQuestingUUID(player);

        int done = 0;

        for(DBEntry<ITask> entry : tasks.getEntries())
        {
            if(entry.getValue().isComplete(playerID) || entry.getValue().ignored(playerID))
            {
                done++;
            }
        }

        if(tasks.size() <= 0 || qInfo.getProperty(NativeProps.LOGIC_TASK).getResult(done, tasks.size()))
        {
            setComplete(playerID, System.currentTimeMillis());
        } else if(done > 0 && qInfo.getProperty(NativeProps.SIMULTANEOUS)) // TODO: There is actually an exploit here to do with locked progression bypassing simultaneous reset conditions. Fix?
        {
            resetUser(playerID, false);
        }
    }

	/**
	 * Fired when someone clicks the detect button for this quest
	 */
	@Override
	public void detect(EntityPlayer player)
	{
		UUID playerID = QuestingAPI.getQuestingUUID(player);
        QuestCache qc = (QuestCache)player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc == null) {
            return;
        }

        UUID questID = QuestDatabase.INSTANCE.lookupKey(this);

		if (isComplete(playerID) && (qInfo.getProperty(NativeProps.REPEAT_TIME) < 0 || rewards.size() <= 0))
		{
			return;
		}
        else if (!canSubmit(player))
		{
			return;
		}

		if (isUnlocked(playerID) || QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE))
		{
			int done = 0;
			boolean update = false;

            ParticipantInfo partInfo = new ParticipantInfo(player);
            Map.Entry<UUID, IQuest> mapEntry = Maps.immutableEntry(questID, this);

			for (DBEntry<ITask> entry : tasks.getEntries())
			{
				if (!entry.getValue().isComplete(playerID) || !entry.getValue().ignored(playerID))
				{
					entry.getValue().detect(partInfo, mapEntry);

					if (entry.getValue().isComplete(playerID) || entry.getValue().ignored(playerID))
					{
						done++;
						update = true;
                        if (hasConsumableTasks && QBConfig.partySyncConsumeQuests) {
                            synchronized (completeUsers) {
                                for (UUID p : partInfo.ALL_UUIDS) {
                                    if (p != playerID) {
                                        setClaimed(p, System.currentTimeMillis());
                                    }
                                }
                            }
                        }
					}
				}
                else
				{
					done++;
				}
			}
			// Note: Tasks can mark the quest dirty themselves if progress changed but hasn't fully completed.
			if (tasks.size() <= 0 || qInfo.getProperty(NativeProps.LOGIC_TASK).getResult(done, tasks.size()))
			{
			    // State won't be auto updated in edit mode so we force change it here and mark it for re-sync
				if (QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE))
                {
                    setComplete(playerID, System.currentTimeMillis());
                }
				qc.markQuestDirty(questID);
			}
            else if (update && qInfo.getProperty(NativeProps.SIMULTANEOUS))
			{
				resetUser(playerID, false);
				qc.markQuestDirty(questID);
			}
            else if (update)
			{
				qc.markQuestDirty(questID);
			}
		}
	}

	@Override
	public boolean hasClaimed(UUID uuid)
	{
		if(rewards.size() <= 0) return true;

		synchronized(completeUsers)
        {
            if(qInfo.getProperty(NativeProps.GLOBAL) && !qInfo.getProperty(NativeProps.GLOBAL_SHARE))
            {
                for(NBTTagCompound entry : completeUsers.values())
                {
                    if(entry.getBoolean("claimed"))
                    {
                        return true;
                    }
                }

                return false;
            }

            NBTTagCompound entry = getCompletionInfo(uuid);
            return entry != null && entry.getBoolean("claimed");
        }
	}

    @Override
    public boolean canClaimBasically(EntityPlayer player) {
        UUID pID = QuestingAPI.getQuestingUUID(player);
        NBTTagCompound entry = getCompletionInfo(pID);

        return entry != null && !hasClaimed(pID) && !canSubmit(player);
    }

    @Override
    public boolean canClaim(EntityPlayer player) {
        if (!canClaimBasically(player)) return false;
        Map.Entry<UUID, IQuest> mapEntry =
                Maps.immutableEntry(QuestDatabase.INSTANCE.lookupKey(this), this);
        for (DBEntry<IReward> rew : rewards.getEntries()) {
            if (!rew.getValue().canClaim(player, mapEntry)) {
                return false;
            }
        }

        return true;
    }

	@Override
	public void claimReward(EntityPlayer player)
	{
        UUID questID = QuestDatabase.INSTANCE.lookupKey(this);
        Map.Entry<UUID, IQuest> mapEntry = Maps.immutableEntry(questID, this);
		for (DBEntry<IReward> rew : rewards.getEntries())
		{
			rew.getValue().claimReward(player, mapEntry);
		}

		UUID pID = QuestingAPI.getQuestingUUID(player);
        QuestCache qc = (QuestCache)player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());

        synchronized (completeUsers)
        {
            NBTTagCompound entry = getCompletionInfo(pID);

            if (entry == null)
            {
                entry = new NBTTagCompound();
                this.completeUsers.put(pID, entry);
            }

            entry.setBoolean("claimed", true);
            entry.setLong("timestamp", System.currentTimeMillis());

            DirtyPlayerMarker.markDirty(pID);
        }
		if (qc != null) {
            qc.markQuestDirty(QuestDatabase.INSTANCE.lookupKey(this));
        }
	}

	@Override
	public boolean canSubmit(EntityPlayer player)
	{
		if(player == null) return false;

		UUID playerID = QuestingAPI.getQuestingUUID(player);

		synchronized(completeUsers)
        {
            NBTTagCompound entry = this.getCompletionInfo(playerID);
            if(entry == null) return true;

            if(!entry.getBoolean("claimed") && getProperty(NativeProps.REPEAT_TIME) >= 0) // Complete but repeatable
            {
                if(tasks.size() <= 0) return true;

                int done = 0;

                for(DBEntry<ITask> tsk : tasks.getEntries())
                {
                    if(tsk.getValue().isComplete(playerID) || tsk.getValue().ignored(playerID))
                    {
                        done += 1;
                    }
                }

                return !qInfo.getProperty(NativeProps.LOGIC_TASK).getResult(done, tasks.size());
            } else
            {
                return false;
            }
        }
	}

	@Override
	public boolean isUnlocked(UUID uuid)
	{
		if (preRequisites.isEmpty())
        {
            return true;
        }

        int complete =
                (int) QuestDatabase.INSTANCE.getAll(preRequisites)
                        .filter(quest -> quest.isComplete(uuid))
                        .count();
		return qInfo.getProperty(NativeProps.LOGIC_QUEST).getResult(complete, preRequisites.size());
	}

	@Override
	public void setComplete(UUID uuid, long timestamp)
    {
        if(uuid == null) return;
        synchronized(completeUsers)
        {
            NBTTagCompound entry = this.getCompletionInfo(uuid);

            if(entry == null)
            {
                entry = new NBTTagCompound();
                completeUsers.put(uuid, entry);
            }

            entry.setBoolean("claimed", false);
            entry.setLong("timestamp", timestamp);

            DirtyPlayerMarker.markDirty(uuid);
        }
    }

	@Override
	public boolean isUnlockable(UUID uuid)
	{
		// TODO: determine if we need to recursively lookup parents
		// glease: currently we do not have whole chains mutually exclusive with each other here in gtnh,
		// but this might be useful for more quest driven packs. the performance implication dissuade me from implementing
		// this. should look into this later.
        if (preRequisites.isEmpty())
        {
            return true;
        }

		EnumLogic questLogic = qInfo.getProperty(NativeProps.LOGIC_QUEST);
		if (questLogic.isTrivial()) return true;

		int complete =
                (int) QuestDatabase.INSTANCE.getAll(preRequisites)
                        .filter(quest -> quest.isComplete(uuid))
                        .count();
		return questLogic.isUnlockable(complete, preRequisites.size());
	}

	/**
	 * Returns true if the quest has been completed at least once
	 */
	@Override
	public boolean isComplete(UUID uuid)
	{
		if(qInfo.getProperty(NativeProps.GLOBAL))
		{
			return completeUsers.size() > 0;
		} else
		{
			return getCompletionInfo(uuid) != null;
		}
	}

	@Override
    public EnumQuestState getState(EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        if (this.isComplete(uuid)) {
            if (canClaimBasically(player)) {
                return EnumQuestState.UNCLAIMED;
            } else if (this.getProperty(NativeProps.REPEAT_TIME) > -1 && !this.hasClaimed(uuid)) {
                return EnumQuestState.REPEATABLE;
            }
            return EnumQuestState.COMPLETED;
        } else if (this.isUnlocked(uuid)) {
            return EnumQuestState.UNLOCKED;
        }

        return EnumQuestState.LOCKED;
	}

	@Override
	public NBTTagCompound getCompletionInfo(UUID uuid)
	{
	    synchronized(completeUsers)
        {
            return completeUsers.get(uuid);
        }
	}

	@Override
    public void setCompletionInfo(UUID uuid, NBTTagCompound nbt)
    {
        if(uuid == null) return;

        synchronized(completeUsers)
        {
            if(nbt == null)
            {
                completeUsers.remove(uuid);
            } else
            {
                completeUsers.put(uuid, nbt);
            }

            DirtyPlayerMarker.markDirty(uuid);
        }
    }

	/**
	 * Resets task progress and claim status. If performing a full reset, completion status will also be erased
	 */
	@Override
	public void resetUser(@Nullable UUID uuid, boolean fullReset)
	{
	    synchronized(completeUsers)
        {
            HashSet<UUID> dirtyPlayers = new HashSet<>();
            if (uuid == null) {
                dirtyPlayers.addAll(completeUsers.keySet());
            } else {
                dirtyPlayers.add(uuid);
            }
            if(fullReset)
            {
                if(uuid == null)
                {
                    completeUsers.clear();
                } else
                {
                    completeUsers.remove(uuid);
                }
            } else
            {
                if(uuid == null)
                {
                    completeUsers.forEach((key, value) -> {
                        value.setBoolean("claimed", false);
                        value.setLong("timestamp", 0);
                    });
                } else
                {
                    NBTTagCompound entry = getCompletionInfo(uuid);
                    if(entry != null)
                    {
                        entry.setBoolean("claimed", false);
                        entry.setLong("timestamp", 0);
                    }
                }
            }

            DirtyPlayerMarker.markDirty(dirtyPlayers);
            tasks.getEntries().forEach((value) -> value.getValue().resetUser(uuid));
        }
	}

	@Override
	public IDatabaseNBT<ITask, NBTTagList, NBTTagList> getTasks()
	{
		return tasks;
	}

	@Override
	public IDatabaseNBT<IReward, NBTTagList, NBTTagList> getRewards()
	{
		return rewards;
	}

	@Nonnull
	@Override
    public Set<UUID> getRequirements()
    {
        return preRequisites;
    }

    public void setRequirements(@Nonnull Iterable<UUID> req)
    {
        preRequisites.clear();
        req.forEach(preRequisites::add);
        prereqTypes.keySet().removeIf(key -> !preRequisites.contains(key));
    }

    @Nonnull
    @Override
    public RequirementType getRequirementType(UUID req) {
        RequirementType type = prereqTypes.get(req);
        return type == null ? RequirementType.NORMAL : type;
    }

    @Override
    public void setRequirementType(UUID req, @Nonnull RequirementType kind) {
        if (kind == RequirementType.NORMAL)
            prereqTypes.remove(req);
        else
            prereqTypes.put(req, kind);
    }

    @Override
	public NBTTagCompound writeToNBT(NBTTagCompound jObj)
	{
		jObj.setTag("properties", qInfo.writeToNBT(new NBTTagCompound()));
		jObj.setTag("tasks", tasks.writeToNBT(new NBTTagList(), null));
		jObj.setTag("rewards", rewards.writeToNBT(new NBTTagList(), null));

        NBTTagList tagList = new NBTTagList();
        for (UUID questID : preRequisites)
        {
            NBTTagCompound tag = NBTConverter.UuidValueType.QUEST.writeId(questID);

            if (prereqTypes.containsKey(questID))
            {
                tag.setByte("type", prereqTypes.get(questID).id());
            }

            tagList.appendTag(tag);
        }
        jObj.setTag("preRequisites", tagList);

		return jObj;
	}

	@Override
	public void readFromNBT(NBTTagCompound jObj)
	{
		this.qInfo.readFromNBT(jObj.getCompoundTag("properties"));
		this.tasks.readFromNBT(jObj.getTagList("tasks", 10), false);
		this.rewards.readFromNBT(jObj.getTagList("rewards", 10), false);

        // Remember if there are any consumable tasks in this quest to prevent reward duplication
        if (QBConfig.partySyncConsumeQuests) {
            for (DBEntry<ITask> entry : tasks.getEntries()) {
                if (entry.getValue().isConsume()) {
                    hasConsumableTasks = true;
                    break;
                }
            }
        }

        // The legacy storage format used array indices to link together two separate list tags,
        // one for prerequisites, and one for prerequisite tags.
        // We need this map to recreate that link.
        Map<Integer, UUID> legacyPrerequisiteIndex = new HashMap<>();
        if (jObj.func_150299_b("preRequisites") == Constants.NBT.TAG_LIST)
        {
            preRequisites = new HashSet<>();

            List<NBTBase> tagList =
                    NBTConverter.getTagList(jObj.getTagList("preRequisites", Constants.NBT.TAG_COMPOUND));
            for (NBTBase tag : tagList)
            {
                if (!(tag instanceof NBTTagCompound))
                {
                    continue;
                }

                NBTTagCompound tagCompound = (NBTTagCompound) tag;
                Optional<UUID> questIDOptional = NBTConverter.UuidValueType.QUEST.tryReadId(tagCompound);
                if (!questIDOptional.isPresent())
                {
                    continue;
                }

                UUID questID = questIDOptional.get();
                preRequisites.add(questID);

                if (tagCompound.hasKey("type", 99))
                {
                    setRequirementType(questID, RequirementType.from(tagCompound.getByte("type")));
                }
            }
        }
        else if (jObj.func_150299_b("preRequisites") == Constants.NBT.TAG_INT_ARRAY) // Legacy format
		{
            // This block is needed for old questbook data.
            preRequisites = new HashSet<>();
            int[] intArray = jObj.getIntArray("preRequisites");
            for (int i = 0; i < intArray.length; i++) {
                UUID questID = UuidConverter.convertLegacyId(intArray[i]);
                preRequisites.add(questID);
                legacyPrerequisiteIndex.put(i, questID);
            }
		}

        // This block is needed for old questbook data.
        if (jObj.func_150299_b("preRequisiteTypes") == Constants.NBT.TAG_BYTE_ARRAY) {
            byte[] byteArray = jObj.getByteArray("preRequisiteTypes");
            for (int i = 0, byteArrayLength = byteArray.length; i < byteArrayLength; i++)
            {
                UUID questID = legacyPrerequisiteIndex.get(i);
                if (questID == null)
                {
                    continue;
                }

                setRequirementType(questID, RequirementType.from(byteArray[i]));
            }
        }

		this.setupProps();
	}

	@Override
	public NBTTagCompound writeProgressToNBT(NBTTagCompound json, @Nullable List<UUID> users)
	{
	    synchronized(completeUsers)
        {
            NBTTagList comJson = new NBTTagList();
            for(Entry<UUID, NBTTagCompound> entry : completeUsers.entrySet())
            {
                if(entry.getValue() == null || entry.getKey() == null) continue;
                if(users != null && !users.contains(entry.getKey())) continue;
                NBTTagCompound tags = (NBTTagCompound)entry.getValue().copy();
                tags.setString("uuid", entry.getKey().toString());
                comJson.appendTag(tags);
            }
            json.setTag("completed", comJson);

            NBTTagList tskJson = tasks.writeProgressToNBT(new NBTTagList(), users);
            json.setTag("tasks", tskJson);

            return json;
        }
	}

	@Override
	public void readProgressFromNBT(NBTTagCompound json, boolean merge)
	{
	    synchronized(completeUsers)
        {
            if(!merge) completeUsers.clear();
            NBTTagList comList = json.getTagList("completed", 10);
            for(int i = 0; i < comList.tagCount(); i++)
            {
                NBTTagCompound entry = (NBTTagCompound)comList.getCompoundTagAt(i).copy();

                try
                {
                    UUID uuid = UUID.fromString(entry.getString("uuid"));
                    completeUsers.put(uuid, entry);
                } catch(Exception e)
                {
                    BetterQuesting.logger.log(Level.ERROR, "Unable to load UUID for quest", e);
                }
            }

            tasks.readProgressFromNBT(json.getTagList("tasks", 10), merge);
        }
	}

    @Override
	public void setClaimed(UUID uuid, long timestamp)
	{
		synchronized(completeUsers)
        {
            NBTTagCompound entry = this.getCompletionInfo(uuid);

            if(entry != null)
            {
                entry.setBoolean("claimed", true);
                entry.setLong("timestamp", timestamp);
            } else
            {
                entry = new NBTTagCompound();
                entry.setBoolean("claimed", true);
                entry.setLong("timestamp", timestamp);
                completeUsers.put(uuid, entry);
            }

            DirtyPlayerMarker.markDirty(uuid);
        }
	}

    public void getUsersWithCompletionData(Set<UUID> targetSet) {
        synchronized (completeUsers) {
            // Take a copy to prevent concurrent modifications to the returned Set
            targetSet.addAll(completeUsers.keySet());
        }
    }

    @Override
    public <T> T getProperty(IPropertyType<T> prop)
    {
        return qInfo.getProperty(prop);
    }

    @Override
    public <T> T getProperty(IPropertyType<T> prop, T def)
    {
        return qInfo.getProperty(prop, def);
    }

    @Override
    public boolean hasProperty(IPropertyType<?> prop)
    {
        return qInfo.hasProperty(prop);
    }

    @Override
    public <T> void setProperty(IPropertyType<T> prop, T value)
    {
        qInfo.setProperty(prop, value);
    }

    @Override
    public void removeProperty(IPropertyType<?> prop)
    {
        qInfo.removeProperty(prop);
    }

    @Override
    public void removeAllProps()
    {
        qInfo.removeAllProps();
    }
}
