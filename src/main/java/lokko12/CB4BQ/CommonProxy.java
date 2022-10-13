package lokko12.CB4BQ;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class CommonProxy extends ServerProxy {

    public CommonProxy() {
        super();
    }

    @Override
    public void init(FMLInitializationEvent e) {
        super.init(e);
        GameRegistry.addShapelessRecipe(
                new ItemStack(CB4BQ.BlockDLB),
                new ItemStack(Items.stick),
                new ItemStack(Blocks.dirt),
                new ItemStack(Blocks.gravel));
        // GameRegistry.addShapelessRecipe(new ItemStack(BlockDLB), new ItemStack(BlockDSB));
        // GameRegistry.addShapelessRecipe(new ItemStack(BlockDSB), new ItemStack(BlockHSB));
        GameRegistry.addShapelessRecipe(new ItemStack(CB4BQ.BlockDLB), new ItemStack(CB4BQ.BlockHSB));
        GameRegistry.addShapelessRecipe(new ItemStack(CB4BQ.BlockHSB), new ItemStack(CB4BQ.BlockREB));
        GameRegistry.addShapelessRecipe(new ItemStack(CB4BQ.BlockREB), new ItemStack(CB4BQ.BlockDLB));
    }
}
