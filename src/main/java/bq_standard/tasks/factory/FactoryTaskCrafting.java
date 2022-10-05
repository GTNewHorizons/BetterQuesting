package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskCrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskCrafting implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskCrafting INSTANCE = new FactoryTaskCrafting();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":crafting");
    }

    @Override
    public TaskCrafting createNew() {
        return new TaskCrafting();
    }

    @Override
    public TaskCrafting loadFromData(NBTTagCompound json) {
        TaskCrafting task = new TaskCrafting();
        task.readFromNBT(json);
        return task;
    }
}
