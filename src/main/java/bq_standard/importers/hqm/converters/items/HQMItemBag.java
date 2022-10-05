package bq_standard.importers.hqm.converters.items;

import betterquesting.api.utils.BigItemStack;
import bq_standard.core.BQ_Standard;
import net.minecraft.nbt.NBTTagCompound;

public class HQMItemBag implements HQMItem {
    @Override
    public BigItemStack convertItem(int damage, int amount, NBTTagCompound tags) {
        return new BigItemStack(BQ_Standard.lootChest, amount, damage * 25);
    }
}
