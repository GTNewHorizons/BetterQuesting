package bq_standard.tasks.factory;

import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskInteractEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryTaskInteractEntity implements IFactoryData<ITask, NBTTagCompound> {
    public static final FactoryTaskInteractEntity INSTANCE = new FactoryTaskInteractEntity();

    private final ResourceLocation REG_ID = new ResourceLocation(BQ_Standard.MODID, "interact_entity");

    @Override
    public ResourceLocation getRegistryName() {
        return REG_ID;
    }

    @Override
    public TaskInteractEntity createNew() {
        return new TaskInteractEntity();
    }

    @Override
    public TaskInteractEntity loadFromData(NBTTagCompound nbt) {
        TaskInteractEntity task = new TaskInteractEntity();
        task.readFromNBT(nbt);
        return task;
    }
}
