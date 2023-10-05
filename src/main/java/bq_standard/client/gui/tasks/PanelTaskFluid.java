package bq_standard.client.gui.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelFluidSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.TaskFluid;
import codechicken.nei.recipe.GuiCraftingRecipe;
import cpw.mods.fml.common.Optional.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import java.util.UUID;

public class PanelTaskFluid extends CanvasMinimum {
    private final TaskFluid task;
    private final IGuiRect initialRect;

    public PanelTaskFluid(IGuiRect rect, TaskFluid task) {
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
                        new GuiTransform(GuiAlign.TOP_EDGE, 0, 0, listW, 12, 0),
                        QuestTranslation.translate("bq_standard.btn.consume", sCon))
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        for (int i = 0; i < task.requiredFluids.size(); i++) {
            FluidStack stack = task.requiredFluids.get(i);

            if (stack == null) {
                continue;
            }

            PanelFluidSlot slot = new PanelFluidSlot(new GuiRectangle(0, i * 28 + 12, 28, 28, 0), -1, stack);
            if (BQ_Standard.hasNEI) slot.setCallback(this::lookupRecipe);
            this.addPanel(slot);

            StringBuilder sb = new StringBuilder();

            sb.append(stack.getLocalizedName()).append("\n");
            sb.append(progress[i]).append("/").append(stack.amount).append("mB\n");

            if (progress[i] >= stack.amount || isComplete) {
                sb.append(EnumChatFormatting.GREEN)
                        .append(QuestTranslation.translate("betterquesting.tooltip.complete"));
            } else {
                sb.append(EnumChatFormatting.RED)
                        .append(QuestTranslation.translate("betterquesting.tooltip.incomplete"));
            }

            PanelTextBox text = new PanelTextBox(new GuiRectangle(36, i * 28 + 12, listW - 36, 28, 0), sb.toString());
            text.setColor(PresetColor.TEXT_MAIN.getColor());
            this.addPanel(text);
        }
        recalcSizes();
    }

    @Method(modid = "NotEnoughItems")
    private void lookupRecipe(FluidStack fluid) {
        GuiCraftingRecipe.openRecipeGui("fluid", fluid);
    }
}
