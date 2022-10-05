package bq_standard.rewards.factory;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.RewardChoice;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryRewardChoice implements IFactoryData<IReward, NBTTagCompound> {
    public static final FactoryRewardChoice INSTANCE = new FactoryRewardChoice();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID, "choice");
    }

    @Override
    public RewardChoice createNew() {
        return new RewardChoice();
    }

    @Override
    public RewardChoice loadFromData(NBTTagCompound json) {
        RewardChoice reward = new RewardChoice();
        reward.readFromNBT(json);
        return reward;
    }
}
