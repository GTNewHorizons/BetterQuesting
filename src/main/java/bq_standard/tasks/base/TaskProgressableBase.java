package bq_standard.tasks.base;

import betterquesting.api2.utils.DirtyPlayerMarker;
import betterquesting.api2.utils.Tuple2;
import bq_standard.core.BQ_Standard;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class TaskProgressableBase<T> extends TaskBase {
    protected final TreeMap<UUID, T> userProgress = new TreeMap<>();

    protected void setUserProgress(UUID uuid, T progress) {
        userProgress.put(uuid, progress);
        DirtyPlayerMarker.markDirty(uuid);
    }

    public abstract T getUsersProgress(UUID uuid);

    @Override
    public void resetUser(@Nullable UUID uuid) {
        HashSet<UUID> dirtyPlayers = new HashSet<>();
        if (uuid == null) {
            dirtyPlayers.addAll(completeUsers);
            dirtyPlayers.addAll(userProgress.keySet());
            completeUsers.clear();
            userProgress.clear();
        } else {
            if (completeUsers.remove(uuid)) {
                dirtyPlayers.add(uuid);
            }
            if (userProgress.containsKey(uuid)) {
                userProgress.remove(uuid);
                dirtyPlayers.add(uuid);
            }
        }
        DirtyPlayerMarker.markDirty(dirtyPlayers);
    }

    protected List<Tuple2<UUID, T>> getBulkProgress(@Nonnull List<UUID> uuids) {
        return uuids.stream()
                .map((uuid) -> new Tuple2<>(uuid, getUsersProgress(uuid)))
                .collect(Collectors.toList());
    }

    protected void setBulkProgress(@Nonnull List<Tuple2<UUID, T>> ProgressList) {
        ProgressList.forEach((entry) -> setUserProgress(entry.getFirst(), entry.getSecond()));
    }

    public abstract T readUserProgressFromNBT(NBTTagCompound nbt);

    public abstract void writeUserProgressToNBT(NBTTagCompound nbt, T progress);

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        super.readProgressFromNBT(nbt, merge);
        if (!merge) userProgress.clear();

        NBTTagList ProgressNBTList = nbt.getTagList("userProgress", Constants.NBT.TAG_COMPOUND);
        for (int n = 0; n < ProgressNBTList.tagCount(); n++) {
            try {
                NBTTagCompound progressNBT = ProgressNBTList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(progressNBT.getString("uuid"));
                T progress = readUserProgressFromNBT(progressNBT);
                userProgress.put(uuid, progress);
            } catch (Exception e) {
                BQ_Standard.logger.log(Level.ERROR, "Unable to load user progress for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        super.writeProgressToNBT(nbt, users);
        NBTTagList ProgressNBTList = new NBTTagList();

        userProgress.forEach((uuid, progress) -> {
            if (users == null || users.contains(uuid)) {
                NBTTagCompound progressNBT = new NBTTagCompound();
                progressNBT.setString("uuid", uuid.toString());
                writeUserProgressToNBT(progressNBT, progress);
                ProgressNBTList.appendTag(progressNBT);
            }
        });

        nbt.setTag("userProgress", ProgressNBTList);
        return nbt;
    }
}
