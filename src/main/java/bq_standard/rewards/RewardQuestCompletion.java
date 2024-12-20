package bq_standard.rewards;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.AbstractReward;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.questing.QuestDatabase;
import bq_standard.client.gui.rewards.PanelRewardQuestCompletion;
import bq_standard.rewards.factory.FactoryRewardQuestCompletion;

public class RewardQuestCompletion extends AbstractReward implements IReward {

    public UUID questNum = null;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardQuestCompletion.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.questcompletion";
    }

    @Override
    public boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        return true;
    }

    @Override
    protected boolean getDefaultIgnoreDisabled() {
        return true;
    }

    @Override
    protected void claimReward0(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        if (questNum == null) {
            return;
        }

        IQuest targetQuest = QuestingAPI.getAPI(ApiReference.QUEST_DB)
            .get(questNum);
        if (targetQuest == null) {
            return;
        }

        QuestCache qc = (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc == null) {
            return;
        }

        UUID questId = QuestDatabase.INSTANCE.lookupKey(targetQuest);
        if (questId == null) {
            return;
        }

        UUID uuid = QuestingAPI.getQuestingUUID(player);
        if (!targetQuest.isComplete(uuid)) {
            targetQuest.setComplete(uuid, System.currentTimeMillis());
            qc.markQuestDirty(questId);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        Optional<UUID> uuid = NBTConverter.UuidValueType.QUEST.tryReadIdString(nbt);
        if (uuid.isPresent()) {
            questNum = uuid.get();
        } else if (nbt.hasKey("quest", 99)) {
            // This block is needed for old questbook data.
            questNum = UuidConverter.convertLegacyId(nbt.getInteger("quest"));
        } else {
            questNum = null;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTConverter.UuidValueType.QUEST.writeIdString(questNum, nbt);
        return nbt;
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelRewardQuestCompletion(rect, this);
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen screen, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
}
