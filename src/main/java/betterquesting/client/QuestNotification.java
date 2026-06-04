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
import betterquesting.network.handlers.NoticeConfig;
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
    private static boolean hintShownThisSession = false;

    public static void setPendingScreen(GuiScreen screen) {
        pendingScreen = screen;
    }

    public static void ScheduleNotice(String mainTxt, String subTxt, ItemStack icon, String sound) {
        ScheduleNotice(mainTxt, subTxt, icon, sound, new NoticeConfig());
    }

    public static void ScheduleNotice(String mainTxt, String subTxt, ItemStack icon, String sound,
        NoticeConfig config) {
        if ("off".equals(effStyle(config))) {
            if (pendingScreen != null) {
                Minecraft.getMinecraft()
                    .displayGuiScreen(pendingScreen);
                pendingScreen = null;
            }
            return;
        }
        boolean showHint = !BQ_Settings.notificationHintSeen && !hintShownThisSession;
        if (showHint) hintShownThisSession = true;
        notices.add(new QuestNotice(mainTxt, subTxt, icon, sound, config, showHint));
    }

    /** Fire a one-off notice for the editor preview, using the same resolution path but never the hint. */
    public static void previewNotice(String mainTxt, String subTxt, ItemStack icon, String sound, NoticeConfig config) {
        if ("off".equals(effStyle(config))) return;
        notices.add(new QuestNotice(mainTxt, subTxt, icon, sound, config, false));
    }

    /** False when this config would resolve to no notification (player "off" or quest "off"). */
    public static boolean wouldShow(NoticeConfig config) {
        return !"off".equals(effStyle(config));
    }

    // ---- precedence resolution: quest value if set, else player; player "off" always wins ----
    private static String effStyle(NoticeConfig c) {
        if ("off".equals(BQ_Settings.notificationStyle)) return "off";
        return !"default".equals(c.style) ? c.style : BQ_Settings.notificationStyle;
    }

    private static boolean effTitleMode(NoticeConfig c) {
        return HAS_TITLE_API && "title".equals(effStyle(c));
    }

    private static float effDuration(NoticeConfig c) {
        return c.duration >= 0 ? c.duration : BQ_Settings.notificationDuration;
    }

    private static float effFadeIn(NoticeConfig c) {
        return c.fadeIn >= 0 ? c.fadeIn : BQ_Settings.notificationFadeIn;
    }

    private static float effFadeOut(NoticeConfig c) {
        return c.fadeOut >= 0 ? c.fadeOut : BQ_Settings.notificationFadeOut;
    }

    private static boolean effShowIcon(NoticeConfig c) {
        if ("yes".equals(c.showIcon)) return true;
        if ("no".equals(c.showIcon)) return false;
        return BQ_Settings.showNotificationIcon;
    }

    private static String resolveSubtitle(QuestNotice notice) {
        if (notice.showHint) {
            return QuestTranslation.translate("betterquesting.notice.customize_hint");
        }
        String custom = notice.config.subtitleText;
        if (custom != null && !custom.isEmpty()) return QuestTranslation.translate(custom);
        return notice.subTxt != null ? QuestTranslation.translate(notice.subTxt) : null;
    }

    private static void showTitleIfAvailable(QuestNotice notice) {
        if (!HAS_TITLE_API) return;
        NoticeConfig c = notice.config;

        int fadeInTicks = Math.max(0, (int) (effFadeIn(c) * 20));
        int fadeOutTicks = Math.max(0, (int) (effFadeOut(c) * 20));
        int totalTicks = Math.max(fadeInTicks + fadeOutTicks + 1, (int) (effDuration(c) * 20));
        int stayTicks = totalTicks - fadeInTicks - fadeOutTicks;

        TitleAPI.setTimes(fadeInTicks, stayTicks, fadeOutTicks);

        if (effShowIcon(c) && notice.icon != null) {
            TitleAPI.setIcon(notice.icon);
            TitleAPI.setIconScale(c.iconScale > 0 ? c.iconScale : BQ_Settings.notificationIconScale);
            TitleAPI.setIconOffsetY(
                c.iconOffsetY != Integer.MIN_VALUE ? c.iconOffsetY : BQ_Settings.notificationIconOffsetY);
        } else {
            TitleAPI.setIcon(null);
        }

        float titleScale = c.titleScale > 0 ? c.titleScale : BQ_Settings.notificationTitleScale;
        if (titleScale > 0) TitleAPI.setTitleScale(titleScale);
        float subtitleScale = c.subtitleScale > 0 ? c.subtitleScale : BQ_Settings.notificationSubtitleScale;
        if (subtitleScale > 0) TitleAPI.setSubtitleScale(subtitleScale);

        int px = c.posX != Integer.MIN_VALUE ? c.posX : BQ_Settings.notificationTitleOffsetX;
        int py = c.posY != Integer.MIN_VALUE ? c.posY : BQ_Settings.notificationTitleOffsetY;
        if (c.posX != Integer.MIN_VALUE || c.posY != Integer.MIN_VALUE
            || BQ_Settings.notificationTitleOffsetX != 0
            || BQ_Settings.notificationTitleOffsetY != 0) {
            TitleAPI.setTitlePosition(px, py);
        }

        String particle = "default".equals(c.particle) ? BQ_Settings.notificationParticle : c.particle;
        TitleAPI.setParticleEffect(particleStringToInt(particle));
        TitleAPI.setParticleCount(c.particleCount);

        TitleAPI.setEffectTier(c.effectTier >= 0 ? c.effectTier : BQ_Settings.notificationEffectTier);

        String anim = "default".equals(c.animation) ? BQ_Settings.notificationIconAnimation : c.animation;
        TitleAPI.setIconAnimation(animStringToInt(anim));

        ItemStack confetti = c.confettiIcon != null ? c.confettiIcon : notice.icon;
        if (confetti != null) {
            TitleAPI.setConfettiIcon(confetti);
        }

        String subLine = resolveSubtitle(notice);
        TitleAPI.setSubtitle(subLine != null && !subLine.isEmpty() ? new ChatComponentText(subLine) : null);
        TitleAPI.setTitle(new ChatComponentText(QuestTranslation.translate(notice.mainTxt)));
    }

    private static int animStringToInt(String anim) {
        switch (anim) {
            case "fly_in":
                return TitleAPI.ICON_ANIM_FLY_IN;
            case "rise":
                return TitleAPI.ICON_ANIM_RISE;
            case "slide":
                return TitleAPI.ICON_ANIM_SLIDE;
            case "zoom":
                return TitleAPI.ICON_ANIM_ZOOM;
            case "pop":
                return TitleAPI.ICON_ANIM_POP;
            case "spin":
                return TitleAPI.ICON_ANIM_SPIN;
            case "spin_reverse":
                return TitleAPI.ICON_ANIM_SPIN_REVERSE;
            case "bounce":
                return TitleAPI.ICON_ANIM_BOUNCE;
            case "wobble":
                return TitleAPI.ICON_ANIM_WOBBLE;
            case "swing":
                return TitleAPI.ICON_ANIM_SWING;
            case "slam":
                return TitleAPI.ICON_ANIM_SLAM;
            case "tada":
                return TitleAPI.ICON_ANIM_TADA;
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

            if (effTitleMode(notice.config)) {
                showTitleIfAvailable(notice);
            }

            float volume = notice.sound.equals(NativeProps.SOUND_COMPLETE.getDefault()) ? 0.25f : 1f;
            mc.getSoundHandler()
                .playSound(new QuestCompleteSound(new ResourceLocation(notice.sound), volume));
        }

        float displayDuration = effDuration(notice.config);
        if (notice.getTime() >= displayDuration) {
            notices.remove(0);
            if (notices.isEmpty() && pendingScreen != null) {
                mc.displayGuiScreen(pendingScreen);
                pendingScreen = null;
            }
            return;
        }

        if (effTitleMode(notice.config)) return;

        float fadeInTime = effFadeIn(notice.config);
        float fadeOutTime = effFadeOut(notice.config);
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

            if (effShowIcon(notice.config) && notice.icon != null) {
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
            tmp = resolveSubtitle(notice);
            if (tmp == null) tmp = "";
            txtW = RenderUtils.getStringWidth(tmp, mc.fontRenderer);
            mc.fontRenderer.drawString(tmp, width / 2 - txtW / 2, height / 4 + 12, color, true);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
        GL11.glPopMatrix();
    }

    public static class QuestNotice {

        public long startTime;
        public boolean init = false;
        public final boolean showHint;
        private final String mainTxt;
        private final String subTxt;
        private final ItemStack icon;
        private final String sound;
        private final NoticeConfig config;

        public QuestNotice(String mainTxt, String subTxt, ItemStack icon, String sound) {
            this(mainTxt, subTxt, icon, sound, new NoticeConfig(), false);
        }

        public QuestNotice(String mainTxt, String subTxt, ItemStack icon, String sound, NoticeConfig config,
            boolean showHint) {
            this.startTime = Minecraft.getSystemTime();
            this.mainTxt = mainTxt;
            this.subTxt = subTxt;
            this.icon = icon;
            this.sound = sound;
            this.config = config;
            this.showHint = showHint;
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
