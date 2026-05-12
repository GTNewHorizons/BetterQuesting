package betterquesting.network.handlers;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Level;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.storage.QuestSettings;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetSettingSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:setting_sync");

    private static final String TAG_SETTINGS = "data";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetSettingSync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetSettingSync::onClient);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void requestEdit() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(TAG_SETTINGS, QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    public static void sendSync(@Nullable EntityPlayerMP player) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(TAG_SETTINGS, QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        if (player != null) {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
        } else {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound payload) {
        QuestSettings.INSTANCE.readFromNBT(payload.getCompoundTag(TAG_SETTINGS));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        boolean isOP = FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getConfigurationManager()
            .func_152596_g(sender.getGameProfile());

        if (!isOP) {
            BetterQuesting.logger.log(
                Level.WARN,
                "Player {} (UUID: {}) tried to edit settings without OP permissions!",
                sender.getCommandSenderName(),
                QuestingAPI.getQuestingUUID(sender));
            sendSync(sender);
            return;
        }

        QuestSettings.INSTANCE.readFromNBT(payload.getCompoundTag(TAG_SETTINGS));
        SaveLoadHandler.INSTANCE.markDirty();
        sendSync(null);
    }
}
