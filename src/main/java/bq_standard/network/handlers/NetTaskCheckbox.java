package bq_standard.network.handlers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import bq_standard.tasks.TaskCheckbox;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import drethic.questbook.config.QBConfig;

public class NetTaskCheckbox {

    private static final ResourceLocation ID_NAME = new ResourceLocation("bq_standard:task_checkbox");

    private static final String TAG_TASK_ID = "taskID";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetTaskCheckbox::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClick(UUID questID, int taskID) {
        NBTTagCompound payload = NBTConverter.UuidValueType.QUEST.writeId(questID);
        payload.setInteger(TAG_TASK_ID, taskID);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        Optional<UUID> questId = NBTConverter.UuidValueType.QUEST.tryReadId(payload);
        int taskId = payload.hasKey(TAG_TASK_ID, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_TASK_ID) : -1;

        if (questId.isEmpty() || taskId < 0) {
            return;
        }

        IQuest quest = QuestDatabase.INSTANCE.get(questId.get());
        if (quest == null) return;

        ITask task = quest.getTasks()
            .getValue(taskId);
        if (!(task instanceof TaskCheckbox)) return;

        ParticipantInfo pInfo = new ParticipantInfo(sender);
        List<UUID> playerIdsToMark = QBConfig.fullySyncQuests ? pInfo.ALL_UUIDS : Collections.singletonList(pInfo.UUID);

        for (UUID playerId : playerIdsToMark) {
            task.setComplete(playerId);

            EntityPlayerMP player = QuestingAPI.getPlayer(playerId);
            if (player == null) continue;

            QuestCache qc = (QuestCache) player.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
            if (qc != null) qc.markQuestDirty(questId.get());
        }
    }
}
