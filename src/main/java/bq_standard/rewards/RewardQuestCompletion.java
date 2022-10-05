package bq_standard.rewards;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import bq_standard.client.gui.rewards.PanelRewardQuestCompletion;
import bq_standard.rewards.factory.FactoryRewardQuestCompletion;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class RewardQuestCompletion implements IReward {
    public int questNum = -1;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardQuestCompletion.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.questcompletion";
    }

    @Override
    public boolean canClaim(EntityPlayer player, DBEntry<IQuest> quest) {
        return true;
    }

    @Override
    public void claimReward(EntityPlayer player, DBEntry<IQuest> quest) {
        if (questNum == -1) return;
        IQuest targetQuest = QuestingAPI.getAPI(ApiReference.QUEST_DB).getValue(questNum);
        if (targetQuest == null) return;
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        if (!targetQuest.isComplete(uuid)) targetQuest.setComplete(uuid, System.currentTimeMillis());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        questNum = nbt.getInteger("quest");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("quest", questNum);
        return nbt;
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelRewardQuestCompletion(rect, this);
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }
}
