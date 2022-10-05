package bq_standard.rewards.factory;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.RewardQuestCompletion;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryRewardQuestCompletion implements IFactoryData<IReward, NBTTagCompound> {
    public static final FactoryRewardQuestCompletion INSTANCE = new FactoryRewardQuestCompletion();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID, "questcompletion");
    }

    @Override
    public RewardQuestCompletion createNew() {
        return new RewardQuestCompletion();
    }

    @Override
    public RewardQuestCompletion loadFromData(NBTTagCompound json) {
        RewardQuestCompletion reward = new RewardQuestCompletion();
        reward.readFromNBT(json);
        return reward;
    }
}
