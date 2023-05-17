package bq_standard.importers.hqm.converters.items;

import betterquesting.api.utils.BigItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

public class HQMItemHeart implements HQMItem {
    private final Item bqHeart;

    public HQMItemHeart() {
        bqHeart = (Item)Item.itemRegistry.getObject("betterquesting:extra_life");
    }

    @Override
    public BigItemStack convertItem(int damage, int amount, NBTTagCompound tags) {
        int amt = amount;
        int dmg = 0;

        switch (damage) {
            case 0:
                dmg = 2;
                break;
            case 1:
                dmg = 1;
                break;
            case 2:
                dmg = 2;
                amt *= 3;
                break;
        }

        return new BigItemStack(bqHeart, amt, dmg);
    }
}
