package betterquesting.network.handlers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import com.google.common.hash.Hashing;

import betterquesting.api.network.QuestingPacket;
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
 * The server sends the snapshot hash on every login and includes the definitions when the client lacks a matching
 * snapshot. The server sends player progress separately.
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

    static NBTTagCompound snapshotDefinitions() {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setTag(TAG_QUESTS, NetQuestSync.buildPayload(null, null, true, false, false));
        snapshot.setTag(TAG_CHAPTERS, NetChapterSync.buildPayload(null));
        return snapshot;
    }

    static QuestingPacket createSyncPacket(NBTTagCompound snapshot, Collection<String> clientHashes)
        throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompressedStreamTools.write(snapshot, new DataOutputStream(out));
        byte[] data = out.toByteArray();
        String hash = hash(data);
        boolean cached = clientHashes.contains(hash);
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString(TAG_HASH, hash);
        if (!cached) {
            payload.setByteArray(TAG_DATA, data);
        }
        BetterQuesting.logger
            .debug("Quest database {}: {} ({} bytes)", hash, cached ? "cached" : "sending", data.length);
        return new QuestingPacket(ID_NAME, payload);
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
                NetBulkSync.requestSync(false);
                return;
            }
        }

        NBTTagCompound snapshot;
        try {
            snapshot = readSnapshot(data);
        } catch (IOException e) {
            BetterQuesting.logger.error("Unable to read quest database snapshot", e);
            if (!received) NetBulkSync.requestSync(false);
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
}
