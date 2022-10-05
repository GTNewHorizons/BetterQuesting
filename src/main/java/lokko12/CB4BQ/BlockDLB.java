package lokko12.CB4BQ;

import java.lang.reflect.InvocationTargetException;

import betterquesting.commands.BQ_Commands;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.core.BetterQuesting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

public class BlockDLB
extends BlockCommandBlock {
    public BlockDLB() {
        this.setHardness(1.0f);
        this.setBlockName("CB4BQ.DLB");
        this.setBlockTextureName("command_block");
        this.setCreativeTab(BetterQuesting.tabQuesting);
    }
    
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
        	try {
        		Class c = Class.forName("betterquesting.commands.BQ_Commands");
        		//try to use modded version of BQ
        		new QuestCommandDefaults().runCommand((CommandBase) c.getConstructor().newInstance(), (ICommandSender)player, new String[]{"default", "load"});
        	} catch (ClassNotFoundException e) {
        		//fallback to vanilla
        		try {
        			Class c = Class.forName("betterquesting.commands.BQ_CommandAdmin");
        		 new QuestCommandDefaults().runCommand((CommandBase)c.getConstructor().newInstance(), (ICommandSender)player, new String[]{"default", "load"});
        		} catch (ClassNotFoundException f) {
        			CB4BQ.CLClogger.fatal("Something went horribly wrong");
        			CB4BQ.CLClogger.catching(e);
        			CB4BQ.CLClogger.catching(f);
				} catch (InstantiationException e1) {
					e1.printStackTrace();
				} catch (IllegalAccessException e1) {
					e1.printStackTrace();
				} catch (IllegalArgumentException e1) {
					e1.printStackTrace();
				} catch (InvocationTargetException e1) {				
					e1.printStackTrace();
				} catch (NoSuchMethodException e1) {
					e1.printStackTrace();
				} catch (SecurityException e1) {
					e1.printStackTrace();
				}
        	} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
        }
        return true;
    }
}
