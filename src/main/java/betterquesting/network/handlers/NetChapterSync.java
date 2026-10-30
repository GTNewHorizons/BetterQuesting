package betterquesting.network.handlers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import com.google.common.collect.Lists;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetChapterSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:chapter_sync");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetChapterSync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetChapterSync::onClient);
        }
    }

    public static void sendSync(@Nullable EntityPlayerMP player, @Nullable Collection<UUID> chapterIDs) {
        if (chapterIDs != null && chapterIDs.isEmpty()) {
            return;
        }

        BQThreadedIO.INSTANCE.enqueue(() -> {
            NBTTagCompound payload = buildPayload(chapterIDs);

            if (player == null) {
                PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
            } else {
                PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
            }
        });
    }

    static NBTTagCompound buildPayload(@Nullable Collection<UUID> chapterIDs) {
        NBTTagList data = new NBTTagList();
        final Map<UUID, IQuestLine> chapterSubset = chapterIDs == null ? QuestLineDatabase.INSTANCE
            : QuestLineDatabase.INSTANCE.filterKeys(chapterIDs);

        for (Map.Entry<UUID, IQuestLine> chapter : chapterSubset.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            NBTConverter.UuidValueType.QUEST_LINE.writeId(chapter.getKey(), entry);
            // entry.setInteger("order", QuestLineDatabase.INSTANCE.getOrderIndex(chapter.getKey()));
            entry.setTag(
                "config",
                chapter.getValue()
                    .writeToNBT(new NBTTagCompound(), null));
            data.appendTag(entry);
        }

        List<Map.Entry<UUID, IQuestLine>> allSort = QuestLineDatabase.INSTANCE.getOrderedEntries();
        List<UUID> aryOrder = Lists.transform(allSort, Map.Entry::getKey);

        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("merge", chapterIDs != null);
        payload.setTag("data", data);
        payload.setTag("order", NBTConverter.UuidValueType.QUEST_LINE.writeIds(aryOrder));
        return payload;
    }

    @SideOnly(Side.CLIENT)
    public static void requestSync(@Nullable Collection<UUID> chapterIDs) {
        NBTTagCompound payload = new NBTTagCompound();
        if (chapterIDs != null) {
            payload.setTag("requestIDs", NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIDs));
        }
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        List<UUID> reqIDs = !payload.hasKey("requestIDs") ? null
            : NBTConverter.UuidValueType.QUEST_LINE.readIds(payload, "requestIDs");
        sendSync(message.getSecond(), reqIDs);
    }

    @SideOnly(Side.CLIENT)
    static void onClient(NBTTagCompound message) {
        NBTTagList data = message.getTagList("data", 10);
        if (!message.getBoolean("merge")) {
            QuestLineDatabase.INSTANCE.clear();
        }

        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound tag = data.getCompoundTagAt(i);
            Optional<UUID> chapterIDOptional = NBTConverter.UuidValueType.QUEST_LINE.tryReadId(tag);
            if (!chapterIDOptional.isPresent()) {
                continue;
            }
            UUID chapterID = chapterIDOptional.get();
            // int order = tag.getInteger("order");

            IQuestLine chapter = QuestLineDatabase.INSTANCE.get(chapterID); // TODO: Send to client side database
            if (chapter == null) {
                chapter = QuestLineDatabase.INSTANCE.createNew(chapterID);
            }

            // QuestLineDatabase.INSTANCE.setOrderIndex(chapterID, order);
            chapter.readFromNBT(tag.getCompoundTag("config"), false); // Merging isn't really a problem unless a chapter
                                                                      // is excessively sized. Can be improved later if
                                                                      // necessary
        }

        List<UUID> aryOrder = NBTConverter.UuidValueType.QUEST_LINE.readIds(message, "order");
        for (int i = 0; i < aryOrder.size(); i++) {
            QuestLineDatabase.INSTANCE.setOrderIndex(aryOrder.get(i), i);
        }

        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
    }
}
