package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.ScoreboardBQ;
import bq_standard.client.gui.editors.tasks.GuiEditTaskScoreboard;
import bq_standard.client.gui.tasks.PanelTaskScoreboard;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskBase;
import bq_standard.tasks.factory.FactoryTaskScoreboard;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreDummyCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.Level;

public class TaskScoreboard extends TaskBase implements ITaskTickable {
    // region Properties
    public String scoreName = "Score";
    public String scoreDisp = "Score";
    public String type = "dummy";
    public int target = 1;
    public float conversion = 1F;
    public String suffix = "";
    public ScoreOperation operation = ScoreOperation.MORE_OR_EQUAL;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        scoreName = nbt.getString("scoreName");
        scoreName = scoreName.replaceAll(" ", "_");
        scoreDisp = nbt.getString("scoreDisp");
        type = nbt.hasKey("type", Constants.NBT.TAG_STRING) ? nbt.getString("type") : "dummy";
        target = nbt.getInteger("target");
        conversion = nbt.getFloat("unitConversion");
        suffix = nbt.getString("unitSuffix");
        try {
            operation = ScoreOperation.valueOf(
                    nbt.hasKey("operation", Constants.NBT.TAG_STRING) ? nbt.getString("operation") : "MORE_OR_EQUAL");
        } catch (Exception e) {
            operation = ScoreOperation.MORE_OR_EQUAL;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setString("scoreName", scoreName);
        nbt.setString("scoreDisp", scoreDisp);
        nbt.setString("type", type);
        nbt.setInteger("target", target);
        nbt.setFloat("unitConversion", conversion);
        nbt.setString("unitSuffix", suffix);
        nbt.setString("operation", operation.name());

        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.scoreboard";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskScoreboard.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskScoreboard(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return new GuiEditTaskScoreboard(parent, quest, this);
    }
    // endregion Basic

    @Override
    public void detect(@Nonnull ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        Scoreboard board = pInfo.PLAYER.getWorldScoreboard();
        ScoreObjective scoreObj = board.getObjective(scoreName);

        if (scoreObj == null) {
            try {
                IScoreObjectiveCriteria criteria =
                        (IScoreObjectiveCriteria) IScoreObjectiveCriteria.field_96643_a.get(type);
                criteria = criteria != null ? criteria : new ScoreDummyCriteria(scoreName);
                scoreObj = board.addScoreObjective(scoreName, criteria);
                scoreObj.setDisplayName(scoreDisp);
            } catch (Exception e) {
                BQ_Standard.logger.log(Level.ERROR, "Unable to create score '" + scoreName + "' for task!", e);
                return;
            }
        }

        int points = board.func_96529_a(pInfo.PLAYER.getCommandSenderName(), scoreObj)
                .getScorePoints();
        ScoreboardBQ.INSTANCE.setScore(pInfo.UUID, scoreName, points);

        if (operation.checkValues(points, target)) {
            setComplete(pInfo.UUID);
            pInfo.markDirty(Collections.singletonList(quest.getID()));
        }
    }

    public enum ScoreOperation {
        EQUAL("="),
        LESS_THAN("<"),
        MORE_THAN(">"),
        LESS_OR_EQUAL("<="),
        MORE_OR_EQUAL(">="),
        NOT("=/=");

        private final String text;

        ScoreOperation(String text) {
            this.text = text;
        }

        public String GetText() {
            return text;
        }

        public boolean checkValues(int n1, int n2) {
            switch (this) {
                case EQUAL:
                    return n1 == n2;
                case LESS_THAN:
                    return n1 < n2;
                case MORE_THAN:
                    return n1 > n2;
                case LESS_OR_EQUAL:
                    return n1 <= n2;
                case MORE_OR_EQUAL:
                    return n1 >= n2;
                case NOT:
                    return n1 != n2;
            }

            return false;
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, @Nonnull DBEntry<IQuest> quest) {
        if (pInfo.PLAYER.ticksExisted % 20 == 0) detect(pInfo, quest); // Auto-detect once per second
    }

    @Override
    public List<String> getTextsForSearch() {
        List<String> texts = new ArrayList<>();
        texts.add(scoreName);
        texts.add(scoreDisp);
        return texts;
    }
}
