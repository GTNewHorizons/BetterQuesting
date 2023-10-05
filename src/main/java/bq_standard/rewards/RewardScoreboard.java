package bq_standard.rewards;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import bq_standard.client.gui.rewards.PanelRewardScoreboard;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.factory.FactoryRewardScoreboard;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreDummyCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Level;

import java.util.Map;
import java.util.UUID;

public class RewardScoreboard implements IReward {
    public String score = "Reputation";
    public String type = "dummy";
    public boolean relative = true;
    public int value = 1;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardScoreboard.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.scoreboard";
    }

    @Override
    public boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        return true;
    }

    @Override
    public void claimReward(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        Scoreboard board = player.getWorldScoreboard();
        if (board == null) return;

        ScoreObjective scoreObj = board.getObjective(score);

        if (scoreObj == null) {
            try {
                IScoreObjectiveCriteria criteria =
                        (IScoreObjectiveCriteria) IScoreObjectiveCriteria.field_96643_a.get(type);
                criteria = criteria != null ? criteria : new ScoreDummyCriteria(score);
                scoreObj = board.addScoreObjective(score, criteria);
                scoreObj.setDisplayName(score);
            } catch (Exception e) {
                BQ_Standard.logger.log(Level.ERROR, "Unable to create score '" + score + "' for reward!", e);
            }
        }

        if (scoreObj == null || scoreObj.getCriteria().isReadOnly()) {
            return;
        }

        Score s = board.func_96529_a(player.getCommandSenderName(), scoreObj);

        if (relative) {
            s.increseScore(value);
        } else {
            s.setScorePoints(value);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound json) {
        score = json.getString("score");
        type = json.getString("type");
        value = json.getInteger("value");
        relative = json.getBoolean("relative");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound json) {
        json.setString("score", score);
        json.setString("type", "dummy");
        json.setInteger("value", value);
        json.setBoolean("relative", relative);
        return json;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelRewardScoreboard(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getRewardEditor(GuiScreen screen, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
}
