package betterquesting.network;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.network.IPacketSender;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;

public class PacketSender implements IPacketSender {

    public static final PacketSender INSTANCE = new PacketSender();

    @Override
    public void sendToPlayers(QuestingPacket payload, EntityPlayerMP... players) {
        setPacketId(payload);
        BQThreadedIO.INSTANCE.enqueue(() -> sendFragments(payload, players));
    }

    /** Prepares and sends a packet in one turn of the network queue. Sends a packet only for a non-null result. */
    public void sendPreparedToPlayers(Supplier<QuestingPacket> packet, EntityPlayerMP... players) {
        BQThreadedIO.INSTANCE.enqueue(() -> {
            QuestingPacket payload = packet.get();
            if (payload == null) return;
            setPacketId(payload);
            sendFragments(payload, players);
        });
    }

    private static void setPacketId(QuestingPacket payload) {
        payload.getPayload()
            .setString(
                "ID",
                payload.getHandler()
                    .toString());
    }

    private void sendFragments(QuestingPacket payload, EntityPlayerMP[] players) {
        List<NBTTagCompound> fragments = PacketAssembly.INSTANCE.splitPacket(payload.getPayload());
        for (EntityPlayerMP p : players) {
            for (NBTTagCompound tag : fragments) {
                BetterQuesting.instance.network.sendTo(new PacketQuesting(tag), p);
            }
        }
    }

    @Override
    public void sendToAll(QuestingPacket payload) {
        setPacketId(payload);

        BQThreadedIO.INSTANCE.enqueue(() -> {
            for (NBTTagCompound p : PacketAssembly.INSTANCE.splitPacket(payload.getPayload())) {
                BetterQuesting.instance.network.sendToAll(new PacketQuesting(p));
            }
        });
    }

    @Override
    public void sendToServer(QuestingPacket payload) {
        payload.getPayload()
            .setString(
                "ID",
                payload.getHandler()
                    .toString());

        BQThreadedIO.INSTANCE.enqueue(() -> {
            for (NBTTagCompound p : PacketAssembly.INSTANCE.splitPacket(payload.getPayload())) {
                BetterQuesting.instance.network.sendToServer(new PacketQuesting(p));
            }
        });
    }

    @Override
    public void sendToAround(QuestingPacket payload, TargetPoint point) {
        payload.getPayload()
            .setString(
                "ID",
                payload.getHandler()
                    .toString());

        BQThreadedIO.INSTANCE.enqueue(() -> {
            for (NBTTagCompound p : PacketAssembly.INSTANCE.splitPacket(payload.getPayload())) {
                BetterQuesting.instance.network.sendToAllAround(new PacketQuesting(p), point);
            }
        });
    }

    @Override
    public void sendToDimension(QuestingPacket payload, int dimension) {
        payload.getPayload()
            .setString(
                "ID",
                payload.getHandler()
                    .toString());

        BQThreadedIO.INSTANCE.enqueue(() -> {
            for (NBTTagCompound p : PacketAssembly.INSTANCE.splitPacket(payload.getPayload())) {
                BetterQuesting.instance.network.sendToDimension(new PacketQuesting(p), dimension);
            }
        });
    }
}
