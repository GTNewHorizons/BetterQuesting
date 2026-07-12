package bq_standard.client.gui.tasks;

import net.minecraft.init.Blocks;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.resources.textures.ItemTexture;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import bq_standard.tasks.TaskCrafting;

public class PanelTaskCrafting extends PanelTaskItemBase<TaskCrafting> {

    public PanelTaskCrafting(IGuiRect rect, TaskCrafting task) {
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
        return new GuiRectangle(0, i * 28 + 24, 28, 28, 0);
    }

    @Override
    protected GuiRectangle createTextBoxRect(int i, int width) {
        return new GuiRectangle(36, i * 28 + 24, width - 36, 28, 0);
    }

    @Override
    protected void initPanelExtras(int listW) {
        IGuiTexture txTick = new GuiTextureColored(PresetIcon.ICON_TICK.getTexture(), new GuiColorStatic(0xFF00FF00));
        IGuiTexture txCross = new GuiTextureColored(PresetIcon.ICON_CROSS.getTexture(), new GuiColorStatic(0xFFFF0000));

        this.addPanel(
            new PanelGeneric(
                new GuiRectangle(0, 0, 16, 16, 0),
                new ItemTexture(new BigItemStack(Blocks.crafting_table))));
        this.addPanel(new PanelGeneric(new GuiRectangle(10, 10, 6, 6, 0), task.allowCraft ? txTick : txCross));

        this.addPanel(
            new PanelGeneric(new GuiRectangle(24, 0, 16, 16, 0), new ItemTexture(new BigItemStack(Blocks.furnace))));
        this.addPanel(new PanelGeneric(new GuiRectangle(34, 10, 6, 6, 0), task.allowSmelt ? txTick : txCross));

        this.addPanel(
            new PanelGeneric(new GuiRectangle(48, 0, 16, 16, 0), new ItemTexture(new BigItemStack(Blocks.anvil))));
        this.addPanel(new PanelGeneric(new GuiRectangle(58, 10, 6, 6, 0), task.allowAnvil ? txTick : txCross));
    }
}
