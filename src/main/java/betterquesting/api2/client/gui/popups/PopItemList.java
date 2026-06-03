package betterquesting.api2.client.gui.popups;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.util.MathHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasResizeable;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.ColorTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import bq_standard.client.gui.panels.content.PanelItemSlotBuilder;

public class PopItemList extends CanvasEmpty {

    private final String message;
    private final List<BigItemStack> list;

    public PopItemList(@Nonnull String message, @Nonnull List<BigItemStack> list) {
        super(new GuiTransform(GuiAlign.FULL_BOX));
        this.message = message;
        this.list = list;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        this.addPanel(
            new PanelGeneric(
                new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 1),
                new ColorTexture(new GuiColorStatic(0x80000000))));

        int itemsPerRow = (int) ((getTransform().getWidth() * 0.5f) / 36);
        itemsPerRow = MathHelper.clamp_int(itemsPerRow, 1, list.size());
        int rowCount = MathHelper.ceiling_float_int((float) list.size() / itemsPerRow);
        int popupHeightPx = (int) (Math.min(rowCount, 5.5f) * 36) + 32;
        float popupHeightFlt = popupHeightPx / (float) getTransform().getHeight();

        int popupWidthPx = Math.max(itemsPerRow, 3) * 36 + 20;
        float popupWidthFlt = popupWidthPx / (float) getTransform().getWidth();

        CanvasResizeable cvBox = new CanvasResizeable(
            new GuiTransform(
                new Vector4f(
                    (1 - popupWidthFlt) / 2f,
                    (1 - popupHeightFlt) / 2f,
                    1 - (1 - popupWidthFlt) / 2f,
                    1 - (1 - popupHeightFlt) / 2f)),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBox);
        int cvWidth = cvBox.getTransform()
            .getWidth();
        int cvHeight = cvBox.getTransform()
            .getHeight();

        cvBox.addPanel(
            new PanelTextBox(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 8, 8), 0), message)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor()));

        int scrollStartYPx = 20;
        int scrollWidthPx = Math.min(list.size(), itemsPerRow) * 36;
        float scrollWidthFlt = scrollWidthPx / (float) cvWidth;

        CanvasScrolling scrolling = new CanvasScrolling(
            new GuiTransform(
                new Vector4f(
                    (1 - scrollWidthFlt) / 2f,
                    scrollStartYPx / (float) cvHeight,
                    1 - (1 - scrollWidthFlt) / 2f,
                    0.95f)));
        cvBox.addPanel(scrolling);

        for (int i = 0; i < list.size(); i++) {
            int itemsInRow = Math.min(list.size() - (i / itemsPerRow) * itemsPerRow, itemsPerRow);
            int xOffset = (scrolling.getTransform()
                .getWidth() / 2 - itemsInRow * 36 / 2);
            BigItemStack stack = list.get(i);
            GuiRectangle rect = new GuiRectangle((i % itemsPerRow) * 36 + xOffset, (i / itemsPerRow) * 36, 32, 32, 10);
            scrolling.addPanel(
                PanelItemSlotBuilder.forValue(stack, rect)
                    .oreDict(false)
                    .build());
        }

        PanelButton closeBtn = new PanelButton(
            new GuiTransform(
                new Vector4f(0.5f, 1 - (1 - popupHeightFlt) / 2f, 0.5f, 1 - (1 - popupHeightFlt) / 2f),
                -popupWidthPx / 2,
                3,
                popupWidthPx,
                16,
                0),
            -1,
            "Close");
        addPanel(closeBtn);
        closeBtn.setClickAction(btn -> close());
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        GL11.glPushMatrix();
        // Make sure items are rendering over the items in the background
        GL11.glTranslatef(0, 0, 10);
        super.drawPanel(mx, my, partialTick);
        GL11.glPopMatrix();
    }

    private void close() {
        if (SceneController.getActiveScene() != null) SceneController.getActiveScene()
            .closePopup();
    }
}
