package bq_standard.tasks.base;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.utils.DirtyPlayerMarker;
import bq_standard.core.BQ_Standard;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public abstract class TaskBase implements ITask {
    protected final Set<UUID> completeUsers = new TreeSet<>();

    @Override
    public boolean isComplete(UUID uuid) {
        return completeUsers.contains(uuid);
    }

    @Override
    public void setComplete(UUID uuid) {
        if (!completeUsers.contains(uuid)) {
            completeUsers.add(uuid);
            DirtyPlayerMarker.markDirty(uuid);
        }
    }

    @Override
    public void resetUser(@Nullable UUID uuid) {
        HashSet<UUID> dirtyPlayers = new HashSet<>();
        if (uuid == null) {
            dirtyPlayers.addAll(completeUsers);
            completeUsers.clear();
        } else {
            if (completeUsers.remove(uuid)) {
                dirtyPlayers.add(uuid);
            }
        }
        DirtyPlayerMarker.markDirty(dirtyPlayers);
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound json, boolean merge) {
        if (!merge) completeUsers.clear();

        NBTTagList completeUsersNBTList = json.getTagList("completeUsers", Constants.NBT.TAG_STRING);
        for (int i = 0; i < completeUsersNBTList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(completeUsersNBTList.getStringTagAt(i)));
            } catch (Exception e) {
                BQ_Standard.logger.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        // if users is null, then save all users, otherwise only save the ones in the list
        NBTTagList completeUsersNBTList = new NBTTagList();

        completeUsers.forEach((uuid) -> {
            if (users == null || users.contains(uuid))
                completeUsersNBTList.appendTag(new NBTTagString(uuid.toString()));
        });

        nbt.setTag("completeUsers", completeUsersNBTList);

        return nbt;
    }
}
