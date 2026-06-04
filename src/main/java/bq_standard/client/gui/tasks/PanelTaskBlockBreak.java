package bq_standard.client.gui.tasks;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import bq_standard.tasks.TaskBlockBreak;

public class PanelTaskBlockBreak extends PanelTaskItemBase<TaskBlockBreak> {

    public PanelTaskBlockBreak(IGuiRect rect, TaskBlockBreak task) {
        super(rect, task);
    }

    @Override
    protected int getItemCount() {
        return task.blockTypes.size();
    }

    @Override
    protected BigItemStack getItemStack(int index) {
        return task.blockTypes.get(index)
            .getItemStack();
    }

    @Override
    protected GuiRectangle createItemSlotRect(int i) {
        return new GuiRectangle(0, i * 36, 36, 36, 0);
    }

    @Override
    protected GuiRectangle createTextBoxRect(int i, int width) {
        return new GuiRectangle(40, i * 36, width - 40, 36, 0);
    }

    @Override
    protected void initPanelExtras(int listW) {}
}
