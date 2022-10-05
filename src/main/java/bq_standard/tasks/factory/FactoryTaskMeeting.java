package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskMeeting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskMeeting implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskMeeting INSTANCE = new FactoryTaskMeeting();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":meeting");
    }

    @Override
    public TaskMeeting createNew() {
        return new TaskMeeting();
    }

    @Override
    public TaskMeeting loadFromData(NBTTagCompound json) {
        TaskMeeting task = new TaskMeeting();
        task.readFromNBT(json);
        return task;
    }
}
