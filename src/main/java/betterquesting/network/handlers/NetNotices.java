package betterquesting.network.handlers;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;

import betterquesting.api.events.QuestNotificationEvent;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.QuestNotification;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class NetNotices {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:notification");

    public static void registerHandler() {
        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetNotices::onClient);
        }
    }

    public static void sendNotice(@Nullable EntityPlayerMP[] players, ItemStack icon, String mainText, String subText,
        String questId, String sound) {
        sendNotice(players, icon, mainText, subText, questId, sound, "default", "default", null, -1);
    }

    public static void sendNotice(@Nullable EntityPlayerMP[] players, ItemStack icon, String mainText, String subText,
        String questId, String sound, String particle, String animation, @Nullable ItemStack confettiIcon,
        int particleCount) {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setTag("icon", icon == null ? new NBTTagCompound() : icon.writeToNBT(new NBTTagCompound()));
        if (mainText != null) payload.setString("mainText", mainText);
        if (subText != null) payload.setString("subText", subText);
        if (questId != null) payload.setString("questId", questId);
        if (sound != null) payload.setString("sound", sound);
        if (particle != null) payload.setString("particle", particle);
        if (animation != null) payload.setString("animation", animation);
        if (confettiIcon != null) {
            payload.setTag("confettiIcon", confettiIcon.writeToNBT(new NBTTagCompound()));
        }
        if (particleCount >= 0) payload.setInteger("particleCount", particleCount);

        if (players != null) {
            PacketSender.INSTANCE.sendToPlayers(new QuestingPacket(ID_NAME, payload), players);
        } else {
            PacketSender.INSTANCE.sendToAll(new QuestingPacket(ID_NAME, payload));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(message.getCompoundTag("icon"));
        String mainTxt = message.getString("mainText");
        String subTxt = message.getString("subText");
        String questIdStr = message.getString("questId");
        String sound = message.getString("sound");
        String particle = message.hasKey("particle") ? message.getString("particle") : "default";
        String animation = message.hasKey("animation") ? message.getString("animation") : "default";
        ItemStack confettiIcon = message.hasKey("confettiIcon")
            ? ItemStack.loadItemStackFromNBT(message.getCompoundTag("confettiIcon"))
            : null;
        int particleCount = message.hasKey("particleCount") ? message.getInteger("particleCount") : -1;

        if ((subTxt == null || subTxt.isEmpty()) && questIdStr != null && !questIdStr.isEmpty()) {
            subTxt = questIdStr;
        }
        if (questIdStr != null && !questIdStr.isEmpty()) {
            try {
                UUID questId = UUID.fromString(questIdStr);
                IQuest quest = QuestDatabase.INSTANCE.get(questId);
                if (quest != null) {
                    String translatedName = QuestTranslation.getQuestNameKeyOrPropertyName(questId, quest);
                    if (translatedName != null && !translatedName.isEmpty()) {
                        subTxt = translatedName;
                    } else {
                        subTxt = quest.getProperty(NativeProps.NAME);
                    }
                }
            } catch (IllegalArgumentException e) {}
        }

        QuestNotificationEvent event = new QuestNotificationEvent(
            subTxt,
            stack,
            sound,
            particle,
            animation,
            confettiIcon);
        if (!MinecraftForge.EVENT_BUS.post(event)) {
            QuestNotification.ScheduleNotice(
                mainTxt,
                subTxt,
                stack,
                sound,
                event.getParticleEffect(),
                event.getIconAnimation(),
                event.getConfettiIcon(),
                particleCount);
        }
    }
}
