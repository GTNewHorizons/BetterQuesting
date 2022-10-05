package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskOptionalRetrieval;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskOptionalRetrieval implements IFactoryData<ITask, NBTTagCompound> {

    public static final FactoryTaskOptionalRetrieval INSTANCE = new FactoryTaskOptionalRetrieval();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":optional_retrieval");
    }

    @Override
    public TaskOptionalRetrieval createNew() {
        return new TaskOptionalRetrieval();
    }

    @Override
    public TaskOptionalRetrieval loadFromData(NBTTagCompound json) {
        TaskOptionalRetrieval task = new TaskOptionalRetrieval();
        task.readFromNBT(json);
        return task;
    }
}
