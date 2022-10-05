package bq_standard.client.gui;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.client.theme.BQSTextures;
import java.util.List;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

public class GuiLootChest extends GuiScreenCanvas {
    private static final ResourceLocation SND_OPEN = new ResourceLocation("randmom.chestopen");
    private final String title;
    private final List<BigItemStack> rewards;

    public GuiLootChest(GuiScreen parent, List<BigItemStack> rewards, String title) {
        super(parent);
        this.rewards = rewards;
        this.title = title;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(SND_OPEN, 1.0F));

        this.addPanel(new PanelGeneric(
                new GuiTransform(GuiAlign.MID_CENTER, -64, 0, 128, 68, 0), BQSTextures.LOOT_CHEST.getTexture()));
        this.addPanel(new PanelTextBox(
                        new GuiTransform(GuiAlign.MID_CENTER, -64, 40, 128, 56, -1), QuestTranslation.translate(title))
                .setAlignment(1));

        IGuiTexture texGlow = BQSTextures.LOOT_GLOW.getTexture();
        int rowMax = (int) Math.ceil(rewards.size() / 8F);
        rowMax = rewards.size() / rowMax;

        for (int i = 0; i < rewards.size(); i++) {
            BigItemStack stack = rewards.get(i);

            int rowX = i % rowMax;
            int rowY = i / rowMax;
            int rowSize = Math.min(rewards.size() - rowY * rowMax, rowMax);

            rowX = -(rowSize * 36) / 2 + (rowX * 36);
            rowY = -36 - (rowY * 36);

            this.addPanel(
                    new PanelGeneric(new GuiTransform(GuiAlign.MID_CENTER, rowX + 2, rowY + 2, 32, 32, 0), texGlow));
            this.addPanel(new PanelItemSlot(
                            new GuiTransform(GuiAlign.MID_CENTER, rowX + 10, rowY + 10, 16, 16, -1), -1, stack)
                    .setTextures(null, null, null));
        }
    }
}
