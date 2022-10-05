package bq_standard;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * Elevates the player's privileges to OP level for use in command rewards
 */
public class AdminExecute implements ICommandSender {
    private final EntityPlayer player;

    public AdminExecute(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public String getCommandSenderName() {
        return player.getCommandSenderName();
    }

    @Override
    public IChatComponent func_145748_c_() {
        return player.func_145748_c_();
    }

    @Override
    public void addChatMessage(IChatComponent p_145747_1_) {
        player.addChatMessage(p_145747_1_);
    }

    @Override
    public boolean canCommandSenderUseCommand(int p_70003_1_, String p_70003_2_) {
        return true;
    }

    @Override
    public ChunkCoordinates getPlayerCoordinates() {
        return player.getPlayerCoordinates();
    }

    @Override
    public World getEntityWorld() {
        return player.getEntityWorld();
    }
}
