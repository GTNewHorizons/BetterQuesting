package bq_standard.rewards.factory;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.RewardCommand;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryRewardCommand implements IFactoryData<IReward, NBTTagCompound> {
    public static final FactoryRewardCommand INSTANCE = new FactoryRewardCommand();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID, "command");
    }

    @Override
    public RewardCommand createNew() {
        return new RewardCommand();
    }

    @Override
    public RewardCommand loadFromData(NBTTagCompound json) {
        RewardCommand reward = new RewardCommand();
        reward.readFromNBT(json);
        return reward;
    }
}
