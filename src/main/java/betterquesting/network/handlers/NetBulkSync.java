package betterquesting.network.handlers;

import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.Tuple2;
import betterquesting.client.QuestDatabaseCache;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.party.PartyInvitations;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.NameCache;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// Clears local data and negotiates a full resync with the server
public class NetBulkSync {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:main_sync");

    private static final String TAG_DEFINITION_HASH = "definitionHash";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetBulkSync::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetBulkSync::onClient);
        }
    }

    public static void sendReset(@Nullable EntityPlayerMP player, boolean reset, boolean respond) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setBoolean("reset", reset);
        payload.setBoolean("respond", respond);

        // Don't use this on a large server unless absolutely necessary!
        if (player == null) {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        } else if (respond && !player.playerNetServerHandler.func_147362_b()
            .isLocalChannel()) {
                NetDatabaseCache.ServerSnapshot snapshot = NetDatabaseCache.getServerSnapshot();
                PacketSender.INSTANCE.sendPreparedToPlayers(() -> {
                    String hash = snapshot.hash();
                    if (hash != null) payload.setString(TAG_DEFINITION_HASH, hash);
                    return new QuestingPacket(ID_NAME, payload);
                }, player);
            } else {
                PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
            }
    }

    public static void sendSync(@Nonnull EntityPlayerMP player) {
        sendSync(player, null);
    }

    private static void sendSync(@Nonnull EntityPlayerMP player, @Nullable String cachedHash) {
        boolean nameChanged = NameCache.INSTANCE.updateName(player);
        UUID playerID = QuestingAPI.getQuestingUUID(player);

        NetSettingSync.sendSync(player);
        sendQuestData(player, cachedHash);
        NetLifeSync.sendSync(new EntityPlayerMP[] { player }, new UUID[] { playerID });
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(playerID);
        List<Entry<Integer, Long>> invites = PartyInvitations.INSTANCE.getPartyInvites(playerID);
        int partyCount = invites.size() + (party == null ? 0 : 1);
        if (partyCount > 0) {
            int[] pids = new int[partyCount];
            for (int i = 0; i < invites.size(); i++) {
                pids[i] = invites.get(i)
                    .getKey();
            }
            if (party != null) pids[partyCount - 1] = party.getID();
            NetPartySync.sendSync(new EntityPlayerMP[] { player }, pids);
        }
        if (party != null) {
            NetNameSync.quickSync(nameChanged ? null : player, party.getID());
        } else {
            NetNameSync.sendNames(new EntityPlayerMP[] { player }, new UUID[] { playerID }, null);
        }
        NetInviteSync.sendSync(player);
        NetCacheSync.sendSync(player);
    }

    private static void sendQuestData(EntityPlayerMP player, @Nullable String cachedHash) {
        if (cachedHash == null) {
            NetQuestSync.sendSync(player, null, true, true);
            NetChapterSync.sendSync(player, null);
            return;
        }

        NetDatabaseCache.ServerSnapshot snapshot = NetDatabaseCache.getServerSnapshot();
        PacketSender.INSTANCE.sendPreparedToPlayers(() -> snapshot.createSyncPacket(cachedHash), player);
        NetQuestSync.sendSync(player, null, false, true, true);
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        String cachedHash = payload.hasKey(TAG_DEFINITION_HASH, Constants.NBT.TAG_STRING)
            ? payload.getString(TAG_DEFINITION_HASH)
            : null;
        sendSync(message.getSecond(), cachedHash);
    }

    @SideOnly(Side.CLIENT)
    static void requestSync(@Nullable String cachedHash) {
        NBTTagCompound payload = new NBTTagCompound();
        if (cachedHash != null) payload.setString(TAG_DEFINITION_HASH, cachedHash);
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        // DON'T do this on LAN hosts
        if (message.getBoolean("reset") && !Minecraft.getMinecraft()
            .isIntegratedServerRunning()) {
            SaveLoadHandler.INSTANCE.unloadDatabases();
        }

        // Client doesn't really have to honor this, but it would mess with things otherwise
        if (message.getBoolean("respond")) {
            String cachedHash = null;
            if (BQ_Settings.loginDatabaseCache && !Minecraft.getMinecraft()
                .isIntegratedServerRunning() && message.hasKey(TAG_DEFINITION_HASH, Constants.NBT.TAG_STRING)) {
                String offeredHash = message.getString(TAG_DEFINITION_HASH);
                cachedHash = QuestDatabaseCache.contains(offeredHash) ? offeredHash : "";
            }
            requestSync(cachedHash);
        }
    }
}
