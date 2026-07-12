package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.NameCache;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetNameSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:name_sync");

    private static final String TAG_NAME_CACHE = "data";
    private static final String TAG_NAMES = "names";
    private static final String TAG_UUIDS = "uuids";
    private static final String TAG_MERGE = "merge";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetNameSync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetNameSync::onClient);
        }
    }

    // NOTE: You can make an empty request if you want EVERYTHING (but I would not recommend it on large servers)
    @SideOnly(Side.CLIENT)
    public static void sendRequest(@Nullable UUID[] uuids, @Nullable String[] names) {
        NBTTagCompound payload = new NBTTagCompound();
        if (uuids != null) {
            NBTTagList uuidTagList = new NBTTagList();
            for (UUID id : uuids) {
                if (id == null) continue;
                uuidTagList.appendTag(new NBTTagString(id.toString()));
            }
            payload.setTag(TAG_UUIDS, uuidTagList);
        }
        if (names != null) {
            NBTTagList nameTagList = new NBTTagList();
            for (String name : names) {
                if (StringUtils.isNullOrEmpty(name)) continue;
                nameTagList.appendTag(new NBTTagString(name));
            }
            payload.setTag(TAG_NAMES, nameTagList);
        }
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    /** Syncs party player names for a specific player (if passed) or for all party members */
    public static void quickSync(@Nullable EntityPlayerMP player, int partyID) {
        IParty party = PartyManager.INSTANCE.getValue(partyID);
        if (party == null) return;

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(TAG_NAME_CACHE, NameCache.INSTANCE.writeToNBT(new NBTTagList(), party.getMembers()));
        payload.setBoolean(TAG_MERGE, true);

        if (player != null) {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        ArrayList<EntityPlayerMP> onlinePlayerList = new ArrayList<>();

        for (UUID playerID : party.getMembers()) {
            for (EntityPlayerMP playerMP : server.getConfigurationManager().playerEntityList) {
                if (QuestingAPI.getQuestingUUID(playerMP)
                    .equals(playerID)) {
                    onlinePlayerList.add(playerMP);
                    break;
                }
            }
        }

        PacketSender.INSTANCE
            .sendToPlayers(new QuestingPacket(ID_NAME, payload), onlinePlayerList.toArray(new EntityPlayerMP[0]));
    }

    /** Syncs passed names for passed players (if not null) or for all online players */
    public static void sendNames(@Nullable EntityPlayerMP[] players, @Nullable UUID[] uuids, @Nullable String[] names) {
        List<UUID> idList = (uuids == null && names == null) ? null : new ArrayList<>();
        if (uuids != null) idList.addAll(Arrays.asList(uuids));
        if (names != null) {
            for (String name : names) {
                UUID id = NameCache.INSTANCE.getUUID(name);
                if (id != null) idList.add(id);
            }
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(TAG_NAME_CACHE, NameCache.INSTANCE.writeToNBT(new NBTTagList(), idList));
        payload.setBoolean(TAG_MERGE, idList != null);

        if (players == null) {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        } else {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), players);
        }
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        UUID[] uuids = null;
        String[] names = null;

        if (payload.hasKey(TAG_UUIDS, Constants.NBT.TAG_LIST)) {
            NBTTagList uuidTagList = payload.getTagList(TAG_UUIDS, Constants.NBT.TAG_STRING);
            uuids = new UUID[uuidTagList.tagCount()];
            for (int i = 0; i < uuids.length; i++) {
                try {
                    uuids[i] = UUID.fromString(uuidTagList.getStringTagAt(i));
                } catch (Exception ignored) {}
            }
        }
        if (payload.hasKey(TAG_NAMES, Constants.NBT.TAG_LIST)) {
            NBTTagList nameTagList = payload.getTagList(TAG_NAMES, Constants.NBT.TAG_STRING);
            names = new String[nameTagList.tagCount()];
            for (int i = 0; i < names.length; i++) {
                names[i] = nameTagList.getStringTagAt(i);
            }
        }
        sendNames(new EntityPlayerMP[] { message.getSecond() }, uuids, names);
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound payload) {
        NameCache.INSTANCE
            .readFromNBT(payload.getTagList(TAG_NAME_CACHE, Constants.NBT.TAG_COMPOUND), payload.getBoolean(TAG_MERGE));
        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.NAMES));
    }
}
