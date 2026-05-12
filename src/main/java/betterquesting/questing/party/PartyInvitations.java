package betterquesting.questing.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.INBTPartial;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetInviteSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.questing.QuestDatabase;

// NOTE: This is in a separate class because it could later be moved to a dedicated inbox system
public class PartyInvitations implements INBTPartial<NBTTagList, UUID> {

    public static final PartyInvitations INSTANCE = new PartyInvitations();

    private static final String TAG_PLAYER_ID = "uuid";
    private static final String TAG_PLAYER_INVITES = "invites";
    private static final String TAG_INVITE_PARTY_ID = "partyID";
    private static final String TAG_INVITE_EXPIRY = "expiry";

    private final HashMap<UUID, HashMap<Integer, Long>> invites = new HashMap<>();

    public synchronized void postInvite(@Nonnull UUID playerId, int partyId, long expiryTime) {
        if (expiryTime <= 0) {
            BetterQuesting.logger.error("Received an invite that has already expired!");
            return; // Can't expire before being issued
        }

        IParty party = PartyManager.INSTANCE.getValue(partyId);
        if (party == null || party.getStatus(playerId) != null) {
            return; // Party doesn't exist or user has already // joined
        }

        HashMap<Integer, Long> playerInvites = invites.computeIfAbsent(playerId, (key) -> new HashMap<>());
        playerInvites.put(partyId, System.currentTimeMillis() + expiryTime);
    }

    public synchronized boolean acceptInvite(@Nonnull UUID playerId, int partyId) {
        HashMap<Integer, Long> playerInvites = invites.get(playerId);
        if (playerInvites == null || playerInvites.isEmpty()) {
            return false;
        }

        long expiryTime = playerInvites.get(partyId);
        IParty party = PartyManager.INSTANCE.getValue(partyId);
        boolean valid = expiryTime > System.currentTimeMillis();

        if (party != null && valid) {
            // Resetting user before joining party
            for (IQuest quest : QuestDatabase.INSTANCE.values()) {
                quest.resetUser(playerId, true);
            }
            EntityPlayerMP player = QuestingAPI.getPlayer(playerId);
            if (player != null) {
                NetQuestSync.sendSync(player, null, false, true, true);
            }

            party.setStatus(playerId, EnumPartyStatus.MEMBER);
            PartyManager.SyncPartyQuests(party, true);
        }

        // We still remove it regardless of validity
        playerInvites.remove(partyId);
        if (playerInvites.isEmpty()) {
            invites.remove(playerId);
        }

        return valid;
    }

    public synchronized void revokeInvites(@Nonnull UUID playerId, int... partyIds) {
        HashMap<Integer, Long> playerInvites = invites.get(playerId);
        if (playerInvites == null || playerInvites.isEmpty()) return;
        for (int partyId : partyIds) playerInvites.remove(partyId);
        if (playerInvites.isEmpty()) invites.remove(playerId);
    }

    public synchronized List<Entry<Integer, Long>> getPartyInvites(@Nonnull UUID playerId) {
        HashMap<Integer, Long> playerInvites = invites.get(playerId);
        if (playerInvites == null || playerInvites.isEmpty()) return Collections.emptyList();

        List<Entry<Integer, Long>> sortedPlayerInvites = new ArrayList<>(playerInvites.entrySet());
        sortedPlayerInvites.sort(Comparator.comparing(Entry::getValue)); // Sort by expiry time
        return sortedPlayerInvites;
    }

    // Primarily used when deleting parties to ensure that pending invites don't link to newly created parties under the
    // same ID
    public synchronized void purgeInvites(int partyId) {
        invites.values()
            .forEach((playerInvites) -> playerInvites.remove(partyId));
    }

    public synchronized void cleanExpired() {
        MinecraftServer server = MinecraftServer.getServer();
        Iterator<Entry<UUID, HashMap<Integer, Long>>> invitesIterator = invites.entrySet()
            .iterator();

        while (invitesIterator.hasNext()) {
            Entry<UUID, HashMap<Integer, Long>> invitesEntry = invitesIterator.next();
            UUID playerId = invitesEntry.getKey();
            HashMap<Integer, Long> playerInvites = invitesEntry.getValue();

            ArrayList<Integer> revokedParties = new ArrayList<>();
            Iterator<Entry<Integer, Long>> playerInvitesIterator = playerInvites.entrySet()
                .iterator();

            while (playerInvitesIterator.hasNext()) {
                Entry<Integer, Long> playerInvitesEntry = playerInvitesIterator.next();
                int partyId = playerInvitesEntry.getKey();
                long expiryTime = playerInvitesEntry.getValue();

                if (expiryTime < System.currentTimeMillis()) {
                    revokedParties.add(partyId);
                    playerInvitesIterator.remove();
                }
            }

            EntityPlayerMP player = null;
            for (EntityPlayerMP playerMP : server.getConfigurationManager().playerEntityList) {
                if (playerMP.getGameProfile()
                    .getId()
                    .equals(playerId)) {
                    player = playerMP;
                    break;
                }
            }

            if (player != null) {
                int[] revokedPartiesArr = new int[revokedParties.size()];
                for (int i = 0; i < revokedParties.size(); i++) revokedPartiesArr[i] = revokedParties.get(i);
                // Normally I avoid including networking calls into the database...
                NetInviteSync.sendRevoked(player, revokedPartiesArr);
            }
            if (playerInvites.isEmpty()) invitesIterator.remove();
        }
    }

    public synchronized void reset() {
        invites.clear();
    }

    // Don't bother saving this to disk. We do need to send packets though
    @Override
    public synchronized NBTTagList writeToNBT(NBTTagList nbt, @Nullable List<UUID> playerIds) {
        Collection<UUID> subset = playerIds != null ? playerIds : invites.keySet();
        for (UUID playerId : subset) {
            NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setString(TAG_PLAYER_ID, playerId.toString());

            HashMap<Integer, Long> playerInvites = invites.get(playerId);
            if (playerInvites == null) playerInvites = new HashMap<>();

            NBTTagList invitesTagList = new NBTTagList();

            for (Entry<Integer, Long> inviteEntry : playerInvites.entrySet()) {
                NBTTagCompound inviteTag = new NBTTagCompound();
                inviteTag.setInteger(TAG_INVITE_PARTY_ID, inviteEntry.getKey());
                inviteTag.setLong(TAG_INVITE_EXPIRY, inviteEntry.getValue());
                invitesTagList.appendTag(inviteTag);
            }

            playerTag.setTag(TAG_PLAYER_INVITES, invitesTagList);
            nbt.appendTag(playerTag);
        }
        return nbt;
    }

    @Override
    public synchronized void readFromNBT(NBTTagList nbt, boolean merge) {
        if (!merge) invites.clear();
        for (int i = 0; i < nbt.tagCount(); i++) {
            NBTTagCompound playerTag = nbt.getCompoundTagAt(i);

            final UUID playerId;
            try {
                playerId = UUID.fromString(playerTag.getString(TAG_PLAYER_ID));
            } catch (Exception e) {
                continue;
            }

            NBTTagList invitesTagList = playerTag.getTagList(TAG_PLAYER_INVITES, Constants.NBT.TAG_COMPOUND);
            HashMap<Integer, Long> playerInvites = invites.compute(playerId, (key, old) -> new HashMap<>());

            for (int n = 0; n < invitesTagList.tagCount(); n++) {
                NBTTagCompound inviteTag = invitesTagList.getCompoundTagAt(n);
                int partyID = inviteTag.hasKey(TAG_INVITE_PARTY_ID, Constants.NBT.TAG_ANY_NUMERIC)
                    ? inviteTag.getInteger(TAG_INVITE_PARTY_ID)
                    : -1;
                long timestamp = inviteTag.hasKey(TAG_INVITE_EXPIRY, Constants.NBT.TAG_ANY_NUMERIC)
                    ? inviteTag.getLong(TAG_INVITE_EXPIRY)
                    : -1;
                if (partyID < 0) continue;
                playerInvites.put(partyID, timestamp);
            }
        }
    }
}
