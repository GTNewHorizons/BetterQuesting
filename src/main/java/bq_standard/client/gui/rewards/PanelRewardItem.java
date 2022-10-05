package bq_standard.client.gui.rewards;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.*;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import bq_standard.client.gui.panels.content.PanelItemSlotBuilder;
import bq_standard.rewards.RewardItem;

public class PanelRewardItem extends CanvasMinimum {
    private final RewardItem reward;
    private final IGuiRect initialRect;

    public PanelRewardItem(IGuiRect rect, RewardItem reward) {
        super(rect);
        this.reward = reward;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        int listWidth = initialRect.getWidth();
        for (int i = 0; i < reward.items.size(); i++) {
            BigItemStack stack = reward.items.get(i);
            GuiRectangle rectangle = new GuiRectangle(0, i * 18, 18, 18, 0);
            PanelItemSlot is = PanelItemSlotBuilder.forValue(stack, rectangle)
                    .showCount(true)
                    .build();
            this.addPanel(is);

            this.addPanel(new PanelTextBox(
                            new GuiRectangle(22, i * 18 + 4, listWidth - 22, 14, 0),
                            stack.stackSize + " " + stack.getBaseStack().getDisplayName())
                    .setColor(PresetColor.TEXT_MAIN.getColor()));
        }

        recalcSizes();
    }
}
