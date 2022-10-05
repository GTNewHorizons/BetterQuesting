package bq_standard.client.gui.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.*;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.client.gui.panels.content.PanelItemSlotBuilder;
import bq_standard.tasks.TaskRetrieval;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

public class PanelTaskRetrieval extends CanvasMinimum {
    private final TaskRetrieval task;
    private final IGuiRect initialRect;

    public PanelTaskRetrieval(IGuiRect rect, TaskRetrieval task) {
        super(rect);
        this.task = task;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int listW = initialRect.getWidth();

        UUID uuid = QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer);
        int[] progress = task.getUsersProgress(uuid);
        boolean isComplete = task.isComplete(uuid);

        String sCon = (task.consume ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
                + QuestTranslation.translate(task.consume ? "gui.yes" : "gui.no");
        this.addPanel(new PanelTextBox(
                        new GuiTransform(GuiAlign.TOP_EDGE, 0, 0, listW, 16, 0),
                        QuestTranslation.translate("bq_standard.btn.consume", sCon))
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        for (int i = 0; i < task.requiredItems.size(); i++) {
            BigItemStack stack = task.requiredItems.get(i);

            if (stack == null) {
                continue;
            }

            GuiRectangle guiRectangle = new GuiRectangle(0, i * 32 + 16, 28, 28, 0);
            PanelItemSlot slot = PanelItemSlotBuilder.forValue(stack, guiRectangle)
                    .oreDict(true)
                    .build();
            this.addPanel(slot);

            StringBuilder sb = new StringBuilder();

            sb.append(stack.getBaseStack().getDisplayName());

            if (stack.hasOreDict()) sb.append(" (").append(stack.getOreDict()).append(")");

            sb.append("\n")
                    .append(progress[i])
                    .append("/")
                    .append(stack.stackSize)
                    .append("\n");

            if (isComplete || progress[i] >= stack.stackSize) {
                sb.append(EnumChatFormatting.GREEN)
                        .append(QuestTranslation.translate("betterquesting.tooltip.complete"));
            } else {
                sb.append(EnumChatFormatting.RED)
                        .append(QuestTranslation.translate("betterquesting.tooltip.incomplete"));
            }

            PanelTextBox text = new PanelTextBox(new GuiRectangle(32, i * 32 + 16, listW - 28, 28, 0), sb.toString());
            text.setColor(PresetColor.TEXT_MAIN.getColor());
            this.addPanel(text);
        }

        recalcSizes();
    }
}
