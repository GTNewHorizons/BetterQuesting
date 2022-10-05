package bq_standard.network.handlers;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.tasks.TaskInteractItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

public class NetTaskInteract {
    private static final ResourceLocation ID_NAME = new ResourceLocation("bq_standard:task_interact");

    public static void registerHandler() {
        QuestingAPI.getAPI(ApiReference.PACKET_REG).registerServerHandler(ID_NAME, NetTaskInteract::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void requestInteraction(boolean isHit) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("isHit", isHit);
        QuestingAPI.getAPI(ApiReference.PACKET_SENDER).sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        EntityPlayerMP sender = message.getSecond();
        NBTTagCompound tag = message.getFirst();

        ParticipantInfo pInfo = new ParticipantInfo(sender);
        List<DBEntry<IQuest>> actQuest =
                QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests());

        boolean isHit = tag.getBoolean("isHit");

        for (DBEntry<IQuest> entry : actQuest) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskInteractItem)
                    ((TaskInteractItem) task.getValue())
                            .onInteract(
                                    pInfo,
                                    entry,
                                    null,
                                    null,
                                    -1,
                                    MathHelper.floor_double(sender.posX),
                                    MathHelper.floor_double(sender.posY),
                                    MathHelper.floor_double(sender.posZ),
                                    isHit);
            }
        }
    }
}
