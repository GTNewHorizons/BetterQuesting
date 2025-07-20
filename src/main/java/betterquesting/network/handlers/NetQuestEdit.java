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

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestEdit::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetQuestEdit::onClient);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void sendEdit(NBTTagCompound payload) // TODO: Make these use proper methods for each action rather
                                                        // than directly assembling the payload
    {
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        EntityPlayerMP sender = message.getSecond();
        MinecraftServer server = sender.mcServer;
        if (server == null) return; // Here mostly just to keep intellisense happy

        boolean isOP = server.getConfigurationManager()
            .func_152596_g(sender.getGameProfile());

        if (!isOP) // OP pre-check
        {
            BetterQuesting.logger.log(
                Level.WARN,
                "Player " + sender.getCommandSenderName()
                    + " (UUID:"
                    + QuestingAPI.getQuestingUUID(sender)
                    + ") tried to edit quests without OP permissions!");
            sender.addChatComponentMessage(
                new ChatComponentText(EnumChatFormatting.RED + "You need to be OP to edit quests!"));
            return; // Player is not operator. Do nothing
        }

        NBTTagCompound tag = message.getFirst();
        UUID senderID = QuestingAPI.getQuestingUUID(sender);
        int action = !message.getFirst()
            .hasKey("action", 99) ? -1
                : message.getFirst()
                    .getInteger("action");

        switch (action) {
            case 0: {
                editQuests(tag.getTagList("data", 10));
                break;
            }
            case 1: {
                deleteQuests(NBTConverter.UuidValueType.QUEST.readIds(tag, "questIDs"));
                break;
            }
            case 2: {
                // TODO: Allow the editor to send a target player name/UUID
                setQuestStates(
                    NBTConverter.UuidValueType.QUEST.readIds(tag, "questIDs"),
                    tag.getBoolean("state"),
                    senderID);
                break;
            }
            case 3: {
                createQuests(tag.getTagList("data", 10));
                break;
            }
            default: {
                BetterQuesting.logger.log(
                    Level.ERROR,
                    "Invalid quest edit action '" + action
                        + "'. Full payload:\n"
                        + message.getFirst()
                            .toString());
            }
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
        payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(questIDs));
        payload.setInteger("action", 1);
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
        for (Object o : server.getConfigurationManager().playerEntityList) {
            if (((EntityPlayerMP) o).getGameProfile()
                .getId()
                .equals(targetID)) {
                player = (EntityPlayerMP) o;
            }
        }

        for (Map.Entry<UUID, IQuest> entry : questMap.entrySet()) {
            if (!state) {
                entry.getValue()
                    .resetUser(targetID, true);
                continue;
            }

            if (entry.getValue()
                .isComplete(targetID)) {
                entry.getValue()
                    .setClaimed(targetID, 0);
            } else {
                entry.getValue()
                    .setComplete(targetID, 0);

                int done = 0;

                if (!entry.getValue()
                    .getProperty(NativeProps.LOGIC_TASK)
                    .getResult(
                        done,
                        entry.getValue()
                            .getTasks()
                            .size())) // Preliminary check
                {
                    for (DBEntry<ITask> task : entry.getValue()
                        .getTasks()
                        .getEntries()) {
                        task.getValue()
                            .setComplete(targetID);
                        done++;

                        if (entry.getValue()
                            .getProperty(NativeProps.LOGIC_TASK)
                            .getResult(
                                done,
                                entry.getValue()
                                    .getTasks()
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

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) // Imparts edit specific changes
    {
        int action = !message.hasKey("action", 99) ? -1 : message.getInteger("action");

        if (action == 1) // Change to a switch statement when more actions are required
        {
            for (UUID uuid : NBTConverter.UuidValueType.QUEST.readIds(message, "questIDs")) {
                QuestDatabase.INSTANCE.remove(uuid);
                QuestLineDatabase.INSTANCE.removeQuest(uuid);
            }

            MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
        }
    }

    public static void log(String msg) {
        // I don't like this but it's all I can think of
        BetterQuesting.logger.log(Level.INFO, msg);
    }

}
