package lokko12.CB4BQ;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

public class ServerProxy {
	
	public ServerProxy() {
	}
    public void init(FMLInitializationEvent e) {
		GameRegistry.registerBlock(CB4BQ.BlockDLB, "BlockDLB");
		//GameRegistry.registerBlock(BlockDSB, "BlockDSB");
		GameRegistry.registerBlock(CB4BQ.BlockHSB, "BlockHSB");
		GameRegistry.registerBlock(CB4BQ.BlockREB, "BlockREB");
    }

}