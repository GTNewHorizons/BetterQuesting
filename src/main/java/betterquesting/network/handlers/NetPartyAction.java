package betterquesting.network.handlers;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumPartyStatus;
import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.party.IParty;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.party.PartyInvitations;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.NameCache;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetPartyAction {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:party_action");

    private static final int ACTION_CREATE = 0;
    private static final int ACTION_DELETE = 1;
    private static final int ACTION_EDIT = 2;
    private static final int ACTION_INVITE = 3;
    private static final int ACTION_ACCEPT_INVITE = 4;
    private static final int ACTION_KICK = 5;

    private static final String TAG_ACTION = "action";
    private static final String TAG_PARTY_ID = "partyID";
    private static final String TAG_PARTY_NAME = "name";
    private static final String TAG_PARTY = "data";
    private static final String TAG_INVITE_USERNAME = "username";
    private static final String TAG_INVITE_EXPIRY_TIME = "expiry";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetPartyAction::onServer);

        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetPartyAction::onClient);
        }
    }

    @SideOnly(Side.CLIENT)
    public static void sendAction(NBTTagCompound payload) {
        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        int action = payload.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_ACTION) : -1;
        int partyID = payload.hasKey(TAG_PARTY_ID, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_PARTY_ID)
            : -1;
        IParty party = PartyManager.INSTANCE.getValue(partyID);
        int senderPermission = party == null ? 0 : getPermission(QuestingAPI.getQuestingUUID(sender), party);

        switch (action) {
            case ACTION_CREATE -> createParty(sender, payload.getString(TAG_PARTY_NAME));
            case ACTION_DELETE -> {
                if (senderPermission >= 3) {
                    deleteParty(partyID);
                }
            }
            case ACTION_EDIT -> {
                if (senderPermission >= 2) {
                    editParty(partyID, party, payload.getCompoundTag(TAG_PARTY));
                }
            }
            case ACTION_INVITE -> {
                if (senderPermission >= 2) {
                    inviteUser(
                        partyID,
                        payload.getString(TAG_INVITE_USERNAME),
                        payload.getLong(TAG_INVITE_EXPIRY_TIME));
                }
            }
            case ACTION_ACCEPT_INVITE -> acceptInvite(partyID, sender); // Probably the only thing an OP can't force
            case ACTION_KICK -> kickUser(
                partyID,
                sender,
                party,
                payload.getString(TAG_INVITE_USERNAME),
                senderPermission);
            default -> BetterQuesting.logger
                .log(Level.ERROR, "Invalid party action '{}'. Full payload:\n{}", action, payload);
        }
    }

    private static void createParty(EntityPlayerMP sender, String name) {
        UUID playerID = QuestingAPI.getQuestingUUID(sender);
        if (PartyManager.INSTANCE.getParty(playerID) != null) return;

        int partyID = PartyManager.INSTANCE.nextID();
        IParty party = PartyManager.INSTANCE.createNew(partyID);
        party.getProperties()
            .setProperty(NativeProps.NAME, name);
        party.setStatus(playerID, EnumPartyStatus.OWNER);
        NetPartySync.sendSync(new EntityPlayerMP[] { sender }, new int[] { partyID });
    }

    private static void deleteParty(int partyID) {
        PartyManager.INSTANCE.removeID(partyID);
        PartyInvitations.INSTANCE.purgeInvites(partyID);

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, ACTION_DELETE);
        payload.setInteger(TAG_PARTY_ID, partyID);
        // Invites need to be purged from everyone
        PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
    }

    private static void editParty(int partyID, IParty party, NBTTagCompound partyNBT) {
        party.readProperties(partyNBT);
        NetPartySync.quickSync(partyID);
    }

    private static void inviteUser(int partyID, String username, long expiry) {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        EntityPlayerMP player = server.getConfigurationManager()
            .func_152612_a(username);

        UUID playerId = player != null ? QuestingAPI.getQuestingUUID(player) : NameCache.INSTANCE.getUUID(username);
        if (playerId == null) {
            BetterQuesting.logger.error("Unable to identify {} to invite to party {}", username, partyID);
            return;
        }

        PartyInvitations.INSTANCE.postInvite(playerId, partyID, expiry);
        if (player != null) {
            NetPartySync.sendSync(new EntityPlayerMP[] { player }, new int[] { partyID });
            NetInviteSync.sendSync(player);
        }
    }

    private static void acceptInvite(int partyID, EntityPlayerMP sender) {
        UUID playerID = QuestingAPI.getQuestingUUID(sender);
        DBEntry<IParty> party = PartyManager.INSTANCE.getParty(playerID);
        if (party != null) return;

        if (PartyInvitations.INSTANCE.acceptInvite(playerID, partyID)) {
            NetPartySync.quickSync(partyID);
            NetNameSync.quickSync(sender, partyID);
        } else {
            BetterQuesting.logger.error("Invalid invite for {} to party {}", sender.getCommandSenderName(), partyID);
        }
        NetInviteSync.sendSync(sender);
    }

    // It's also the leave action (self kick if you will)
    private static void kickUser(int partyId, EntityPlayerMP sender, IParty party, String username,
        int senderPermission) {
        if (party == null) {
            BetterQuesting.logger.error("Tried to kick a player from a non-existent party ({})", partyId);
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        EntityPlayerMP target = server.getConfigurationManager()
            .func_152612_a(username);

        UUID targetId = target != null ? QuestingAPI.getQuestingUUID(target) : NameCache.INSTANCE.getUUID(username);
        if (targetId == null) {
            BetterQuesting.logger.error("Unable to identify {} to remove them from party {}", username, partyId);
            return;
        }

        boolean isLeave = targetId.equals(QuestingAPI.getQuestingUUID(sender));
        boolean senderCanKickTarget = getPermission(targetId, party) < senderPermission;
        if (!isLeave && !senderCanKickTarget) {
            BetterQuesting.logger.error("Insufficient permissions to kick {} from party {}", username, partyId);
            return;
        }

        // Even if the kick isn't confirmed we still need to tell the clients in case of desync
        if (party.getStatus(targetId) != null) {
            party.kickUser(targetId);
        }

        if (!party.getMembers()
            .isEmpty()) {
            NetPartySync.quickSync(partyId);
            if (target != null) {
                NBTTagCompound payload = new NBTTagCompound();
                payload.setInteger(TAG_ACTION, ACTION_KICK);
                payload.setInteger(TAG_PARTY_ID, partyId);
                PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), target);
            }
        } else {
            PartyManager.INSTANCE.removeID(partyId);
            PartyInvitations.INSTANCE.purgeInvites(partyId);

            NBTTagCompound payload = new NBTTagCompound();
            payload.setInteger(TAG_ACTION, ACTION_DELETE);
            payload.setInteger(TAG_PARTY_ID, partyId);
            // Invites need to be purged from everyone
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        }
    }

    private static int getPermission(UUID playerID, IParty party) {
        MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();

        EntityPlayerMP player = null;
        for (EntityPlayerMP playerMP : server.getConfigurationManager().playerEntityList) {
            if (playerMP.getGameProfile()
                .getId()
                .equals(playerID)) {
                player = playerMP;
            }
        }

        // OPs can kick owners or force invites without needing to be a member of the party
        if (player != null && server.getConfigurationManager()
            .func_152596_g(player.getGameProfile())) return 4;

        EnumPartyStatus status = party.getStatus(playerID);
        if (status == null) return 0;

        return switch (status) {
            case MEMBER -> 1;
            case ADMIN -> 2;
            case OWNER -> 3;
        };
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        int action = message.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? message.getInteger(TAG_ACTION) : -1;
        int partyID = message.hasKey(TAG_PARTY_ID, Constants.NBT.TAG_ANY_NUMERIC) ? message.getInteger(TAG_PARTY_ID)
            : -1;

        switch (action) {
            case ACTION_DELETE -> {
                PartyManager.INSTANCE.removeID(partyID);
                PartyInvitations.INSTANCE.purgeInvites(partyID);
                MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.PARTY));
            }
            case ACTION_KICK -> {
                IParty party = PartyManager.INSTANCE.getValue(partyID);
                if (party != null) {
                    party.kickUser(QuestingAPI.getQuestingUUID(Minecraft.getMinecraft().thePlayer));
                    MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Update(DBType.PARTY));
                }
            }
        }
    }
}
