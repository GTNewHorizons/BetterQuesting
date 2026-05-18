package betterquesting.network.handlers;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetQuestSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:quest_sync");

    private static final String TAG_QUEST_LIST = "data";
    private static final String TAG_QUEST_CONFIG = "config"; // main quest data
    private static final String TAG_QUEST_REWARDS = "rewards"; // config.rewards
    private static final String TAG_QUEST_PROGRESS = "progress";
    private static final String TAG_GET_CONFIG = "getConfig";
    private static final String TAG_GET_PROGRESS = "getProgress";
    private static final String TAG_MERGE = "merge";
    private static final String TAG_REQUEST_IDS = "requestIDs";
    private static final String TAG_RESET_COMPLETION = "resetCompletion";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestSync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetQuestSync::onClient);
        }
    }

    public static void quickSync(@Nullable UUID questID, boolean config, boolean progress) {
        if (!config && !progress) {
            return;
        }

        Collection<UUID> questIDs = questID == null ? null : Collections.singletonList(questID);

        if (config) {
            sendSync(null, questIDs, true, false); // We're not sending progress in this pass.
        }

        // Send everyone's individual progression
        if (progress) {
            MinecraftServer server = FMLCommonHandler.instance()
                .getMinecraftServerInstance();

            for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
                sendSync(player, questIDs, false, true, true); // Progression only this pass
            }
        }
    }

    public static void sendSync(@Nullable EntityPlayerMP player, @Nullable Collection<UUID> questIDs, boolean config,
        boolean progress) {
        sendSync(player, questIDs, config, progress, false);
    }

    public static void sendSync(@Nullable EntityPlayerMP player, @Nullable Collection<UUID> questIDs, boolean config,
        boolean progress, boolean resetCompletion) {
        if ((!config && !progress) || (questIDs != null && questIDs.isEmpty())) {
            return;
        }

        // Offload this to another thread as it could take a while to build
        BQThreadedIO.INSTANCE.enqueue(() -> {
            NBTTagList questTagList = new NBTTagList();
            final Map<UUID, IQuest> questSubset = questIDs == null ? QuestDatabase.INSTANCE
                : QuestDatabase.INSTANCE.filterKeys(questIDs);
            final List<UUID> pidList = player == null ? null
                : Collections.singletonList(QuestingAPI.getQuestingUUID(player));

            for (Map.Entry<UUID, IQuest> entry : questSubset.entrySet()) {
                UUID questId = entry.getKey();
                IQuest quest = entry.getValue();
                NBTTagCompound questTag = new NBTTagCompound();

                if (config) {
                    final NBTTagCompound configTag = quest.writeToNBT(new NBTTagCompound());

                    if (BQ_Settings.noRewards) {
                        configTag.removeTag(TAG_QUEST_REWARDS);
                    }

                    questTag.setTag(TAG_QUEST_CONFIG, configTag);
                }

                if (progress) {
                    questTag.setTag(TAG_QUEST_PROGRESS, quest.writeProgressToNBT(new NBTTagCompound(), pidList));
                }

                NBTConverter.UuidValueType.QUEST.writeId(questId, questTag);
                questTagList.appendTag(questTag);
            }

            NBTTagCompound payload = new NBTTagCompound();
            payload.setBoolean(TAG_MERGE, !config || questIDs != null);
            payload.setBoolean(TAG_RESET_COMPLETION, resetCompletion);
            payload.setTag(TAG_QUEST_LIST, questTagList);

            if (player == null) {
                PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
            } else {
                PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
            }
        });
    }

    // Asks the server to send specific quest data over
    @SideOnly(Side.CLIENT)
    public static void requestSync(@Nullable Collection<UUID> questIDs, boolean configs, boolean progress) {
        NBTTagCompound payload = new NBTTagCompound();

        if (questIDs != null) {
            payload.setTag(TAG_REQUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        }

        payload.setBoolean(TAG_GET_CONFIG, configs);
        payload.setBoolean(TAG_GET_PROGRESS, progress);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();

        Collection<UUID> reqIDs = null;
        if (payload.hasKey(TAG_REQUEST_IDS, Constants.NBT.TAG_LIST)) {
            reqIDs = NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_REQUEST_IDS);
        }

        sendSync(message.getSecond(), reqIDs, payload.getBoolean(TAG_GET_CONFIG), payload.getBoolean(TAG_GET_PROGRESS));
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound payload) {
        NBTTagList questListTag = payload.getTagList(TAG_QUEST_LIST, Constants.NBT.TAG_COMPOUND);
        boolean merge = payload.getBoolean(TAG_MERGE);
        boolean resetCompletion = payload.getBoolean(TAG_RESET_COMPLETION);
        if (!merge) {
            QuestDatabase.INSTANCE.clear();
        }

        for (int i = 0; i < questListTag.tagCount(); i++) {
            NBTTagCompound questTag = questListTag.getCompoundTagAt(i);
            UUID questID = NBTConverter.UuidValueType.QUEST.tryReadId(questTag)
                .orElse(null);
            if (questID == null) {
                continue;
            }

            IQuest quest = QuestDatabase.INSTANCE.get(questID);

            if (questTag.hasKey(TAG_QUEST_CONFIG, Constants.NBT.TAG_COMPOUND)) {
                if (quest == null) {
                    quest = QuestDatabase.INSTANCE.createNew(questID);
                }
                quest.readFromNBT(questTag.getCompoundTag(TAG_QUEST_CONFIG));
            }

            if (quest != null && questTag.hasKey(TAG_QUEST_PROGRESS, Constants.NBT.TAG_COMPOUND)) {
                // TODO: Fix this properly
                // If there we're not running the LAN server off this client then we overwrite always
                quest.readProgressFromNBT(
                    questTag.getCompoundTag(TAG_QUEST_PROGRESS),
                    !resetCompletion && (merge || Minecraft.getMinecraft()
                        .isIntegratedServerRunning()));
            }
        }

        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.QUEST));
    }
}
