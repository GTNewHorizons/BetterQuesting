package bq_standard.client.gui.tasks;

import net.minecraft.util.EnumChatFormatting;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.tasks.TaskRetrieval;

public class PanelTaskRetrieval extends PanelTaskItemBase<TaskRetrieval> {

    public PanelTaskRetrieval(IGuiRect rect, TaskRetrieval task) {
        super(rect, task);
    }

    @Override
    protected int getItemCount() {
        return task.requiredItems.size();
    }

    @Override
    protected BigItemStack getItemStack(int index) {
        return task.requiredItems.get(index);
    }

    @Override
    protected GuiRectangle createItemSlotRect(int i) {
        return new GuiRectangle(0, i * 32 + 16, 28, 28, 0);
    }

    @Override
    protected GuiRectangle createTextBoxRect(int i, int width) {
        return new GuiRectangle(32, i * 32 + 16, width - 28, 28, 0);
    }

    @Override
    public void initPanelExtras(int listW) {
        String sCon = (task.consume ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
            + QuestTranslation.translate(task.consume ? "gui.yes" : "gui.no");
        this.addPanel(
            new PanelTextBox(
                new GuiTransform(GuiAlign.TOP_EDGE, 0, 0, listW, 16, 0),
                QuestTranslation.translate("bq_standard.btn.consume", sCon))
                    .setColor(PresetColor.TEXT_MAIN.getColor()));
    }
}
