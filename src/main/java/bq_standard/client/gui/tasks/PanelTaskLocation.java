package bq_standard.client.gui.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.tasks.TaskLocation;
import java.awt.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

public class PanelTaskLocation extends CanvasMinimum {
    private final TaskLocation task;
    private final IGuiRect initialRect;

    public PanelTaskLocation(IGuiRect rect, TaskLocation task) {
        super(rect);
        this.task = task;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();

        String desc = QuestTranslation.translate(task.name);

        if (!task.hideInfo) {
            desc += " (" + TaskLocation.getDimName(task.dim) + ")";

            if (task.range >= 0) {
                desc += "\n"
                        + QuestTranslation.translate(
                                "bq_standard.gui.location", "(" + task.x + ", " + task.y + ", " + task.z + ")");
                desc += "\n"
                        + QuestTranslation.translate(
                                "bq_standard.gui.distance",
                                (int) Minecraft.getMinecraft().thePlayer.getDistance(task.x, task.y, task.z) + "m");
            }
        }

        if (task.isComplete(QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer))) {
            desc += "\n" + EnumChatFormatting.BOLD + EnumChatFormatting.GREEN
                    + QuestTranslation.translate("bq_standard.gui.found");
        } else {
            desc += "\n" + EnumChatFormatting.BOLD + EnumChatFormatting.RED
                    + QuestTranslation.translate("bq_standard.gui.undiscovered");
        }

        int textHeight = (StringUtils.countMatches(desc, "\n") + 1) * 12;
        this.addPanel(new PanelTextBox(new GuiTransform(GuiAlign.TOP_LEFT, 0, 0, width, textHeight, 0), desc)
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        IGuiTexture texCompass = new IGuiTexture() {
            @Override
            public void drawTexture(int x, int y, int width, int height, float zDepth, float partialTick) {
                drawTexture(x, y, width, height, zDepth, partialTick, null);
            }

            @Override
            public void drawTexture(
                    int x, int y, int width, int height, float zDepth, float partialTick, IGuiColor color) {
                Minecraft mc = Minecraft.getMinecraft();

                double la = Math.atan2(task.z - mc.thePlayer.posZ, task.x - mc.thePlayer.posX);
                int radius = width / 2 - 12;
                int cx = x + width / 2;
                int cy = y + height / 2;
                int dx = (int) (Math.cos(la) * radius);
                int dy = (int) (Math.sin(la) * -radius);
                int txtClr = color == null ? 0xFFFFFFFF : color.getRGB();

                Gui.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, Color.BLACK.getRGB());
                RenderUtils.DrawLine(cx - radius, cy - radius, cx + radius, cy - radius, 4, Color.WHITE.getRGB());
                RenderUtils.DrawLine(cx - radius, cy - radius, cx - radius, cy + radius, 4, Color.WHITE.getRGB());
                RenderUtils.DrawLine(cx + radius, cy + radius, cx + radius, cy - radius, 4, Color.WHITE.getRGB());
                RenderUtils.DrawLine(cx + radius, cy + radius, cx - radius, cy + radius, 4, Color.WHITE.getRGB());
                mc.fontRenderer.drawString(EnumChatFormatting.BOLD + "N", cx - 4, cy - radius - 9, txtClr);
                mc.fontRenderer.drawString(EnumChatFormatting.BOLD + "S", cx - 4, cy + radius + 2, txtClr);
                mc.fontRenderer.drawString(EnumChatFormatting.BOLD + "E", cx + radius + 2, cy - 4, txtClr);
                mc.fontRenderer.drawString(EnumChatFormatting.BOLD + "W", cx - radius - 8, cy - 4, txtClr);

                if (task.hideInfo || task.range < 0 || mc.thePlayer.dimension != task.dim) {
                    GL11.glPushMatrix();
                    GL11.glScalef(2F, 2F, 2F);
                    mc.fontRenderer.drawString(
                            EnumChatFormatting.BOLD + "?", cx / 2 - 4, cy / 2 - 4, Color.RED.getRGB());
                    GL11.glPopMatrix();
                } else {
                    RenderUtils.DrawLine(cx, cy, cx + dx, cy - dy, 4, Color.RED.getRGB());
                }
            }

            @Override
            public ResourceLocation getTexture() {
                return null;
            }

            @Override
            public IGuiRect getBounds() {
                return null;
            }
        };

        int innerSize = Math.min(Math.min(initialRect.getWidth(), 128), initialRect.getHeight() - textHeight);
        PanelGeneric innerCanvas = new PanelGeneric(
                new GuiTransform(GuiAlign.TOP_LEFT, (width - innerSize) / 2, textHeight, innerSize, innerSize, 0),
                texCompass,
                PresetColor.TEXT_MAIN.getColor());
        this.addPanel(innerCanvas);
        recalcSizes();
    }
}
