package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.party.PartyManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Ignore the invite system here. We'll deal wih that elsewhere
public class NetPartySync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:party_sync");

    private static final String TAG_PARTY_ID = "partyID";
    private static final String TAG_PARTY = "config";
    private static final String TAG_PARTY_LIST = "data";
    private static final String TAG_MERGE = "merge";
    private static final String TAG_PARTY_IDS = "partyIDs";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetPartySync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetPartySync::onClient);
        }
    }

    /** Syncs party for online members only */
    public static void quickSync(int partyID) {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();

        IParty party = PartyManager.INSTANCE.getValue(partyID);
        if (party == null) return;

        HashSet<UUID> partyMemberIds = new HashSet<>(party.getMembers());
        ArrayList<EntityPlayerMP> onlineMembers = new ArrayList<>();

        for (EntityPlayerMP player : server.getConfigurationManager().playerEntityList) {
            UUID playerId = player.getGameProfile()
                .getId();
            if (partyMemberIds.contains(playerId)) {
                onlineMembers.add(player);
                if (onlineMembers.size() == partyMemberIds.size()) {
                    break;
                }
            }
        }

        sendSync(onlineMembers.toArray(new EntityPlayerMP[0]), new int[] { partyID });
    }

    public static void sendSync(@Nullable EntityPlayerMP[] players, @Nullable int[] partyIDs) {
        if (partyIDs != null && partyIDs.length == 0) return;
        if (players != null && players.length == 0) return;

        NBTTagList partyList = new NBTTagList();
        final List<DBEntry<IParty>> partySubset = partyIDs == null ? PartyManager.INSTANCE.getEntries()
            : PartyManager.INSTANCE.bulkLookup(partyIDs);

        for (DBEntry<IParty> party : partySubset) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(TAG_PARTY_ID, party.getID());
            entry.setTag(
                TAG_PARTY,
                party.getValue()
                    .writeToNBT(new NBTTagCompound()));
            partyList.appendTag(entry);
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag(TAG_PARTY_LIST, partyList);
        payload.setBoolean(TAG_MERGE, partyIDs != null);

        if (players == null) {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        } else {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), players);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void requestSync(@Nullable int[] partyIDs) {
        NBTTagCompound payload = new NBTTagCompound();
        if (partyIDs != null) payload.setIntArray(TAG_PARTY_IDS, partyIDs);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        int[] reqIDs = !payload.hasKey(TAG_PARTY_IDS) ? null : payload.getIntArray(TAG_PARTY_IDS);
        sendSync(new EntityPlayerMP[] { message.getSecond() }, reqIDs);
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound payload) {
        NBTTagList partyList = payload.getTagList(TAG_PARTY_LIST, Constants.NBT.TAG_COMPOUND);
        if (!payload.getBoolean(TAG_MERGE)) PartyManager.INSTANCE.reset();

        for (int i = 0; i < partyList.tagCount(); i++) {
            NBTTagCompound partyNBT = partyList.getCompoundTagAt(i);
            if (!partyNBT.hasKey(TAG_PARTY_ID, Constants.NBT.TAG_ANY_NUMERIC)) continue;

            int partyID = partyNBT.getInteger(TAG_PARTY_ID);
            IParty party = PartyManager.INSTANCE.getValue(partyID); // TODO: Send to client side database
            if (party == null) party = PartyManager.INSTANCE.createNew(partyID);

            party.readFromNBT(partyNBT.getCompoundTag(TAG_PARTY));
        }

        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.PARTY));
    }
}
