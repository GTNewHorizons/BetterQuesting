package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.Collection;
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

import betterquesting.api.api.QuestingAPI;
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
    private static final int ACTION_SET_STATE = 2; // or it can be called SET_COMPLETED (based on state)
    private static final int ACTION_CREATE = 3;

    private static final String TAG_ACTION = "action";
    private static final String TAG_DATA = "data";
    private static final String TAG_QUEST_IDS = "questIDs";
    private static final String TAG_STATE = "state";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestEdit::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetQuestEdit::onClient);
        }
    }

    // TODO: Make these use proper methods for each action rather than directly assembling the payload
    @SideOnly(Side.CLIENT)
    public static void sendEdit(NBTTagCompound payload) {
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
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
            case ACTION_EDIT -> editQuests(payload.getTagList(TAG_DATA, Constants.NBT.TAG_COMPOUND));
            case ACTION_DELETE -> deleteQuests(NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS));
            // TODO: Allow the editor to send a target player name/UUID
            case ACTION_SET_STATE -> setQuestStates(
                NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS),
                payload.getBoolean(TAG_STATE),
                senderID);
            case ACTION_CREATE -> createQuests(payload.getTagList(TAG_DATA, Constants.NBT.TAG_COMPOUND));
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
            questIDs.add(questID);

            IQuest quest = QuestDatabase.INSTANCE.get(questID);
            if (quest != null) {
                quest.readFromNBT(entry.getCompoundTag("config"));
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
        payload.setTag(TAG_QUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    // Serverside only
    public static void setQuestStates(Collection<UUID> questIDs, boolean state, UUID targetID) {
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
                .equals(targetID)) {
                player = playerMP;
            }
        }

        for (Map.Entry<UUID, IQuest> entry : questMap.entrySet()) {
            IQuest quest = entry.getValue();

            if (!state) {
                quest.resetUser(targetID, true);
                continue;
            }

            if (quest.isComplete(targetID)) {
                quest.setClaimed(targetID, 0);
            } else {
                quest.setComplete(targetID, 0);

                int done = 0;

                if (!quest.getProperty(NativeProps.LOGIC_TASK)
                    .getResult(
                        done,
                        quest.getTasks()
                            .size())) {
                    for (DBEntry<ITask> task : quest.getTasks()
                        .getEntries()) {
                        task.getValue()
                            .setComplete(targetID);
                        done++;

                        if (quest.getProperty(NativeProps.LOGIC_TASK)
                            .getResult(
                                done,
                                quest.getTasks()
                                    .size())) {
                            break; // Only complete enough quests to claim the reward
                        }
                    }
                }
            }

            if (player != null) {
                BetterQuesting.logger
                    .info("{} ({}) completed quest {}", player.getDisplayName(), targetID, entry.getKey());
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

            UUID questID = NBTConverter.UuidValueType.QUEST.tryReadId(entry)
                .orElseGet(QuestDatabase.INSTANCE::generateKey);
            questIDs.add(questID);

            IQuest quest = QuestDatabase.INSTANCE.get(questID);
            if (quest == null) {
                quest = QuestDatabase.INSTANCE.createNew(questID);
            }

            if (entry.hasKey("config", Constants.NBT.TAG_COMPOUND)) {
                quest.readFromNBT(entry.getCompoundTag("config"));
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
