package betterquesting.network.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
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

    private static final String TAG_CACHED_DATABASES = "cachedDatabases";
    private static final int MAX_CACHED_DATABASES = 16;

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
        } else {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
        }
    }

    public static void sendSync(@Nonnull EntityPlayerMP player) {
        sendSync(player, null);
    }

    /**
     * @param cachedDatabases hashes of the quest databases the client has cached, or null if it does not support
     *                        {@link NetDatabaseCache}
     */
    public static void sendSync(@Nonnull EntityPlayerMP player, @Nullable Collection<String> cachedDatabases) {
        boolean nameChanged = NameCache.INSTANCE.updateName(player);
        UUID playerID = QuestingAPI.getQuestingUUID(player);

        NetSettingSync.sendSync(player);
        if (cachedDatabases == null) {
            NetQuestSync.sendSync(player, null, true, true);
            NetChapterSync.sendSync(player, null);
        } else {
            NetDatabaseCache.sendSync(player, cachedDatabases);
        }
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

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        List<String> cachedDatabases = null;
        if (payload.hasKey(TAG_CACHED_DATABASES, Constants.NBT.TAG_LIST)) {
            NBTTagList list = payload.getTagList(TAG_CACHED_DATABASES, Constants.NBT.TAG_STRING);
            cachedDatabases = new ArrayList<>();
            for (int i = 0; i < Math.min(list.tagCount(), MAX_CACHED_DATABASES); i++) {
                cachedDatabases.add(list.getStringTagAt(i));
            }
        }
        sendSync(message.getSecond(), cachedDatabases);
    }

    // Asks the server for a full sync, telling it which quest databases we have cached
    @SideOnly(Side.CLIENT)
    static void requestSync(boolean useCache) {
        NBTTagCompound payload = new NBTTagCompound();
        if (BQ_Settings.loginDatabaseCache) {
            NBTTagList list = new NBTTagList();
            if (useCache) {
                for (String hash : QuestDatabaseCache.listHashes()) {
                    list.appendTag(new NBTTagString(hash));
                }
            }
            payload.setTag(TAG_CACHED_DATABASES, list);
        }
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
            requestSync(true);
        }
    }
}
