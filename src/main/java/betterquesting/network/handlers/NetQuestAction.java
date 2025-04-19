package betterquesting.network.handlers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.Level;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetQuestAction {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:quest_action");

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestAction::onServer);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClaim(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, 0);
    }

    @SideOnly(Side.CLIENT)
    public static void requestDetect(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, 1);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClaimForced(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, 2);
    }

    private static void sendPacket(@Nonnull Collection<UUID> questIDs, int actionCode) {
        if (questIDs.isEmpty()) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger("action", actionCode);
        payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(questIDs));

        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void onServer(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        int action = !message.getFirst()
            .hasKey("action", 99) ? -1
                : message.getFirst()
                    .getInteger("action");

        Supplier<List<UUID>> getQuestIDs = () -> NBTConverter.UuidValueType.QUEST
            .readIds(message.getFirst(), "questIDs");

        switch (action) {
            case 0: {
                claimQuest(getQuestIDs.get(), message.getSecond());
                break;
            }
            case 1: {
                detectQuest(getQuestIDs.get(), message.getSecond());
                break;
            }
            case 2: {
                forceClaimQuest(getQuestIDs.get(), message.getSecond());
                break;
            }
            default: {
                BetterQuesting.logger.log(
                    Level.ERROR,
                    "Invalid quest user action '" + action
                        + "'. Full payload:\n"
                        + message.getFirst()
                            .toString());
            }
        }
    }

    public static void claimQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.getAll(questIDs)
            .filter(q -> q.canClaim(player))
            .forEach(q -> q.claimReward(player));
    }

    public static void detectQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.filterKeys(questIDs)
            .values()
            .forEach(q -> q.detect(player));
    }

    public static void forceClaimQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.getAll(questIDs)
            .filter(q -> q.canClaim(player, true))
            .forEach(q -> q.claimReward(player, true));
    }
}
