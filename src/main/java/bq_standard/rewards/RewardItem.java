package bq_standard.rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Level;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.AbstractReward;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import bq_standard.NBTReplaceUtil;
import bq_standard.client.gui.rewards.PanelRewardItem;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.factory.FactoryRewardItem;

public class RewardItem extends AbstractReward implements IReward, IRewardItemOutput {

    public final List<BigItemStack> items = new ArrayList<>();

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardItem.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.item";
    }

    @Override
    public boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        return true;
    }

    @Override
    protected void claimReward0(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        for (BigItemStack r : items) {
            BigItemStack stack = r.copy();

            for (ItemStack s : stack.getCombinedStacks()) {
                if (s.getTagCompound() != null) {
                    s.setTagCompound(
                        NBTReplaceUtil.replaceStrings(s.getTagCompound(), "VAR_NAME", player.getCommandSenderName()));
                    s.setTagCompound(
                        NBTReplaceUtil.replaceStrings(
                            s.getTagCompound(),
                            "VAR_UUID",
                            QuestingAPI.getQuestingUUID(player)
                                .toString()));
                }

                if (!player.inventory.addItemStackToInventory(s)) {
                    player.dropPlayerItemWithRandomChoice(s, false);
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        items.clear();
        NBTTagList rList = nbt.getTagList("rewards", 10);
        for (int i = 0; i < rList.tagCount(); i++) {
            try {
                BigItemStack item = JsonHelper.JsonToItemStack(rList.getCompoundTagAt(i));
                if (item != null) items.add(item);
            } catch (Exception e) {
                BQ_Standard.logger.log(Level.ERROR, "Unable to load reward item data", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList rJson = new NBTTagList();
        for (BigItemStack stack : items) {
            rJson.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound()));
        }
        nbt.setTag("rewards", rJson);
        return nbt;
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelRewardItem(rect, this);
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen screen, Map.Entry<UUID, IQuest> quest) {
        return null;
    }

    @Override
    public List<BigItemStack> getItemOutputs() {
        return items;
    }
}
