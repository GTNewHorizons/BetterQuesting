package betterquesting.network;

import javax.annotation.Nullable;

import net.minecraft.network.NetworkManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tells client-side packet handlers whether the packet they are handling came from this client's own integrated server
 * (singleplayer or Open to LAN host).
 * <p>
 * The logical client and the integrated server run in the same JVM, so they share every BetterQuesting database
 * singleton ({@code QuestDatabase.INSTANCE}, {@code PartyManager.INSTANCE}, ...). Packets from that server describe
 * data the server has already written into those same objects. Applying them again on the client is at best a no-op.
 * At worst it destroys server state: a full sync clears the shared database and rebuilds it from a payload that only
 * carries the host's own progress, and a delayed snapshot overwrites newer values. With other players connected over
 * LAN, that wipes or rolls back their quest progress and parties.
 * <p>
 * Handlers that copy server data into a database must skip the copy when {@link #isHost()} is {@code true}, and only
 * notify the GUI that data changed.
 * <p>
 * The decision is made per packet from the connection it arrived on (only the integrated server uses an in-memory
 * connection), not from {@code Minecraft.isIntegratedServerRunning()}: that flag can stay set after an aborted
 * singleplayer launch, which would make the next remote server's syncs be ignored.
 */
@SideOnly(Side.CLIENT)
public final class IntegratedServerHost {

    /** Only accessed on the client thread, while a BetterQuesting packet handler runs. */
    private static boolean handlingHostPacket;

    private IntegratedServerHost() {}

    /**
     * @return {@code true} while handling a packet that came from this client's own integrated server, i.e. when the
     *         client shares the sending server's databases.
     */
    public static boolean isHost() {
        return handlingHostPacket;
    }

    /** Whether a packet received on this connection comes from this client's own integrated server. */
    static boolean isHostConnection(@Nullable NetworkManager connection) {
        return connection != null && connection.isLocalChannel();
    }

    /** Runs a client packet handler with {@link #isHost()} reporting where its packet came from. */
    static void runHandler(boolean fromHost, Runnable handler) {
        boolean previous = handlingHostPacket;
        handlingHostPacket = fromHost;
        try {
            handler.run();
        } finally {
            handlingHostPacket = previous;
        }
    }
}
