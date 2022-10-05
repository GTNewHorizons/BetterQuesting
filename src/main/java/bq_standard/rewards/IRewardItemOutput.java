package bq_standard.rewards;

import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.utils.BigItemStack;
import java.util.List;

public interface IRewardItemOutput extends IReward {
    List<BigItemStack> getItemOutputs();
}
