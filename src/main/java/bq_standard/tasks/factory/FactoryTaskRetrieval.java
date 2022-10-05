package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskRetrieval;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskRetrieval implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskRetrieval INSTANCE = new FactoryTaskRetrieval();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":retrieval");
    }

    @Override
    public TaskRetrieval createNew() {
        return new TaskRetrieval();
    }

    @Override
    public TaskRetrieval loadFromData(NBTTagCompound json) {
        TaskRetrieval task = new TaskRetrieval();
        task.readFromNBT(json);
        return task;
    }
}
