package lokko12.CB4BQ;

import org.apache.logging.log4j.LogManager;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

@Mod(
		modid = "cb4bq", name = "Command Blocks for Better Questing", version = BetterQuesting.VERSION,
		dependencies = "required-after:betterquesting"
		)
public class CB4BQ {

	public static Block BlockDLB = new BlockDLB();
	public static Block BlockDSB = new BlockDSB();
	public static Block BlockHSB = new BlockHSB();
	public static Block BlockREB = new BlockREB();

	public static final org.apache.logging.log4j.Logger CLClogger = LogManager.getLogger("cb4bq");
	
	@Instance(value = "CropLoadCore")
	public static CB4BQ instance;
	
	@SidedProxy(clientSide="lokko12.CB4BQ.CommonProxy",serverSide="lokko12.CB4BQ.ServerProxy")
	public static ServerProxy proxy;
	
	@EventHandler
	public void init(FMLInitializationEvent e) {
		proxy.init(e);
	}
}
