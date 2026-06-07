package betterquesting.client.gui2;

import java.util.Collections;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Configuration;

import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.IGuiCanvas;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.QuestNotification;
import betterquesting.handlers.ConfigHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiNotificationSettings extends GuiScreenCanvas implements IPEventListener {

    private static final int BTN_STYLE = 1;
    private static final int BTN_ICON = 2;
    private static final int BTN_DUR_DOWN = 3;
    private static final int BTN_DUR_UP = 4;
    private static final int BTN_FADE_IN_DOWN = 5;
    private static final int BTN_FADE_IN_UP = 6;
    private static final int BTN_FADE_OUT_DOWN = 7;
    private static final int BTN_FADE_OUT_UP = 8;
    private static final int BTN_PREVIEW = 9;
    private static final int BTN_DONE = 10;
    private static final int BTN_ICON_SCALE_DOWN = 11;
    private static final int BTN_ICON_SCALE_UP = 12;
    private static final int BTN_ICON_OFFSET_DOWN = 13;
    private static final int BTN_ICON_OFFSET_UP = 14;
    private static final int BTN_TITLE_SCALE_DOWN = 15;
    private static final int BTN_TITLE_SCALE_UP = 16;
    private static final int BTN_SUB_SCALE_DOWN = 17;
    private static final int BTN_SUB_SCALE_UP = 18;
    private static final int BTN_ANIMATE = 19;
    private static final int BTN_RESET = 20;
    private static final int BTN_PARTICLE = 21;
    private static final int BTN_POS_X_DOWN = 22;
    private static final int BTN_POS_X_UP = 23;
    private static final int BTN_POS_Y_DOWN = 24;
    private static final int BTN_POS_Y_UP = 25;
    private static final int BTN_EFFECT = 26;

    private PanelButton btnStyle;
    private PanelButton btnIcon;
    private PanelButton btnAnimate;
    private PanelButton btnParticle;
    private PanelButton btnEffect;
    private PanelTextBox txtDuration;
    private PanelTextBox txtStatus;
    private PanelTextBox txtFadeIn;
    private PanelTextBox txtFadeOut;
    private PanelTextBox txtIconScale;
    private PanelTextBox txtIconOffset;
    private PanelTextBox txtTitleScale;
    private PanelTextBox txtSubScale;
    private PanelTextBox txtPosX;
    private PanelTextBox txtPosY;

    public GuiNotificationSettings(GuiScreen parent) {
        super(parent);
        // opening this screen dismisses the one-time discovery hint for good
        BQ_Settings.notificationHintSeen = true;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);

        CanvasTextured bgCan = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(bgCan);

        CanvasEmpty inCan = new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(16, 16, 16, 16), 0));
        bgCan.addPanel(inCan);

        int centerW = 200;
        float left = 0.5F;
        float right = 0.5F;

        PanelTextBox title = new PanelTextBox(
            new GuiTransform(new Vector4f(0F, 0F, 1F, 0F), new GuiPadding(0, 0, 0, -16), 0),
            QuestTranslation.translate("betterquesting.notification.settings")).setAlignment(1)
                .setColor(PresetColor.TEXT_HEADER.getColor());
        inCan.addPanel(title);

        CanvasScrolling scrollCan = new CanvasScrolling(
            new GuiTransform(new Vector4f(left, 0F, right, 1F), new GuiPadding(-centerW / 2, 20, -centerW / 2, 24), 0))
                .enableBlocking(false);
        inCan.addPanel(scrollCan);

        PanelVScrollBar scrollBar = new PanelVScrollBar(
            new GuiTransform(
                new Vector4f(right, 0F, right, 1F),
                new GuiPadding(centerW / 2 + 16, 20, -centerW / 2 - 24, 24),
                0));
        inCan.addPanel(scrollBar);
        scrollCan.setScrollDriverY(scrollBar);

        int y = 0;
        int rowH = 20;

        addCycleRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.style"),
            BTN_STYLE,
            getStyleLabel());
        y += rowH;

        addCycleRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.showicon"),
            BTN_ICON,
            getIconLabel());
        y += rowH;

        addCycleRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.animate"),
            BTN_ANIMATE,
            getAnimateLabel());
        y += rowH;

        addCycleRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.particle"),
            BTN_PARTICLE,
            getParticleLabel());
        y += rowH;

        addCycleRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.effect"),
            BTN_EFFECT,
            getEffectLabel());
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.titlescale"),
            formatScaleOrAuto(BQ_Settings.notificationTitleScale),
            BTN_TITLE_SCALE_DOWN,
            BTN_TITLE_SCALE_UP,
            5);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.subtitlescale"),
            formatScaleOrAuto(BQ_Settings.notificationSubtitleScale),
            BTN_SUB_SCALE_DOWN,
            BTN_SUB_SCALE_UP,
            6);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.duration"),
            formatSeconds(BQ_Settings.notificationDuration),
            BTN_DUR_DOWN,
            BTN_DUR_UP,
            0);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.fadein"),
            formatSeconds(BQ_Settings.notificationFadeIn),
            BTN_FADE_IN_DOWN,
            BTN_FADE_IN_UP,
            1);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.fadeout"),
            formatSeconds(BQ_Settings.notificationFadeOut),
            BTN_FADE_OUT_DOWN,
            BTN_FADE_OUT_UP,
            2);
        y += rowH;

        txtStatus = new PanelTextBox(new GuiRectangle(0, y, centerW, 12, 0), getStatusText()).setAlignment(1);
        updateStatusColor();
        scrollCan.addPanel(txtStatus);
        y += 16;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.iconscale"),
            formatScale(BQ_Settings.notificationIconScale),
            BTN_ICON_SCALE_DOWN,
            BTN_ICON_SCALE_UP,
            3);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.iconoffset"),
            String.valueOf(BQ_Settings.notificationIconOffsetY),
            BTN_ICON_OFFSET_DOWN,
            BTN_ICON_OFFSET_UP,
            4);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.posx"),
            String.valueOf(BQ_Settings.notificationTitleOffsetX),
            BTN_POS_X_DOWN,
            BTN_POS_X_UP,
            7);
        y += rowH;

        addNumberRow(
            scrollCan,
            y,
            centerW,
            QuestTranslation.translate("betterquesting.notification.posy"),
            String.valueOf(BQ_Settings.notificationTitleOffsetY),
            BTN_POS_Y_DOWN,
            BTN_POS_Y_UP,
            8);

        int btnW = (centerW - 8) / 3;
        PanelButton resetBtn = new PanelButton(
            new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2, -16, btnW, 16, 0),
            BTN_RESET,
            QuestTranslation.translate("betterquesting.notification.reset"));
        inCan.addPanel(resetBtn);

        PanelButton previewBtn = new PanelButton(
            new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2 + btnW + 4, -16, btnW, 16, 0),
            BTN_PREVIEW,
            QuestTranslation.translate("betterquesting.notification.preview"));
        previewBtn.setTooltip(
            Collections.singletonList(QuestTranslation.translate("betterquesting.notification.preview.tooltip")));
        inCan.addPanel(previewBtn);

        PanelButton doneBtn = new PanelButton(
            new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2 + (btnW + 4) * 2, -16, btnW, 16, 0),
            BTN_DONE,
            QuestTranslation.translate("betterquesting.notification.done"));
        inCan.addPanel(doneBtn);
    }

    private void addCycleRow(IGuiCanvas canvas, int y, int w, String label, int btnId, String btnLabel) {
        PanelTextBox lbl = new PanelTextBox(new GuiRectangle(0, y + 4, w / 2 - 4, 12, 0), label).setAlignment(2)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(lbl);

        PanelButton btn = new PanelButton(new GuiRectangle(w / 2 + 4, y, w / 2 - 4, 16, 0), btnId, btnLabel);
        canvas.addPanel(btn);

        if (btnId == BTN_STYLE) btnStyle = btn;
        else if (btnId == BTN_ICON) btnIcon = btn;
        else if (btnId == BTN_ANIMATE) btnAnimate = btn;
        else if (btnId == BTN_PARTICLE) btnParticle = btn;
        else if (btnId == BTN_EFFECT) btnEffect = btn;
    }

    private void addNumberRow(IGuiCanvas canvas, int y, int w, String label, String value, int btnDownId, int btnUpId,
        int idx) {
        PanelTextBox lbl = new PanelTextBox(new GuiRectangle(0, y + 4, w / 2 - 4, 12, 0), label).setAlignment(2)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(lbl);

        PanelTextBox val = new PanelTextBox(new GuiRectangle(w / 2 + 4, y + 4, w / 2 - 44, 12, 0), value)
            .setAlignment(1)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(val);

        if (idx == 0) txtDuration = val;
        else if (idx == 1) txtFadeIn = val;
        else if (idx == 2) txtFadeOut = val;
        else if (idx == 3) txtIconScale = val;
        else if (idx == 4) txtIconOffset = val;
        else if (idx == 5) txtTitleScale = val;
        else if (idx == 6) txtSubScale = val;
        else if (idx == 7) txtPosX = val;
        else if (idx == 8) txtPosY = val;

        PanelButton btnDown = new PanelButton(new GuiRectangle(w - 36, y, 16, 16, 0), btnDownId, "-");
        canvas.addPanel(btnDown);

        PanelButton btnUp = new PanelButton(new GuiRectangle(w - 16, y, 16, 16, 0), btnUpId, "+");
        canvas.addPanel(btnUp);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    private void onButtonPress(PEventButton event) {
        int id = event.getButton()
            .getButtonID();

        switch (id) {
            case BTN_STYLE:
                cycleStyle();
                btnStyle.setText(getStyleLabel());
                break;
            case BTN_ICON:
                BQ_Settings.showNotificationIcon = !BQ_Settings.showNotificationIcon;
                btnIcon.setText(getIconLabel());
                break;
            case BTN_ANIMATE:
                cycleAnimation();
                btnAnimate.setText(getAnimateLabel());
                break;
            case BTN_PARTICLE:
                cycleParticle();
                btnParticle.setText(getParticleLabel());
                break;
            case BTN_EFFECT:
                cycleEffect();
                btnEffect.setText(getEffectLabel());
                break;
            case BTN_DUR_DOWN:
                BQ_Settings.notificationDuration = Math.max(1.0f, BQ_Settings.notificationDuration - 0.5f);
                txtDuration.setText(formatSeconds(BQ_Settings.notificationDuration));
                updateStatus();
                break;
            case BTN_DUR_UP:
                BQ_Settings.notificationDuration = Math.min(15.0f, BQ_Settings.notificationDuration + 0.5f);
                txtDuration.setText(formatSeconds(BQ_Settings.notificationDuration));
                updateStatus();
                break;
            case BTN_FADE_IN_DOWN:
                BQ_Settings.notificationFadeIn = Math.max(0.0f, BQ_Settings.notificationFadeIn - 0.5f);
                txtFadeIn.setText(formatSeconds(BQ_Settings.notificationFadeIn));
                updateStatus();
                break;
            case BTN_FADE_IN_UP:
                BQ_Settings.notificationFadeIn = Math.min(5.0f, BQ_Settings.notificationFadeIn + 0.5f);
                txtFadeIn.setText(formatSeconds(BQ_Settings.notificationFadeIn));
                updateStatus();
                break;
            case BTN_FADE_OUT_DOWN:
                BQ_Settings.notificationFadeOut = Math.max(0.0f, BQ_Settings.notificationFadeOut - 0.5f);
                txtFadeOut.setText(formatSeconds(BQ_Settings.notificationFadeOut));
                updateStatus();
                break;
            case BTN_FADE_OUT_UP:
                BQ_Settings.notificationFadeOut = Math.min(5.0f, BQ_Settings.notificationFadeOut + 0.5f);
                txtFadeOut.setText(formatSeconds(BQ_Settings.notificationFadeOut));
                updateStatus();
                break;
            case BTN_ICON_SCALE_DOWN:
                BQ_Settings.notificationIconScale = Math.max(0.5f, BQ_Settings.notificationIconScale - 0.5f);
                txtIconScale.setText(formatScale(BQ_Settings.notificationIconScale));
                break;
            case BTN_ICON_SCALE_UP:
                BQ_Settings.notificationIconScale = Math.min(8.0f, BQ_Settings.notificationIconScale + 0.5f);
                txtIconScale.setText(formatScale(BQ_Settings.notificationIconScale));
                break;
            case BTN_ICON_OFFSET_DOWN:
                BQ_Settings.notificationIconOffsetY = Math.max(-100, BQ_Settings.notificationIconOffsetY - 5);
                txtIconOffset.setText(String.valueOf(BQ_Settings.notificationIconOffsetY));
                break;
            case BTN_ICON_OFFSET_UP:
                BQ_Settings.notificationIconOffsetY = Math.min(100, BQ_Settings.notificationIconOffsetY + 5);
                txtIconOffset.setText(String.valueOf(BQ_Settings.notificationIconOffsetY));
                break;
            case BTN_TITLE_SCALE_DOWN:
                BQ_Settings.notificationTitleScale = BQ_Settings.notificationTitleScale <= 0.5f ? 0
                    : BQ_Settings.notificationTitleScale - 0.5f;
                txtTitleScale.setText(formatScaleOrAuto(BQ_Settings.notificationTitleScale));
                break;
            case BTN_TITLE_SCALE_UP:
                BQ_Settings.notificationTitleScale = BQ_Settings.notificationTitleScale <= 0 ? 0.5f
                    : Math.min(8.0f, BQ_Settings.notificationTitleScale + 0.5f);
                txtTitleScale.setText(formatScaleOrAuto(BQ_Settings.notificationTitleScale));
                break;
            case BTN_SUB_SCALE_DOWN:
                BQ_Settings.notificationSubtitleScale = BQ_Settings.notificationSubtitleScale <= 0.5f ? 0
                    : BQ_Settings.notificationSubtitleScale - 0.5f;
                txtSubScale.setText(formatScaleOrAuto(BQ_Settings.notificationSubtitleScale));
                break;
            case BTN_SUB_SCALE_UP:
                BQ_Settings.notificationSubtitleScale = BQ_Settings.notificationSubtitleScale <= 0 ? 0.5f
                    : Math.min(8.0f, BQ_Settings.notificationSubtitleScale + 0.5f);
                txtSubScale.setText(formatScaleOrAuto(BQ_Settings.notificationSubtitleScale));
                break;
            case BTN_POS_X_DOWN:
                BQ_Settings.notificationTitleOffsetX = Math.max(-1000, BQ_Settings.notificationTitleOffsetX - 5);
                txtPosX.setText(String.valueOf(BQ_Settings.notificationTitleOffsetX));
                break;
            case BTN_POS_X_UP:
                BQ_Settings.notificationTitleOffsetX = Math.min(1000, BQ_Settings.notificationTitleOffsetX + 5);
                txtPosX.setText(String.valueOf(BQ_Settings.notificationTitleOffsetX));
                break;
            case BTN_POS_Y_DOWN:
                BQ_Settings.notificationTitleOffsetY = Math.max(-1000, BQ_Settings.notificationTitleOffsetY - 5);
                txtPosY.setText(String.valueOf(BQ_Settings.notificationTitleOffsetY));
                break;
            case BTN_POS_Y_UP:
                BQ_Settings.notificationTitleOffsetY = Math.min(1000, BQ_Settings.notificationTitleOffsetY + 5);
                txtPosY.setText(String.valueOf(BQ_Settings.notificationTitleOffsetY));
                break;
            case BTN_RESET:
                resetDefaults();
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiNotificationSettings(parent));
                break;
            case BTN_PREVIEW:
                saveConfig();
                QuestNotification.setPendingScreen(new GuiNotificationSettings(parent));
                QuestNotification.ScheduleNotice(
                    "betterquesting.notice.complete",
                    "Test Quest",
                    new ItemStack(Items.diamond),
                    NativeProps.SOUND_COMPLETE.getDefault());
                if (!"off".equals(BQ_Settings.notificationStyle)) {
                    Minecraft.getMinecraft()
                        .displayGuiScreen(null);
                }
                break;
            case BTN_DONE:
                saveConfig();
                displayParent();
                break;
        }
    }

    private void cycleStyle() {
        switch (BQ_Settings.notificationStyle) {
            case "title":
                BQ_Settings.notificationStyle = "classic";
                break;
            case "classic":
                BQ_Settings.notificationStyle = "off";
                break;
            default:
                BQ_Settings.notificationStyle = "title";
                break;
        }
        BQ_Settings.questNotices = !"off".equals(BQ_Settings.notificationStyle);
    }

    private String getStyleLabel() {
        switch (BQ_Settings.notificationStyle) {
            case "title":
                return QuestTranslation.translate("betterquesting.notification.style.title");
            case "classic":
                return QuestTranslation.translate("betterquesting.notification.style.classic");
            default:
                return QuestTranslation.translate("betterquesting.notification.style.off");
        }
    }

    private String getIconLabel() {
        return BQ_Settings.showNotificationIcon ? QuestTranslation.translate("betterquesting.notification.icon.show")
            : QuestTranslation.translate("betterquesting.notification.icon.hide");
    }

    // Shared with the per-quest editor. "default" is prepended there; not part of the player cycle.
    static final String[] ANIM_ORDER = { "none", "fly_in", "rise", "slide", "zoom", "pop", "spin", "spin_reverse",
        "bounce", "wobble", "swing", "slam", "tada" };

    static String nextAnim(String cur) {
        for (int i = 0; i < ANIM_ORDER.length; i++) {
            if (ANIM_ORDER[i].equals(cur)) return ANIM_ORDER[(i + 1) % ANIM_ORDER.length];
        }
        return ANIM_ORDER[0];
    }

    public static String animLabel(String key) {
        switch (key) {
            case "fly_in":
                return QuestTranslation.translate("betterquesting.notification.animate.flyin");
            case "rise":
                return QuestTranslation.translate("betterquesting.notification.animate.rise");
            case "slide":
                return QuestTranslation.translate("betterquesting.notification.animate.slide");
            case "zoom":
                return QuestTranslation.translate("betterquesting.notification.animate.zoom");
            case "pop":
                return QuestTranslation.translate("betterquesting.notification.animate.pop");
            case "spin":
                return QuestTranslation.translate("betterquesting.notification.animate.spin");
            case "spin_reverse":
                return QuestTranslation.translate("betterquesting.notification.animate.spinrev");
            case "bounce":
                return QuestTranslation.translate("betterquesting.notification.animate.bounce");
            case "wobble":
                return QuestTranslation.translate("betterquesting.notification.animate.wobble");
            case "swing":
                return QuestTranslation.translate("betterquesting.notification.animate.swing");
            case "slam":
                return QuestTranslation.translate("betterquesting.notification.animate.slam");
            case "tada":
                return QuestTranslation.translate("betterquesting.notification.animate.tada");
            default:
                return QuestTranslation.translate("betterquesting.notification.animate.none");
        }
    }

    private void cycleAnimation() {
        BQ_Settings.notificationIconAnimation = nextAnim(BQ_Settings.notificationIconAnimation);
    }

    private String getAnimateLabel() {
        return animLabel(BQ_Settings.notificationIconAnimation);
    }

    public static String effectLabel(int tier) {
        switch (tier) {
            case 3:
                return QuestTranslation.translate("betterquesting.notification.effect.hyperspace");
            case 4:
                return QuestTranslation.translate("betterquesting.notification.effect.singularity");
            case 5:
                return QuestTranslation.translate("betterquesting.notification.effect.warp");
            case 6:
                return QuestTranslation.translate("betterquesting.notification.effect.lightspeed");
            default:
                return QuestTranslation.translate("betterquesting.notification.effect.none");
        }
    }

    private void cycleEffect() {
        int t = (BQ_Settings.notificationEffectTier + 1) % 7;
        if (t == 1 || t == 2) t = 3; // spark/burst removed
        BQ_Settings.notificationEffectTier = t;
    }

    private String getEffectLabel() {
        return effectLabel(BQ_Settings.notificationEffectTier);
    }

    private void cycleParticle() {
        switch (BQ_Settings.notificationParticle) {
            case "none":
                BQ_Settings.notificationParticle = "confetti";
                break;
            case "confetti":
                BQ_Settings.notificationParticle = "sparkle";
                break;
            case "sparkle":
                BQ_Settings.notificationParticle = "firework";
                break;
            case "firework":
                BQ_Settings.notificationParticle = "item_confetti";
                break;
            default:
                BQ_Settings.notificationParticle = "none";
                break;
        }
    }

    private String getParticleLabel() {
        switch (BQ_Settings.notificationParticle) {
            case "confetti":
                return QuestTranslation.translate("betterquesting.notification.particle.confetti");
            case "sparkle":
                return QuestTranslation.translate("betterquesting.notification.particle.sparkle");
            case "firework":
                return QuestTranslation.translate("betterquesting.notification.particle.firework");
            case "item_confetti":
                return QuestTranslation.translate("betterquesting.notification.particle.item");
            default:
                return QuestTranslation.translate("betterquesting.notification.particle.none");
        }
    }

    private static String formatSeconds(float seconds) {
        return String.format("%.1fs", seconds);
    }

    private static String formatScale(float scale) {
        return String.format("%.1fx", scale);
    }

    private static String formatScaleOrAuto(float scale) {
        if (scale <= 0) return QuestTranslation.translate("betterquesting.notification.scale.auto");
        return String.format("%.1fx", scale);
    }

    private static float getStayTime() {
        return BQ_Settings.notificationDuration - BQ_Settings.notificationFadeIn - BQ_Settings.notificationFadeOut;
    }

    private String getStatusText() {
        float stay = getStayTime();
        if (stay < 0) {
            return QuestTranslation.translate("betterquesting.notification.status.overlap");
        } else if (stay < 0.05f) {
            return QuestTranslation.translate("betterquesting.notification.status.nostay");
        }
        return QuestTranslation.translate("betterquesting.notification.status.ok", formatSeconds(stay));
    }

    private void updateStatusColor() {
        float stay = getStayTime();
        if (stay < 0.05f) {
            txtStatus.setColor(new GuiColorStatic(0xFFFF5555));
        } else if (stay < 1.0f) {
            txtStatus.setColor(new GuiColorStatic(0xFFFFAA00));
        } else {
            txtStatus.setColor(PresetColor.TEXT_MAIN.getColor());
        }
    }

    private void updateStatus() {
        txtStatus.setText(getStatusText());
        updateStatusColor();
    }

    private void resetDefaults() {
        BQ_Settings.notificationStyle = "title";
        BQ_Settings.questNotices = true;
        BQ_Settings.showNotificationIcon = true;
        BQ_Settings.notificationIconAnimation = "none";
        BQ_Settings.notificationParticle = "none";
        BQ_Settings.notificationTitleScale = 0;
        BQ_Settings.notificationSubtitleScale = 0;
        BQ_Settings.notificationDuration = 4.5f;
        BQ_Settings.notificationFadeIn = 0.5f;
        BQ_Settings.notificationFadeOut = 1.0f;
        BQ_Settings.notificationIconScale = 4.0f;
        BQ_Settings.notificationIconOffsetY = -25;
        BQ_Settings.notificationTitleOffsetX = 0;
        BQ_Settings.notificationTitleOffsetY = 0;
        BQ_Settings.notificationEffectTier = 0;
        saveConfig();
    }

    private void saveConfig() {
        if (ConfigHandler.config == null) return;
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Style", "title")
            .set(BQ_Settings.notificationStyle);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Quest Notices", true)
            .set(BQ_Settings.questNotices);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Show Notification Icon", true)
            .set(BQ_Settings.showNotificationIcon);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Duration", 4.5)
            .set(BQ_Settings.notificationDuration);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Fade In", 0.5)
            .set(BQ_Settings.notificationFadeIn);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Fade Out", 1.0)
            .set(BQ_Settings.notificationFadeOut);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Icon Scale", 4.0)
            .set(BQ_Settings.notificationIconScale);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Icon Offset Y", 0)
            .set(BQ_Settings.notificationIconOffsetY);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Title Scale", 0.0)
            .set(BQ_Settings.notificationTitleScale);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Subtitle Scale", 0.0)
            .set(BQ_Settings.notificationSubtitleScale);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Icon Animation", "none")
            .set(BQ_Settings.notificationIconAnimation);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Particle", "none")
            .set(BQ_Settings.notificationParticle);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Title Offset X", 0)
            .set(BQ_Settings.notificationTitleOffsetX);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Title Offset Y", 0)
            .set(BQ_Settings.notificationTitleOffsetY);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Effect Tier", 0)
            .set(BQ_Settings.notificationEffectTier);
        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Notification Hint Seen", false)
            .set(BQ_Settings.notificationHintSeen);
        ConfigHandler.config.save();
    }

    @Override
    public void onGuiClosed() {
        saveConfig();
        super.onGuiClosed();
    }
}
