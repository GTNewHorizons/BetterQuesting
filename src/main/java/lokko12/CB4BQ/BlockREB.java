package lokko12.CB4BQ;

import betterquesting.commands.BQ_CommandAdmin;
import betterquesting.commands.admin.QuestCommandReset;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class BlockREB
        extends BlockCommandBlock {
    public BlockREB() {
        this.setHardness(1.0f);
        this.setBlockName("CB4BQ.REB");
        this.setBlockTextureName("command_block");
        this.setCreativeTab(BetterQuesting.tabQuesting);
    }

    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            new QuestCommandReset().runCommand(FMLCommonHandler.instance().getMinecraftServerInstance(), new BQ_CommandAdmin(), player, new String[]{"reset", "all", player.getUniqueID().toString()});
        }
        return true;
    }
}
