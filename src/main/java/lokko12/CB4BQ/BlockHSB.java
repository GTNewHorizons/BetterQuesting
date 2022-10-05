package lokko12.CB4BQ;

import betterquesting.commands.BQ_CommandAdmin;
import betterquesting.commands.admin.QuestCommandHardcore;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class BlockHSB
extends BlockCommandBlock {
    public BlockHSB() {
        this.setHardness(1.0f);
        this.setBlockName("CB4BQ.HSB");
        this.setBlockTextureName("command_block");
        this.setCreativeTab(BetterQuesting.tabQuesting);
    }
    
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
			new QuestCommandHardcore().runCommand(FMLCommonHandler.instance().getMinecraftServerInstance(), new BQ_CommandAdmin(), player, new String[]{"hardcore"});
        }
        return true;
    }
}
