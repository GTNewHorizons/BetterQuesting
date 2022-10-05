package bq_standard.rewards.factory;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.registry.IFactoryData;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.RewardScoreboard;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class FactoryRewardScoreboard implements IFactoryData<IReward, NBTTagCompound> {
    public static final FactoryRewardScoreboard INSTANCE = new FactoryRewardScoreboard();

    @Override
    public ResourceLocation getRegistryName() {
        return new ResourceLocation(BQ_Standard.MODID, "scoreboard");
    }

    @Override
    public RewardScoreboard createNew() {
        return new RewardScoreboard();
    }

    @Override
    public RewardScoreboard loadFromData(NBTTagCompound json) {
        RewardScoreboard reward = new RewardScoreboard();
        reward.readFromNBT(json);
        return reward;
    }
}
