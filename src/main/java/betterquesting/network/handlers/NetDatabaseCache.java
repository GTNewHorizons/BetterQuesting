package betterquesting.network.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.google.common.hash.Hashing;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.client.QuestDatabaseCache;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Synchronizes quest and chapter definitions on login.
 * <p>
 * The server offers the hash of the active definitions. The client confirms a matching copy or requests the
 * definitions. The server sends player progress separately.
 */
public class NetDatabaseCache {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:db_cache");

    private static final String TAG_HASH = "hash";
    private static final String TAG_DATA = "data";
    private static final String TAG_QUESTS = "quests";
    private static final String TAG_CHAPTERS = "chapters";

    private static ServerSnapshot serverSnapshot;

    public static void registerHandler() {
        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetDatabaseCache::onClient);
        }
    }

    static NBTTagCompound snapshotDefinitions() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag(TAG_QUESTS, NetQuestSync.buildPayload(null, null, true, false, false));
        snapshot.setTag(TAG_CHAPTERS, NetChapterSync.buildPayload(null));
        return snapshot;
    }

    public static synchronized void invalidateServerSnapshot() {
        serverSnapshot = null;
    }

    /**
     * Captures one revision on the server thread. Serialization and packet creation use the same network queue.
     */
    static synchronized ServerSnapshot getServerSnapshot() {
        boolean noRewards = BQ_Settings.noRewards;
        if (serverSnapshot == null || serverSnapshot.noRewards != noRewards) {
            NBTTagCompound definitions = snapshotDefinitions();
            ServerSnapshot snapshot = new ServerSnapshot(noRewards);
            BQThreadedIO.INSTANCE.enqueue(() -> snapshot.encode(definitions));
            serverSnapshot = snapshot;
        }
        return serverSnapshot;
    }

    public static String hash(byte[] data) {
        return Hashing.sha256()
            .hashBytes(data)
            .toString();
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        String hash = message.getString(TAG_HASH);
        byte[] data;

        boolean received = message.hasKey(TAG_DATA, Constants.NBT.TAG_BYTE_ARRAY);
        if (received) {
            data = message.getByteArray(TAG_DATA);
            if (!hash.equals(hash(data))) {
                BetterQuesting.logger.warn("Received quest database snapshot does not match its hash");
                return;
            }
        } else {
            data = QuestDatabaseCache.load(hash);
            if (data == null) {
                // Request a fresh snapshot if the local copy is unavailable
                BetterQuesting.logger.warn("Cached quest database {} is unavailable, requesting a full sync", hash);
                NetBulkSync.requestSync("");
                return;
            }
        }

        NBTTagCompound snapshot;
        try {
            snapshot = readSnapshot(data);
        } catch (IOException e) {
            BetterQuesting.logger.error("Unable to read quest database snapshot", e);
            if (!received) NetBulkSync.requestSync("");
            return;
        }

        NetQuestSync.onClient(snapshot.getCompoundTag(TAG_QUESTS));
        NetChapterSync.onClient(snapshot.getCompoundTag(TAG_CHAPTERS));
        if (received) QuestDatabaseCache.store(hash, data);
        BetterQuesting.logger.info(
            "Quest database {} {}: {} quests, {} chapters",
            hash,
            received ? "received" : "loaded from cache",
            QuestDatabase.INSTANCE.size(),
            QuestLineDatabase.INSTANCE.size());
    }

    private static NBTTagCompound readSnapshot(byte[] data) throws IOException {
        NBTTagCompound snapshot = CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(data)));
        if (!snapshot.hasKey(TAG_QUESTS, Constants.NBT.TAG_COMPOUND)
            || !snapshot.hasKey(TAG_CHAPTERS, Constants.NBT.TAG_COMPOUND)) {
            throw new IOException("Missing quest or chapter definitions in snapshot");
        }
        return snapshot;
    }

    /**
     * The network queue encodes the snapshot before creating packets. Queued packets keep the captured revision.
     */
    static final class ServerSnapshot {

        private final boolean noRewards;
        private byte[] data;
        private String hash;

        private ServerSnapshot(boolean noRewards) {
            this.noRewards = noRewards;
        }

        private void encode(NBTTagCompound definitions) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                CompressedStreamTools.write(definitions, new DataOutputStream(out));
                data = out.toByteArray();
                hash = NetDatabaseCache.hash(data);
            } catch (IOException failure) {
                BetterQuesting.logger.error("Could not serialize quest definitions for login sync", failure);
                // The reset omits the hash and the client requests the usual full sync
                invalidateFailedSnapshot(this);
            }
        }

        @Nullable
        String hash() {
            return hash;
        }

        @Nullable
        QuestingPacket createSyncPacket(String cachedHash) {
            if (hash == null) return null;
            NBTTagCompound payload = new NBTTagCompound();
            payload.setString(TAG_HASH, hash);
            if (!hash.equals(cachedHash)) payload.setByteArray(TAG_DATA, data);
            return new QuestingPacket(ID_NAME, payload);
        }
    }

    private static synchronized void invalidateFailedSnapshot(ServerSnapshot failed) {
        if (serverSnapshot == failed) serverSnapshot = null;
    }
}
