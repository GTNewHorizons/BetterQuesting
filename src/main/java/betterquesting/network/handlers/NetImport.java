package betterquesting.network.handlers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Level;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.client.importers.ImportedQuestLines;
import betterquesting.client.importers.ImportedQuests;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;

public class NetImport {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:import");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetImport::onServer);
    }

    public static void sendImport(@Nonnull IQuestDatabase questDB, @Nonnull IQuestLineDatabase chapterDB) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("quests", questDB.writeToNBT(new NBTTagList(), null));
        payload.setTag("chapters", chapterDB.writeToNBT(new NBTTagList(), null));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        EntityPlayerMP sender = message.getSecond();
        if (sender.mcServer == null) {
            return;
        }

        boolean isOP = sender.mcServer.getConfigurationManager()
            .func_152596_g(sender.getGameProfile());

        if (!isOP) {
            BetterQuesting.logger.log(
                Level.WARN,
                "Player " + sender.getCommandSenderName()
                    + " (UUID:"
                    + QuestingAPI.getQuestingUUID(sender)
                    + ") tried to import quests without OP permissions!");
            sender.addChatComponentMessage(
                new ChatComponentText(EnumChatFormatting.RED + "You need to be OP to edit quests!"));
            return; // Player is not operator. Do nothing
        }

        ImportedQuests impQuestDB = new ImportedQuests();
        IQuestLineDatabase impQuestLineDB = new ImportedQuestLines();

        impQuestDB.readFromNBT(
            message.getFirst()
                .getTagList("quests", 10),
            false);
        impQuestLineDB.readFromNBT(
            message.getFirst()
                .getTagList("chapters", 10),
            false);

        BetterQuesting.logger.log(
            Level.INFO,
            "Importing " + impQuestDB.size()
                + " quest(s) and "
                + impQuestLineDB.size()
                + " quest line(s) from "
                + sender.getGameProfile()
                    .getName());

        BiMap<UUID, UUID> remapped = getRemappedIDs(impQuestDB.keySet());
        for (Map.Entry<UUID, IQuest> entry : impQuestDB.entrySet()) {
            Set<UUID> newRequirements = entry.getValue()
                .getRequirements()
                .stream()
                .map(req -> remapped.getOrDefault(req, req))
                .collect(Collectors.toCollection(HashSet::new));
            entry.getValue()
                .setRequirements(newRequirements);

            QuestDatabase.INSTANCE.put(remapped.get(entry.getKey()), entry.getValue());
        }

        for (IQuestLine questLine : impQuestLineDB.values()) {
            Set<Map.Entry<UUID, IQuestLineEntry>> pendingQLE = new HashSet<>(questLine.entrySet());
            questLine.clear();

            for (Map.Entry<UUID, IQuestLineEntry> qle : pendingQLE) {
                if (!remapped.containsKey(qle.getKey())) {
                    BetterQuesting.logger.error(
                        "Failed to import quest into quest line. Unable to remap ID "
                            + UuidConverter.encodeUuid(qle.getKey()));
                    continue;
                }

                questLine.put(remapped.get(qle.getKey()), qle.getValue());
            }

            QuestLineDatabase.INSTANCE.put(QuestLineDatabase.INSTANCE.generateKey(), questLine);
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
    }

    /**
     * Takes a list of imported IDs and returns a remapping to unused IDs
     */
    private static BiMap<UUID, UUID> getRemappedIDs(Set<UUID> ids) {
        Set<UUID> nextIDs = getNextIDs(ids.size());
        BiMap<UUID, UUID> remapped = HashBiMap.create(ids.size());

        Iterator<UUID> nextIDIterator = nextIDs.iterator();
        for (UUID id : ids) {
            remapped.put(id, nextIDIterator.next());
        }

        return remapped;
    }

    private static Set<UUID> getNextIDs(int num) {
        Set<UUID> nextIds = new HashSet<>();
        while (nextIds.size() < num) {
            // In the extremely unlikely event of a collision,
            // we'll handle it automatically due to nextIds being a Set
            nextIds.add(QuestDatabase.INSTANCE.generateKey());
        }
        return nextIds;
    }
}
