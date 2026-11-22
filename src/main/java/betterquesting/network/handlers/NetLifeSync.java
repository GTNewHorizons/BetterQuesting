package betterquesting.network.handlers;

import java.util.Arrays;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.network.QuestingPacket;
import betterquesting.core.BetterQuesting;
import betterquesting.network.IntegratedServerHost;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.storage.LifeDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetLifeSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:life_sync");

    public static void registerHandler() {
        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetLifeSync::onClient);
        }
    }

    public static void sendSync(@Nullable EntityPlayerMP[] players, @Nullable UUID[] playerIDs) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(
            "data",
            LifeDatabase.INSTANCE
                .writeToNBT(new NBTTagCompound(), playerIDs == null ? null : Arrays.asList(playerIDs)));
        payload.setBoolean("merge", playerIDs != null);

        if (players != null) {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), players);
        } else {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        // The integrated server host shares the server's databases: the server already applied this data.
        if (IntegratedServerHost.isHost()) {
            return;
        }

        LifeDatabase.INSTANCE.readFromNBT(message.getCompoundTag("data"), message.getBoolean("merge"));
    }
}
