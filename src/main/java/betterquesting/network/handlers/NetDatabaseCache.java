package betterquesting.network.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.client.QuestDatabaseCache;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Login sync of the quest and chapter configs, which clients can reuse from a previous login.
 * <p>
 * The configs are the same for every player and make up most of the login traffic. The server serializes them
 * into a snapshot and hashes the bytes. A client that already has a snapshot with that hash only gets the hash,
 * otherwise it gets the snapshot and caches it. Progress always follows in a separate quest sync.
 */
public class NetDatabaseCache {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:db_cache");

    private static final String TAG_HASH = "hash";
    private static final String TAG_DATA = "data";
    private static final String TAG_QUESTS = "quests";
    private static final String TAG_CHAPTERS = "chapters";

    public static void registerHandler() {
        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetDatabaseCache::onClient);
        }
    }

    public static void sendSync(EntityPlayerMP player, Collection<String> clientHashes) {
        BQThreadedIO.INSTANCE.enqueue(() -> {
            NBTTagCompound snapshot = new NBTTagCompound();
            snapshot.setTag(TAG_QUESTS, NetQuestSync.buildPayload(null, null, true, false, false));
            snapshot.setTag(TAG_CHAPTERS, NetChapterSync.buildPayload(null));

            byte[] data;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                CompressedStreamTools.write(snapshot, new DataOutputStream(baos));
                data = baos.toByteArray();
            } catch (IOException e) {
                BetterQuesting.logger.error("Unable to serialize quest database snapshot", e);
                return;
            }

            String hash = hash(data);
            boolean cached = clientHashes.contains(hash);
            BetterQuesting.logger.info(
                "Quest database {} ({} bytes) for {}: {}",
                hash,
                data.length,
                player.getCommandSenderName(),
                cached ? "cached by client" : "sending");

            NBTTagCompound payload = new NBTTagCompound();
            payload.setString(TAG_HASH, hash);
            if (!cached) {
                payload.setByteArray(TAG_DATA, data);
            }

            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), player);
            NetQuestSync.sendSync(player, null, false, true);
        });
    }

    public static String hash(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        String hash = message.getString(TAG_HASH);
        byte[] data;

        if (message.hasKey(TAG_DATA, Constants.NBT.TAG_BYTE_ARRAY)) {
            data = message.getByteArray(TAG_DATA);
            if (hash.equals(hash(data))) {
                QuestDatabaseCache.store(hash, data);
            } else {
                BetterQuesting.logger.warn("Quest database snapshot does not match its hash, not caching it");
            }
        } else {
            data = QuestDatabaseCache.load(hash);
            if (data == null) {
                // The cached copy disappeared since we listed it. Ask again, this time for everything.
                BetterQuesting.logger.warn("Cached quest database {} is unavailable, requesting a full sync", hash);
                NetBulkSync.requestSync(false);
                return;
            }
        }

        NBTTagCompound snapshot;
        try {
            snapshot = CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(data)));
        } catch (IOException e) {
            BetterQuesting.logger.error("Unable to read quest database snapshot", e);
            return;
        }

        NetQuestSync.onClient(snapshot.getCompoundTag(TAG_QUESTS));
        NetChapterSync.onClient(snapshot.getCompoundTag(TAG_CHAPTERS));
        BetterQuesting.logger.info(
            "Quest database {} {}: {} quests, {} chapters",
            hash,
            message.hasKey(TAG_DATA) ? "received" : "loaded from cache",
            QuestDatabase.INSTANCE.size(),
            QuestLineDatabase.INSTANCE.size());
    }
}
