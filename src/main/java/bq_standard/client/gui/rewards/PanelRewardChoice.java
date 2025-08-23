package bq_standard.client.gui.rewards;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import bq_standard.client.gui.panels.content.PanelItemSlotBuilder;
import bq_standard.network.handlers.NetRewardChoice;
import bq_standard.rewards.RewardChoice;

public class PanelRewardChoice extends CanvasMinimum {

    private final Map.Entry<UUID, IQuest> quest;
    private final RewardChoice reward;
    private final IGuiRect initialRect;

    public PanelRewardChoice(IGuiRect rect, Map.Entry<UUID, IQuest> quest, RewardChoice reward) {
        super(rect);
        initialRect = rect;
        this.quest = quest;
        this.reward = reward;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        UUID uuid = QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer);
        int sel = reward.getSelecton(uuid);

        GuiTransform guiTransform = new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 0, 0, 32, 32, 0);
        PanelItemSlot slot = PanelItemSlotBuilder.forValue(sel < 0 ? null : reward.choices.get(sel), guiTransform)
            .build();
        this.addPanel(slot);

        final UUID qID = quest.getKey();
        final int rID = quest.getValue()
            .getRewards()
            .getID(reward);

        int listWidth = initialRect.getWidth();
        for (int i = 0; i < reward.choices.size(); i++) {
            BigItemStack stack = reward.choices.get(i);
            GuiRectangle guiRectangle = new GuiRectangle(40, i * 18, 18, 18, 0);
            PanelItemSlot is = PanelItemSlotBuilder.forValue(stack, guiRectangle)
                .build();
            this.addPanel(is);

            this.addPanel(
                new PanelTextBox(
                    new GuiRectangle(62, i * 18 + 4, listWidth - 22, 14, 0),
                    stack.stackSize + " "
                        + stack.getBaseStack()
                            .getDisplayName()).setColor(PresetColor.TEXT_MAIN.getColor()));

            final int sID = i;
            is.setCallback(value -> NetRewardChoice.requestChoice(qID, rID, sID));
        }

        recalcSizes();
    }
}
