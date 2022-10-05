package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskBlockBreak;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskBlockBreak implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskBlockBreak INSTANCE = new FactoryTaskBlockBreak();

    private final ResourceLocation REG_ID = new ResourceLocation(BQ_Standard.MODID, "block_break");

    @Override
    public ResourceLocation getRegistryName() {
        return REG_ID;
    }

    @Override
    public TaskBlockBreak createNew() {
        return new TaskBlockBreak();
    }

    @Override
    public TaskBlockBreak loadFromData(NBTTagCompound json) {
        TaskBlockBreak task = new TaskBlockBreak();
        task.readFromNBT(json);
        return task;
    }
}
