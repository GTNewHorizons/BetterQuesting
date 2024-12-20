package bq_standard.rewards;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.AbstractReward;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import bq_standard.AdminExecute;
import bq_standard.client.gui.rewards.PanelRewardCommand;
import bq_standard.handlers.EventHandler;
import bq_standard.rewards.factory.FactoryRewardCommand;
import io.netty.buffer.ByteBuf;

public class RewardCommand extends AbstractReward implements IReward {

    public String command = "/say VAR_NAME Claimed a reward";
    public boolean hideCmd = false;
    public boolean viaPlayer = false;

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryRewardCommand.INSTANCE.getRegistryName();
    }

    @Override
    public String getUnlocalisedName() {
        return "bq_standard.reward.command";
    }

    @Override
    public boolean canClaim(EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        return true;
    }

    @Override
    protected boolean getDefaultIgnoreDisabled() {
        return true;
    }

    @Override
    protected void claimReward0(final EntityPlayer player, Map.Entry<UUID, IQuest> quest) {
        if (player.worldObj.isRemote) return;

        String tmp = command.replaceAll("VAR_NAME", player.getCommandSenderName());
        final String finCom = tmp.replaceAll(
            "VAR_UUID",
            QuestingAPI.getQuestingUUID(player)
                .toString());
        final MinecraftServer server = MinecraftServer.getServer();

        if (viaPlayer) {
            EventHandler.scheduleServerTask(
                () -> server.getCommandManager()
                    .executeCommand(new AdminExecute(player), finCom));
        } else {
            final RewardCommandSender cmdSender = new RewardCommandSender(
                player.worldObj,
                (int) player.posX,
                (int) player.posY,
                (int) player.posZ);

            EventHandler.scheduleServerTask(
                () -> server.getCommandManager()
                    .executeCommand(cmdSender, finCom));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        command = nbt.getString("command");
        hideCmd = nbt.getBoolean("hideCommand");
        viaPlayer = nbt.getBoolean("viaPlayer");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("command", command);
        nbt.setBoolean("hideCommand", hideCmd);
        nbt.setBoolean("viaPlayer", viaPlayer);
        return nbt;
    }

    @Override
    public IGuiPanel getRewardGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelRewardCommand(rect, this);
    }

    @Override
    public GuiScreen getRewardEditor(GuiScreen screen, Map.Entry<UUID, IQuest> quest) {
        return null;
    }

    public static class RewardCommandSender extends CommandBlockLogic {

        private final World world;
        private final ChunkCoordinates blockLoc;

        private RewardCommandSender(World world, int x, int y, int z) {
            blockLoc = new ChunkCoordinates(x, y, z);
            this.world = world;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return blockLoc;
        }

        @Override
        public World getEntityWorld() {
            return world;
        }

        @Override
        public void func_145756_e() {}

        @Override
        public int func_145751_f() {
            return 0;
        }

        @Override
        public void func_145757_a(ByteBuf p_145757_1_) {}

        @Override
        public String getCommandSenderName() {
            return "BetterQuesting";
        }
    }
}
