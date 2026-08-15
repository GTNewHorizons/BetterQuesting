package betterquesting.items;

import net.minecraft.item.Item;
import betterquesting.core.BetterQuesting;

public class ItemFakePattern extends Item {
    public ItemFakePattern() {
        this.setTextureName("betterquesting:fake_pattern");
        this.setUnlocalizedName("betterquesting.fake_pattern");
        this.setCreativeTab(BetterQuesting.tabQuesting);
    }
}
