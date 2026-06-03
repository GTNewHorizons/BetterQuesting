package bq_standard.client.gui.panels.content;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import betterquesting.api2.client.gui.popups.PopItemList;
import betterquesting.api2.utils.QuestTranslation;
import codechicken.nei.api.ShortcutInputHandler;

/**
 * NEI compatible ItemSlot
 * <p>
 * Process any input while hovering this panel and pass the ItemStack to NEI allowing it to process
 * it's internal keybindings.
 */
public class PanelInteractiveItemSlot extends PanelItemSlot {

    private boolean isMouseHovered;
    private final boolean popupVariants;

    private static final ResourceLocation CLICK_SND = new ResourceLocation("gui.button.press");

    public PanelInteractiveItemSlot(IGuiRect rect, int id, BigItemStack value, boolean showCount, boolean oreDict,
        boolean popupVariants) {
        super(rect, id, value, showCount, oreDict);
        this.popupVariants = popupVariants;
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        if (isMouseHovered) {
            if (ShortcutInputHandler.handleKeyEvent(getBaseStackOfSameSize())) {
                playClickSound();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (isMouseHovered && getCallback() == null) {
            if (popupVariants && GuiContainer.isShiftKeyDown() && SceneController.getActiveScene() != null) {
                SceneController.getActiveScene()
                    .openPopup(
                        new PopItemList(QuestTranslation.translate("betterquesting.title.valid_items"), oreVariants));
            } else if (ShortcutInputHandler.handleMouseClick(getBaseStackOfSameSize())) {
                playClickSound();
                return true;
            }
        }

        return super.onMouseClick(mx, my, click);
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        isMouseHovered = isActive() && getTransform().contains(mx, my);
        super.drawPanel(mx, my, partialTick);
    }

    private ItemStack getBaseStackOfSameSize() {
        BigItemStack bigItemStack = getStoredValue();

        ItemStack itemStack = bigItemStack.getBaseStack();
        itemStack.stackSize = bigItemStack.stackSize;

        return itemStack;
    }

    private void playClickSound() {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(PositionedSoundRecord.func_147674_a(CLICK_SND, 1.0F));
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        List<String> list = super.getTooltip(mx, my);
        if (list != null && popupVariants) {
            // Need to copy as some mods might return an immutable list
            List<String> newList = new ArrayList<>(list.size() + 1);
            newList.addAll(list);
            newList.add(
                EnumChatFormatting.GRAY + EnumChatFormatting.ITALIC.toString()
                    + QuestTranslation.translate("betterquesting.tooltip.popup_valid_items"));
            return newList;
        }
        return list;
    }
}
