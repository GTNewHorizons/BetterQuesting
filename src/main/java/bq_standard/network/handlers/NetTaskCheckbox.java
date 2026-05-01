package bq_standard.network.handlers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.questing.mutation.QuestMutationService;
import betterquesting.questing.mutation.QuestParticipantResolver;
import betterquesting.questing.mutation.QuestProgressResult;
import betterquesting.questing.sync.QuestSyncService;
import bq_standard.tasks.TaskCheckbox;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import drethic.questbook.config.QBConfig;

public class NetTaskCheckbox {

    private static final ResourceLocation ID_NAME = new ResourceLocation("bq_standard:task_checkbox");

    public static void registerHandler() {
        QuestingAPI.getAPI(ApiReference.PACKET_REG)
            .registerServerHandler(ID_NAME, NetTaskCheckbox::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClick(UUID questID, int taskID) {
        NBTTagCompound payload = NBTConverter.UuidValueType.QUEST.writeId(questID);
        payload.setInteger("taskID", taskID);
        QuestingAPI.getAPI(ApiReference.PACKET_SENDER)
            .sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound data = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        Optional<UUID> qId = NBTConverter.UuidValueType.QUEST.tryReadId(data);
        int tId = !data.hasKey("taskID", 99) ? -1 : data.getInteger("taskID");

        if (qId.isPresent() && tId >= 0) {
            IQuest quest = QuestingAPI.getAPI(ApiReference.QUEST_DB)
                .get(qId.get());
            ITask task = quest == null ? null
                : quest.getTasks()
                    .getValue(tId);

            if (task instanceof TaskCheckbox) {
                List<UUID> playersToMark = QuestParticipantResolver
                    .resolvePlayerProgressParticipants(sender, QBConfig.fullySyncQuests);

                QuestProgressResult result = QuestMutationService
                    .setTaskComplete(qId.get(), quest, task, sender, playersToMark);

                QuestSyncService.notifyQuestsChanged(result.getChanges());
                QuestSyncService.refreshCachesAndFlushDirtyProgress(result.getAffectedPlayers());
            }
        }
    }
}
