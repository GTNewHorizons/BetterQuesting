package bq_standard.client.gui.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import bq_standard.client.theme.BQSTextures;
import bq_standard.tasks.TaskInteractItem;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public class PanelTaskInteractItem extends CanvasMinimum {
    private final TaskInteractItem task;
    private final IGuiRect initialRect;

    public PanelTaskInteractItem(IGuiRect rect, TaskInteractItem task) {
        super(rect);
        this.task = task;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();
        int centerWidth = width / 2;

        this.addPanel(new PanelItemSlot(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 48, 0, 32, 32, 0), -1, task.targetItem, false, true));
        this.addPanel(new PanelItemSlot(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth + 16, 0, 32, 32, 0),
                -1,
                task.targetBlock.getItemStack(),
                false,
                true));

        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 8, 0, 16, 16, 0),
                PresetIcon.ICON_RIGHT.getTexture()));
        UUID playerID = QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer);
        int prog = task.getUsersProgress(playerID);
        this.addPanel(new PanelTextBox(
                        new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 16, 18, 32, 14, 0),
                        prog + "/" + task.required)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 48, 40, 24, 24, 0),
                BQSTextures.HAND_LEFT.getTexture()));
        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 24, 40, 24, 24, 0),
                BQSTextures.HAND_RIGHT.getTexture()));
        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth, 40, 24, 24, 0), BQSTextures.ATK_SYMB.getTexture()));
        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth + 24, 40, 24, 24, 0),
                BQSTextures.USE_SYMB.getTexture()));

        IGuiTexture txTick = new GuiTextureColored(PresetIcon.ICON_TICK.getTexture(), new GuiColorStatic(0xFF00FF00));
        IGuiTexture txCross = new GuiTextureColored(PresetIcon.ICON_CROSS.getTexture(), new GuiColorStatic(0xFFFF0000));

        // this.addPanel(new PanelGeneric(new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 32, 56, 8, 8, 0),
        // task.useOffHand ? txTick : txCross));
        // this.addPanel(new PanelGeneric(new GuiTransform(GuiAlign.TOP_LEFT, centerWidth - 8, 56, 8, 8, 0),
        // task.useMainHand ? txTick : txCross));
        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth + 16, 56, 8, 8, 0), task.onHit ? txTick : txCross));
        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, centerWidth + 40, 56, 8, 8, 0),
                task.onInteract ? txTick : txCross));
        recalcSizes();
    }
}
