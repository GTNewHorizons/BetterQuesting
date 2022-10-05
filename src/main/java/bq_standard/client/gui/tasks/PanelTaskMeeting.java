package bq_standard.client.gui.tasks;

import betterquesting.api2.client.gui.controls.io.ValueFuncIO;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelEntityPreview;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.tasks.TaskMeeting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;

public class PanelTaskMeeting extends CanvasMinimum {
    private final TaskMeeting task;
    private final IGuiRect initialRect;

    public PanelTaskMeeting(IGuiRect rect, TaskMeeting task) {
        super(rect);
        this.task = task;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();

        Entity target;

        if (EntityList.stringToClassMapping.containsKey(task.idName)) {
            target = EntityList.createEntityByName(task.idName, Minecraft.getMinecraft().theWorld);
            if (target != null) target.readFromNBT(task.targetTags);
        } else {
            target = null;
        }

        String tnm = target != null ? target.getCommandSenderName() : task.idName;

        this.addPanel(new PanelTextBox(
                        new GuiTransform(GuiAlign.TOP_EDGE, 0, 0, width, 16, 0),
                        QuestTranslation.translate("bq_standard.gui.meet", tnm) + " x" + task.amount)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        if (target != null)
            this.addPanel(new PanelEntityPreview(new GuiTransform(GuiAlign.TOP_LEFT, 0, 16, width, 64, 0), target)
                    .setRotationDriven(new ValueFuncIO<>(() -> 15F), new ValueFuncIO<>(() ->
                            (float) (Minecraft.getSystemTime() % 30000L / 30000D * 360D))));
        recalcSizes();
    }
}
