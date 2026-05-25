package betterquesting.client;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.client.title.TitleAPI;
import com.gtnewhorizon.gtnhlib.client.title.TitleParticleSystem;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.utils.QuestTranslation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class QuestNotification {

    private static final boolean HAS_TITLE_API;
    static {
        boolean found;
        try {
            found = QuestNotification.class.getClassLoader()
                .getResource("com/gtnewhorizon/gtnhlib/client/title/TitleAPI.class") != null;
        } catch (Exception e) {
            found = false;
        }
        HAS_TITLE_API = found;
    }

    private static final List<QuestNotice> notices = new ArrayList<>();
    private static GuiScreen pendingScreen = null;

    public static void setPendingScreen(GuiScreen screen) {
        pendingScreen = screen;
    }

    public static void ScheduleNotice(String mainTxt, String subTxt, ItemStack icon, String sound) {
        ScheduleNotice(mainTxt, subTxt, icon, sound, "default", "default", null, -1);
    }

    public static void ScheduleNotice(String mainTxt, String subTxt, ItemStack icon, String sound, String particle,
        String animation, ItemStack confettiIcon, int particleCount) {
        if ("off".equals(BQ_Settings.notificationStyle)) return;
        notices.add(new QuestNotice(mainTxt, subTxt, icon, sound, particle, animation, confettiIcon, particleCount));
    }

    private static void showTitleIfAvailable(QuestNotice notice) {
        if (!HAS_TITLE_API) return;

        int fadeInTicks = Math.max(0, (int) (BQ_Settings.notificationFadeIn * 20));
        int fadeOutTicks = Math.max(0, (int) (BQ_Settings.notificationFadeOut * 20));
        int totalTicks = Math.max(fadeInTicks + fadeOutTicks + 1, (int) (BQ_Settings.notificationDuration * 20));
        int stayTicks = totalTicks - fadeInTicks - fadeOutTicks;

        TitleAPI.setTimes(fadeInTicks, stayTicks, fadeOutTicks);

        if (BQ_Settings.showNotificationIcon && notice.icon != null) {
            TitleAPI.setIcon(notice.icon);
            TitleAPI.setIconScale(BQ_Settings.notificationIconScale);
            TitleAPI.setIconOffsetY(BQ_Settings.notificationIconOffsetY);
        } else {
            TitleAPI.setIcon(null);
        }

        if (BQ_Settings.notificationTitleScale > 0) {
            TitleAPI.setTitleScale(BQ_Settings.notificationTitleScale);
        }
        if (BQ_Settings.notificationSubtitleScale > 0) {
            TitleAPI.setSubtitleScale(BQ_Settings.notificationSubtitleScale);
        }

        String particle = "default".equals(notice.particle) ? BQ_Settings.notificationParticle : notice.particle;
        TitleAPI.setParticleEffect(particleStringToInt(particle));
        TitleAPI.setParticleCount(notice.particleCount);

        String anim = "default".equals(notice.animation) ? BQ_Settings.notificationIconAnimation : notice.animation;
        TitleAPI.setIconAnimation(animStringToInt(anim));

        ItemStack confetti = notice.confettiIcon != null ? notice.confettiIcon : notice.icon;
        if (confetti != null) {
            TitleAPI.setConfettiIcon(confetti);
        }

        String subTxt = notice.subTxt;
        TitleAPI.setSubtitle(
            subTxt != null && !subTxt.isEmpty() ? new ChatComponentText(QuestTranslation.translate(subTxt)) : null);
        TitleAPI.setTitle(new ChatComponentText(QuestTranslation.translate(notice.mainTxt)));
    }

    private static int animStringToInt(String anim) {
        switch (anim) {
            case "fly_in":
                return TitleAPI.ICON_ANIM_FLY_IN;
            case "spin":
                return TitleAPI.ICON_ANIM_SPIN;
            default:
                return TitleAPI.ICON_ANIM_NONE;
        }
    }

    private static int particleStringToInt(String particle) {
        switch (particle) {
            case "confetti":
                return TitleParticleSystem.PARTICLE_CONFETTI;
            case "sparkle":
                return TitleParticleSystem.PARTICLE_SPARKLE;
            case "firework":
                return TitleParticleSystem.PARTICLE_FIREWORK;
            case "item_confetti":
                return TitleParticleSystem.PARTICLE_ITEM_CONFETTI;
            default:
                return TitleParticleSystem.PARTICLE_NONE;
        }
    }

    private static boolean useTitleMode() {
        return HAS_TITLE_API && "title".equals(BQ_Settings.notificationStyle);
    }

    public static void resetNotices() {
        notices.clear();
    }

    @SubscribeEvent
    public void onDrawScreen(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HELMET || notices.isEmpty()) {
            return;
        }

        if (notices.size() >= 20 || "off".equals(BQ_Settings.notificationStyle)) {
            notices.clear();
            pendingScreen = null;
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        int width = event.resolution.getScaledWidth();
        int height = event.resolution.getScaledHeight();
        final QuestNotice notice = notices.get(0);

        if (!notice.init) {
            if (mc.isGamePaused() || mc.currentScreen != null) {
                return;
            }
            notice.init = true;
            notice.startTime = Minecraft.getSystemTime();

            if (useTitleMode()) {
                showTitleIfAvailable(notice);
            }

            float volume = notice.sound.equals(NativeProps.SOUND_COMPLETE.getDefault()) ? 0.25f : 1f;
            mc.getSoundHandler()
                .playSound(new QuestCompleteSound(new ResourceLocation(notice.sound), volume));
        }

        float displayDuration = BQ_Settings.notificationDuration;
        if (notice.getTime() >= displayDuration) {
            notices.remove(0);
            if (notices.isEmpty() && pendingScreen != null) {
                mc.displayGuiScreen(pendingScreen);
                pendingScreen = null;
            }
            return;
        }

        if (useTitleMode()) return;

        float fadeInTime = BQ_Settings.notificationFadeIn;
        float fadeOutTime = BQ_Settings.notificationFadeOut;
        float fadeOutStart = displayDuration - fadeOutTime;

        float alpha;
        if (notice.getTime() < fadeInTime && fadeInTime > 0) {
            alpha = notice.getTime() / fadeInTime;
        } else if (notice.getTime() > fadeOutStart && fadeOutTime > 0) {
            alpha = (displayDuration - notice.getTime()) / fadeOutTime;
        } else {
            alpha = 1.0F;
        }
        alpha = MathHelper.clamp_float(alpha, 0.02F, 1F);
        final int color = new Color(1F, 1F, 1F, alpha).getRGB();

        if (alpha < 0.2F) return;

        GL11.glPushMatrix();
        {
            final float scale = width > 600 ? 1.5F : 1F;

            GL11.glScalef(scale, scale, scale);
            width = MathHelper.ceiling_float_int(width / scale);
            height = MathHelper.ceiling_float_int(height / scale);

            if (BQ_Settings.showNotificationIcon && notice.icon != null) {
                RenderUtils.RenderItemStack(mc, notice.icon, width / 2 - 8, height / 4 - 20, "", color);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            String tmp = EnumChatFormatting.UNDERLINE + ""
                + EnumChatFormatting.BOLD
                + QuestTranslation.translate(notice.mainTxt);
            int txtW = RenderUtils.getStringWidth(tmp, mc.fontRenderer);
            mc.fontRenderer.drawString(tmp, width / 2 - txtW / 2, height / 4, color, true);
            tmp = QuestTranslation.translate(notice.subTxt);
            txtW = RenderUtils.getStringWidth(tmp, mc.fontRenderer);
            mc.fontRenderer.drawString(tmp, width / 2 - txtW / 2, height / 4 + 12, color, true);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        GL11.glPopMatrix();
    }

    public static class QuestNotice {

        public long startTime;
        public boolean init = false;
        private final String mainTxt;
        private final String subTxt;
        private final ItemStack icon;
        private final String sound;
        private final String particle;
        private final String animation;
        private final ItemStack confettiIcon;
        private final int particleCount;

        public QuestNotice(String mainTxt, String subTxt, ItemStack icon, String sound) {
            this(mainTxt, subTxt, icon, sound, "default", "default", null, -1);
        }

        public QuestNotice(String mainTxt, String subTxt, ItemStack icon, String sound, String particle,
            String animation, ItemStack confettiIcon, int particleCount) {
            this.startTime = Minecraft.getSystemTime();
            this.mainTxt = mainTxt;
            this.subTxt = subTxt;
            this.icon = icon;
            this.sound = sound;
            this.particle = particle;
            this.animation = animation;
            this.confettiIcon = confettiIcon;
            this.particleCount = particleCount;
        }

        public float getTime() {
            return (Minecraft.getSystemTime() - startTime) / 1000F;
        }
    }

    public static class QuestCompleteSound extends PositionedSound {

        public QuestCompleteSound(ResourceLocation resource, float volume) {
            super(resource);
            this.volume = volume;
            this.field_147663_c = 1; // Pitch
            this.xPosF = 0;
            this.yPosF = 0;
            this.zPosF = 0;
            this.repeat = false;
            this.field_147665_h = 0; // Repeat time
            this.field_147666_i = AttenuationType.NONE;
        }
    }
}
