package drethic.questbook.item;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;

public final class QBItems {
    public static Item ItemQuestBook = new ItemQuestBook();

    public static final void init() {
        GameRegistry.registerItem(ItemQuestBook, "ItemQuestBook");
    }
}
