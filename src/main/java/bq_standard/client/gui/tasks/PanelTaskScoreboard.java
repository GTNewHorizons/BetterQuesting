package bq_standard.client.gui.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import bq_standard.ScoreboardBQ;
import bq_standard.tasks.TaskScoreboard;
import java.text.DecimalFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

public class PanelTaskScoreboard extends CanvasMinimum {
    private final TaskScoreboard task;
    private final IGuiRect initialRect;

    public PanelTaskScoreboard(IGuiRect rect, TaskScoreboard task) {
        super(rect);
        this.task = task;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();

        int score = ScoreboardBQ.INSTANCE.getScore(
                QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer), task.scoreName);
        DecimalFormat df = new DecimalFormat("0.##");
        String value = df.format(score / task.conversion) + task.suffix;

        if (task.operation.checkValues(score, task.target)) {
            value = EnumChatFormatting.GREEN + value;
        } else {
            value = EnumChatFormatting.RED + value;
        }

        String txt2 = EnumChatFormatting.BOLD + value + " " + EnumChatFormatting.RESET + task.operation.GetText() + " "
                + df.format(task.target / task.conversion) + task.suffix;

        // TODO: Add x2 scale when supported
        this.addPanel(new PanelTextBox(new GuiTransform(GuiAlign.TOP_LEFT, 0, 0, width, 16, 0), task.scoreDisp)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor()));
        this.addPanel(new PanelTextBox(new GuiTransform(GuiAlign.TOP_LEFT, 0, 16, width, 16, 0), txt2)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor()));
        recalcSizes();
    }
}
