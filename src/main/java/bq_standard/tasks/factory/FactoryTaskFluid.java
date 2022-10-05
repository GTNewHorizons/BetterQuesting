package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskFluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskFluid implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskFluid INSTANCE = new FactoryTaskFluid();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID + ":fluid");
    }

    @Override
    public TaskFluid createNew() {
        return new TaskFluid();
    }

    @Override
    public TaskFluid loadFromData(NBTTagCompound json) {
        TaskFluid task = new TaskFluid();
        task.readFromNBT(json);
        return task;
    }
}
