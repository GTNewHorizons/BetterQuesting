package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskXP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskXP implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskXP INSTANCE = new FactoryTaskXP();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":xp");
    }

    @Override
    public TaskXP createNew() {
        return new TaskXP();
    }

    @Override
    public TaskXP loadFromData(NBTTagCompound json) {
        TaskXP task = new TaskXP();
        task.readFromNBT(json);
        return task;
    }
}
