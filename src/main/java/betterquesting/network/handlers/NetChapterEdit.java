package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetChapterEdit {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:chapter_edit");

    private static final int ACTION_EDIT = 0;
    private static final int ACTION_DELETE = 1;
    private static final int ACTION_REORDER = 2;
    private static final int ACTION_CREATE = 3;

    private static final String TAG_ACTION = "action";
    private static final String TAG_QUEST_LINE_IDS = "questLineIDs";
    private static final String TAG_QUEST_LINES = "data";
    private static final String TAG_QUEST_LINE = "config";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetChapterEdit::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetChapterEdit::onClient);
        }
    }

    // region request from client
    /** @deprecated use request methods instead. Kept for API compat */
    @Deprecated
    @SideOnly(Side.CLIENT)
    public static void sendEdit(NBTTagCompound payload) {
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestEdit(UUID chapterId, IQuestLine chapter) {
        requestEdit(chapterId, chapter.writeToNBT(new NBTTagCompound(), null));
    }

    @SideOnly(Side.CLIENT)
    public static void requestEdit(UUID chapterId, NBTTagCompound chapterNBT) {
        NBTTagList chapters = new NBTTagList();
        NBTTagCompound chapterEntry = new NBTTagCompound();
        NBTConverter.UuidValueType.QUEST_LINE.writeId(chapterId, chapterNBT);
        chapterEntry.setTag(TAG_QUEST_LINE, chapterNBT);
        chapters.appendTag(chapterEntry);

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_EDIT);
        payload.setTag(TAG_QUEST_LINES, chapters);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestDelete(Collection<UUID> chapterIds) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        payload.setTag(TAG_QUEST_LINE_IDS, NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIds));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestReorder(Collection<UUID> chapterIds) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_REORDER);
        payload.setTag(TAG_QUEST_LINE_IDS, NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIds));
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    public static void requestCreate() {
        NBTTagList chapters = new NBTTagList();
        chapters.appendTag(new NBTTagCompound());

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_CREATE);
        payload.setTag(TAG_QUEST_LINES, chapters);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }
    // endregion request

    // region handle from server
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

        int action = payload.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_ACTION) : -1;

        switch (action) {
            case ACTION_EDIT -> editChapters(payload.getTagList(TAG_QUEST_LINES, Constants.NBT.TAG_COMPOUND));
            case ACTION_DELETE -> deleteChapters(
                NBTConverter.UuidValueType.QUEST_LINE.readIds(payload, TAG_QUEST_LINE_IDS));
            case ACTION_REORDER -> reorderChapters(
                NBTConverter.UuidValueType.QUEST_LINE.readIds(payload, TAG_QUEST_LINE_IDS));
            case ACTION_CREATE -> createChapters(payload.getTagList(TAG_QUEST_LINES, Constants.NBT.TAG_COMPOUND));
            default -> BetterQuesting.logger
                .log(Level.ERROR, "Invalid chapter edit action '{}'. Full payload:\n{}", action, payload);
        }
    }

    private static void editChapters(NBTTagList chapters) {
        ArrayList<UUID> chapterIds = new ArrayList<>(chapters.tagCount());

        for (int i = 0; i < chapters.tagCount(); i++) {
            NBTTagCompound entry = chapters.getCompoundTagAt(i);
            UUID chapterID = NBTConverter.UuidValueType.QUEST_LINE.readId(entry);
            IQuestLine chapter = QuestLineDatabase.INSTANCE.get(chapterID);

            if (chapter != null) {
                chapter.readFromNBT(entry.getCompoundTag(TAG_QUEST_LINE), false);
                chapterIds.add(chapterID);
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetChapterSync.sendSync(null, chapterIds);
    }

    private static void deleteChapters(Collection<UUID> chapterIDs) {
        for (UUID id : chapterIDs) {
            QuestLineDatabase.INSTANCE.remove(id);
        }

        SaveLoadHandler.INSTANCE.markDirty();

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        payload.setTag(TAG_QUEST_LINE_IDS, NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIDs));
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    private static void reorderChapters(List<UUID> chapterIDs) {
        for (int n = 0; n < chapterIDs.size(); n++) {
            QuestLineDatabase.INSTANCE.setOrderIndex(chapterIDs.get(n), n);
        }

        SaveLoadHandler.INSTANCE.markDirty();

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_REORDER);
        payload.setTag(TAG_QUEST_LINE_IDS, NBTConverter.UuidValueType.QUEST_LINE.writeIds(chapterIDs));
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    private static void createChapters(NBTTagList chapters) {
        List<UUID> chapterIds = new ArrayList<>(chapters.tagCount());

        for (int i = 0; i < chapters.tagCount(); i++) {
            NBTTagCompound entry = chapters.getCompoundTagAt(i);
            UUID chapterID = NBTConverter.UuidValueType.QUEST_LINE.tryReadId(entry)
                .orElseGet(QuestLineDatabase.INSTANCE::generateKey);
            IQuestLine chapter = QuestLineDatabase.INSTANCE.createNew(chapterID);

            chapterIds.add(chapterID);
            if (entry.hasKey(TAG_QUEST_LINE, Constants.NBT.TAG_COMPOUND)) {
                chapter.readFromNBT(entry.getCompoundTag(TAG_QUEST_LINE), false);
            }
        }

        SaveLoadHandler.INSTANCE.markDirty();
        NetChapterSync.sendSync(null, chapterIds);
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        int action = message.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? message.getInteger(TAG_ACTION) : -1;

        switch (action) {
            case ACTION_DELETE -> {
                for (UUID id : NBTConverter.UuidValueType.QUEST_LINE.readIds(message, TAG_QUEST_LINE_IDS)) {
                    QuestLineDatabase.INSTANCE.remove(id);
                }

                MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
            }
            case ACTION_REORDER -> {
                List<UUID> chapterIDs = NBTConverter.UuidValueType.QUEST_LINE.readIds(message, TAG_QUEST_LINE_IDS);
                for (int n = 0; n < chapterIDs.size(); n++) {
                    QuestLineDatabase.INSTANCE.setOrderIndex(chapterIDs.get(n), n);
                }

                MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.CHAPTER));
            }
        }
    }
    // endregion handle
}
