package betterquesting.network.handlers;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.MarkerManager;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.blocks.TileSubmitStation;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetStationEdit {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:station_edit");

    private static final int ACTION_RESET = 0;
    private static final int ACTION_SETUP = 1;

    private static final String TAG_ACTION = "action";
    private static final String TAG_TASK_ID = "taskID";
    private static final String TAG_TILE_POS_X = "tilePosX";
    private static final String TAG_TILE_POS_Y = "tilePosY";
    private static final String TAG_TILE_POS_Z = "tilePosZ";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetStationEdit::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void resetStation(int posX, int posY, int posZ) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_RESET);
        payload.setInteger(TAG_TILE_POS_X, posX);
        payload.setInteger(TAG_TILE_POS_Y, posY);
        payload.setInteger(TAG_TILE_POS_Z, posZ);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void setupStation(int posX, int posY, int posZ, UUID questID, int taskID) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_SETUP);
        NBTConverter.UuidValueType.QUEST.writeId(questID, payload);
        payload.setInteger(TAG_TASK_ID, taskID);
        payload.setInteger(TAG_TILE_POS_X, posX);
        payload.setInteger(TAG_TILE_POS_Y, posY);
        payload.setInteger(TAG_TILE_POS_Z, posZ);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        int px = payload.getInteger(TAG_TILE_POS_X);
        int py = payload.getInteger(TAG_TILE_POS_Y);
        int pz = payload.getInteger(TAG_TILE_POS_Z);
        TileEntity tile = sender.worldObj.getTileEntity(px, py, pz);

        if (!(tile instanceof TileSubmitStation oss)) return;
        if (!oss.isUseableByPlayer(sender)) return;

        switch (payload.getInteger(TAG_ACTION)) {
            case ACTION_RESET -> oss.reset();
            case ACTION_SETUP -> {
                UUID senderId = QuestingAPI.getQuestingUUID(sender);
                IQuest quest = QuestDatabase.INSTANCE.get(NBTConverter.UuidValueType.QUEST.readId(payload));
                ITask task = quest == null ? null
                    : quest.getTasks()
                        .getValue(payload.getInteger(TAG_TASK_ID));

                if (quest != null && task != null) {
                    if (!quest.isUnlocked(senderId) || !task.isComplete(senderId)) {
                        BetterQuesting.logger.warn(
                            MarkerManager.getMarker("SuspiciousPackets"),
                            "Player {} tried to set task to completed or not yet unlocked one.",
                            sender.getGameProfile());
                    }
                    oss.setupTask(senderId, quest, task);
                }
            }
        }
    }
}
