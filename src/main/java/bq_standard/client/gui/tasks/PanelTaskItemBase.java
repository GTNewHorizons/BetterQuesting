package bq_standard.client.gui.tasks;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.client.gui.panels.content.PanelItemSlotBuilder;
import bq_standard.tasks.base.TaskProgressableBase;

public abstract class PanelTaskItemBase<T extends TaskProgressableBase<int[]>> extends CanvasMinimum {

    protected final T task;
    protected final IGuiRect initialRect;

    private final PanelItemSlot[] itemSlots;
    private final PanelTextBox[] textBoxes;
    private final BigItemStack[] stackCache;
    private final String[] oreDictName;
    private int[] progress;
    private boolean isComplete;

    public PanelTaskItemBase(IGuiRect rect, T task) {
        super(rect);
        this.task = task;
        initialRect = rect;
        int reqItemSize = getItemCount();
        itemSlots = new PanelItemSlot[reqItemSize];
        textBoxes = new PanelTextBox[reqItemSize];
        stackCache = new BigItemStack[reqItemSize];
        oreDictName = new String[reqItemSize];
    }

    protected abstract int getItemCount();

    protected abstract BigItemStack getItemStack(int index);

    protected abstract GuiRectangle createItemSlotRect(int i);

    protected abstract GuiRectangle createTextBoxRect(int i, int width);

    protected abstract void initPanelExtras(int listW);

    @Override
    public void initPanel() {
        super.initPanel();
        int listW = initialRect.getWidth();

        UUID uuid = QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer);
        progress = task.getUsersProgress(uuid);
        isComplete = task.isComplete(uuid);
        initPanelExtras(listW);

        for (int i = 0; i < getItemCount(); i++) {
            BigItemStack stack = getItemStack(i);

            if (stack == null) {
                continue;
            }
            oreDictName[i] = stack.hasOreDict() ? stack.getOreDict() : null;

            PanelItemSlot slot = PanelItemSlotBuilder.forValue(stack, createItemSlotRect(i))
                .oreDict(true)
                .popupVariants(stack.hasOreDict())
                .build();
            this.addPanel(slot);
            itemSlots[i] = slot;

            String text = getRequiredItemText(i);
            PanelTextBox textBox = new PanelTextBox(createTextBoxRect(i, listW), text);
            textBox.setColor(PresetColor.TEXT_MAIN.getColor());
            this.addPanel(textBox);
            textBoxes[i] = textBox;
        }

        recalcSizes();
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        for (int i = 0; i < getItemCount(); i++) {
            if (itemSlots[i] == null) continue;
            BigItemStack stack = itemSlots[i].getStoredValue();
            if (stack != stackCache[i]) {
                stackCache[i] = stack;
                textBoxes[i].setText(getRequiredItemText(i));
            }
        }

        super.drawPanel(mx, my, partialTick);
    }

    private String getRequiredItemText(int index) {
        StringBuilder sb = new StringBuilder();
        BigItemStack stack = itemSlots[index].getStoredValue();
        sb.append(
            stack.getBaseStack()
                .getDisplayName());

        if (oreDictName[index] != null) sb.append(" (")
            .append(oreDictName[index])
            .append(")");

        sb.append("\n")
            .append(progress[index])
            .append("/")
            .append(stack.stackSize)
            .append("\n");

        if (isComplete || progress[index] >= stack.stackSize) {
            sb.append(EnumChatFormatting.GREEN)
                .append(QuestTranslation.translate("betterquesting.tooltip.complete"));
        } else {
            sb.append(EnumChatFormatting.RED)
                .append(QuestTranslation.translate("betterquesting.tooltip.incomplete"));
        }
        return sb.toString();
    }

}
