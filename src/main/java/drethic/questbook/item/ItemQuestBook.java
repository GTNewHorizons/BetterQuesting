package drethic.questbook.item;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.client.gui2.GuiHome;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import drethic.questbook.QuestBook;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class ItemQuestBook extends Item {

    public static boolean hasEffect;

    public ItemQuestBook() {
        this.setTextureName(QuestBook.MODID + ":ItemQuestBook");
        this.setUnlocalizedName("ItemQuestBook");
        this.setCreativeTab(BetterQuesting.tabQuesting);
        hasEffect = false;
    }

    @Override
    public boolean onItemUse(
            ItemStack stack,
            EntityPlayer player,
            World world,
            int x,
            int y,
            int z,
            int side,
            float hitX,
            float hitY,
            float hitZ) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            Minecraft mc = Minecraft.getMinecraft();
            if (BQ_Settings.useBookmark && GuiHome.bookmark != null) {
                mc.displayGuiScreen(GuiHome.bookmark);
            } else {
                mc.displayGuiScreen(new GuiHome(mc.currentScreen));
            }
        }

        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack) {
        return hasEffect;
    }
}
