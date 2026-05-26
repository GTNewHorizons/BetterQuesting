package bq_standard.tasks.factory;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskPartialRetrieval;

public class FactoryTaskPartialRetrieval implements IFactoryData<ITask, NBTTagCompound> {

    public static final FactoryTaskPartialRetrieval INSTANCE = new FactoryTaskPartialRetrieval();

    @Override
    public TaskPartialRetrieval loadFromData(NBTTagCompound json) {
        TaskPartialRetrieval task = new TaskPartialRetrieval();
        task.readFromNBT(json);
        return task;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":partial_retrieval");
    }

    @Override
    public TaskPartialRetrieval createNew() {
        return new TaskPartialRetrieval();
    }
}
