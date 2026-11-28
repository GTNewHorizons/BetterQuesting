package betterquesting.network.handlers;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.Level;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.utils.Tuple2;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.party.PartyManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetQuestAction {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:quest_action");

    private static final int ACTION_CLAIM = 0;
    private static final int ACTION_DETECT = 1;
    private static final int ACTION_CLAIM_FORCE_CHOICE = 2;

    private static final String TAG_ACTION = "action";
    private static final String TAG_QUEST_IDS = "questIDs";

    public static void registerHandler() {
        PacketTypeRegistry.INSTANCE.registerServerHandler(ID_NAME, NetQuestAction::receivePacket);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClaim(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, ACTION_CLAIM);
    }

    @SideOnly(Side.CLIENT)
    public static void requestDetect(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, ACTION_DETECT);
    }

    @SideOnly(Side.CLIENT)
    public static void requestClaimForceChoice(@Nonnull Collection<UUID> questIDs) {
        sendPacket(questIDs, ACTION_CLAIM_FORCE_CHOICE);
    }

    private static void sendPacket(@Nonnull Collection<UUID> questIDs, int actionCode) {
        if (questIDs.isEmpty()) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setInteger(TAG_ACTION, actionCode);
        payload.setTag(TAG_QUEST_IDS, NBTConverter.UuidValueType.QUEST.writeIds(questIDs));

        PacketSender.INSTANCE.sendToServer(new QuestingPacket(ID_NAME, payload));
    }

    private static void receivePacket(Tuple2<NBTTagCompound, EntityPlayerMP> message) {
        NBTTagCompound payload = message.getFirst();
        EntityPlayerMP sender = message.getSecond();

        int action = payload.hasKey(TAG_ACTION, Constants.NBT.TAG_ANY_NUMERIC) ? payload.getInteger(TAG_ACTION) : -1;
        List<UUID> questIDs = NBTConverter.UuidValueType.QUEST.readIds(payload, TAG_QUEST_IDS);

        switch (action) {
            case ACTION_CLAIM -> claimQuest(questIDs, sender);
            case ACTION_DETECT -> detectQuest(questIDs, sender);
            case ACTION_CLAIM_FORCE_CHOICE -> claimQuestForceChoice(questIDs, sender);
            default -> BetterQuesting.logger
                .log(Level.ERROR, "Invalid quest user action '{}'. Full payload:\n{}", action, payload);
        }
    }

    public static void claimQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        claimQuests(questIDs, player, false);
    }

    public static void detectQuest(Collection<UUID> questIDs, EntityPlayerMP player) {
        QuestDatabase.INSTANCE.getAll(questIDs)
            .forEach(q -> q.detect(player));
    }

    public static void claimQuestForceChoice(Collection<UUID> questIDs, EntityPlayerMP player) {
        claimQuests(questIDs, player, true);
    }

    private static void claimQuests(Collection<UUID> questIDs, EntityPlayerMP player, boolean forceChoice) {
        Iterator<IQuest> eligible = QuestDatabase.INSTANCE.getAll(questIDs)
            .filter(q -> forceChoice ? q.canClaim(player, true) : q.canClaim(player))
            .iterator();
        if (!eligible.hasNext()) return;
        List<UUID> claimants = PartyManager.INSTANCE.getRewardClaimants(player);
        if (claimants.size() > 1 && !SaveLoadHandler.INSTANCE.ensurePlayerProgressLoaded(claimants)) {
            player.addChatMessage(new ChatComponentTranslation("betterquesting.cmd.reward.party_unreadable"));
            return;
        }
        eligible.forEachRemaining(q -> {
            if (forceChoice) q.claimReward(player, true);
            else q.claimReward(player);
        });
    }
}
