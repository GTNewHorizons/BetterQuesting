package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetQuestEdit {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:quest_edit");

    private static final int ACTION_EDIT = 0;
    private static final int ACTION_DELETE = 1;
    private static final int ACTION_COMPLETE = 2;
    private static final int ACTION_CREATE = 3;

    private static final String TAG_ACTION = "action";
    private static final String TAG_QUEST_LIST = "questList";
    private static final String TAG_QUEST = "quest";
    private static final String TAG_QUEST_IDS = "questIDs";
    private static final String TAG_COMPLETE = "complete";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestEdit::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetQuestEdit::onClient);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void requestEdit(@NotNull Map<UUID, IQuest> questsToEdit) {
        NBTTagList questList = writeQuestList(questsToEdit);
        if (questList.tagCount() == 0) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_EDIT);
        payload.setTag(TAG_QUEST_LIST, questList);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestDelete(@NotNull Collection<UUID> questIDs) {
        if (questIDs.isEmpty()) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        payload.setTag(TAG_QUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestComplete(@NotNull Collection<UUID> questIDs, boolean complete) {
        if (questIDs.isEmpty()) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_COMPLETE);
        payload.setTag(TAG_QUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        payload.setBoolean(TAG_COMPLETE, complete);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestCreate(@NotNull Map<UUID, IQuest> questsToCreate) {
        NBTTagList questList = writeQuestList(questsToCreate);
        if (questList.tagCount() == 0) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_CREATE);
        payload.setTag(TAG_QUEST_LIST, questList);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestCreate(@NotNull UUID questID) {
        requestCreate(Collections.singletonMap(questID, null));
    }

    @SideOnly(Side.CLIENT)
    public static void requestCreate() {
        requestCreate(QuestDatabase.INSTANCE.generateKey());
    }

    @SideOnly(Side.CLIENT)
    private static NBTTagList writeQuestList(@NotNull Map<UUID, IQuest> quests) {
        NBTTagList questList = new NBTTagList();
        quests.forEach((questID, quest) -> {
            if (questID == null) {
                return;
            }

            NBTTagCompound entry = NBTConverter.UuidValueType.QUEST.writeId(questID);
            if (quest != null) {
                entry.setTag(TAG_QUEST, quest.writeToNBT(new NBTTagCompound()));
            }
            questList.appendTag(entry);
        });
        return questList;
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        boolean isOP = sender.mcServer.getConfigurationManager()
            .func_152596_g(sender.getGameProfile());

        if (!isOP) {
            sender.addChatComponentMessage(
                new ChatComponentText(EnumChatFormatting.RED + "You need to be OP to edit quests!"));
            return;
        }

        UUID senderID = QuestingAPI.getQuestingUUID(sender);
        int action = payload.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_ACTION) : -1;

        switch (action) {
            case ACTION_EDIT -> editQuests(payload.getTagList(TAG_QUEST_LIST, Constants.NBT.TAG_COMPOUND));
            case ACTION_DELETE -> deleteQuests(NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS));
            // TODO: Allow the editor to send a target player name/UUID
            case ACTION_COMPLETE -> completeQuests(
                NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS),
                payload.getBoolean(TAG_COMPLETE),
                senderID);
            case ACTION_CREATE -> createQuests(payload.getTagList(TAG_QUEST_LIST, Constants.NBT.TAG_COMPOUND));
            default -> BetterQuesting.logger
                .log(Level.ERROR, "Invalid quest edit action '{}'. Full payload:\n{}", action, payload);
        }
    }

    // Serverside only
    public static void editQuests(NBTTagList data) {
        List<UUID> questIDs = new ArrayList<>();
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            UUID questID = NBTConverter.UuidValueType.QUEST.readId(entry);
            IQuest quest = QuestDatabase.INSTANCE.get(questID);

            if (quest != null) {
                questIDs.add(questID);
                quest.readFromNBT(entry.getCompoundTag(TAG_QUEST));
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetQuestSync.sendSync(null, questIDs, true, false);
    }

    // Serverside only
    public static void deleteQuests(Collection<UUID> questIDs) {
        for (UUID uuid : questIDs) {
            QuestDatabase.INSTANCE.remove(uuid);
            QuestLineDatabase.INSTANCE.removeQuest(uuid);
        }

        SaveLoadHandler.INSTANCE.markDirty();

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        payload.setTag(TAG_QUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    // Serverside only
    public static void completeQuests(Collection<UUID> questIDs, boolean state, UUID playerID) {
        Map<UUID, IQuest> questMap = QuestDatabase.INSTANCE.filterKeys(questIDs);

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        if (server == null) {
            return;
        }

        EntityPlayerMP player = null;
        for (EntityPlayerMP playerMP : server.getConfigurationManager().playerEntityList) {
            if (playerMP.getGameProfile()
                .getId()
                .equals(playerID)) {
                player = playerMP;
            }
        }

        for (Map.Entry<UUID, IQuest> entry : questMap.entrySet()) {
            IQuest quest = entry.getValue();

            if (!state) {
                quest.resetUser(playerID, true);
                continue;
            }

            if (quest.isComplete(playerID)) {
                quest.setClaimed(playerID, 0);
                continue;
            }

            quest.setComplete(playerID, 0);

            EnumLogic logic = quest.getProperty(NativeProps.LOGIC_TASK);
            int tasksCount = quest.getTasks()
                .size();
            int completedTasksCount = 0;

            if (!logic.getResult(completedTasksCount, tasksCount)) {
                for (DBEntry<ITask> task : quest.getTasks()
                    .getEntries()) {
                    task.getValue()
                        .setComplete(playerID);
                    completedTasksCount++;

                    if (logic.getResult(completedTasksCount, tasksCount)) {
                        break; // Only complete enough quests to claim the reward
                    }
                }
            }

            if (player != null) {
                BetterQuesting.logger
                    .info("{} ({}) completed quest {}", player.getDisplayName(), playerID, entry.getKey());
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();

        if (player == null) return;
        NetQuestSync.sendSync(player, questIDs, false, true);
    }

    // Serverside only
    public static void createQuests(NBTTagList data) {
        List<UUID> questIDs = new ArrayList<>();
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound entry = data.getCompoundTagAt(i);
            UUID questID = NBTConverter.UuidValueType.QUEST.readId(entry);
            IQuest quest = QuestDatabase.INSTANCE.createNew(questID);

            questIDs.add(questID);
            if (entry.hasKey(TAG_QUEST, Constants.NBT.TAG_COMPOUND)) {
                quest.readFromNBT(entry.getCompoundTag(TAG_QUEST));
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetQuestSync.sendSync(null, questIDs, true, false);
    }

    // Imparts edit specific changes
    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound payload) {
        int action = payload.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_ACTION) : -1;

        if (action == ACTION_DELETE) {
            for (UUID uuid : NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS)) {
                QuestDatabase.INSTANCE.remove(uuid);
                QuestLineDatabase.INSTANCE.removeQuest(uuid);
            }

            MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
        }
    }
}
