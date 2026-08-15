package betterquesting.items;

import net.minecraft.item.Item;

import betterquesting.core.BetterQuesting;

public class ItemPatternPlaceholder extends Item {

    public ItemPatternPlaceholder() {
        this.setTextureName("betterquesting:pattern_placeholder");
        this.setUnlocalizedName("betterquesting.pattern_placeholder");
        this.setCreativeTab(BetterQuesting.tabQuesting);
    }
}
