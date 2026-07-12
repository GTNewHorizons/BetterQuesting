package betterquesting.client.gui2.editors;

import java.util.Collections;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.lwjgl.util.vector.Vector4f;

import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
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
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.QuestNotification;
import betterquesting.client.gui2.GuiNotificationSettings;
import betterquesting.client.gui2.editors.nbt.GuiItemSelection;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.network.handlers.NoticeConfig;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Per-quest notification editor. Mirrors the player {@code GuiNotificationSettings}, but every control
 * carries a "Default" state (= fall back to the player setting) and writes to the quest's properties.
 */
@SideOnly(Side.CLIENT)
public class GuiQuestNotificationSettings extends GuiScreenCanvas implements IPEventListener {

    private static final int BTN_STYLE = 1;
    private static final int BTN_ICON = 2;
    private static final int BTN_ANIMATE = 3;
    private static final int BTN_PARTICLE = 4;
    private static final int BTN_TITLE_TEXT = 5;
    private static final int BTN_SUB_TEXT = 6;
    private static final int BTN_CONFETTI = 7;
    private static final int BTN_TITLE_SCALE_DOWN = 10;
    private static final int BTN_TITLE_SCALE_UP = 11;
    private static final int BTN_SUB_SCALE_DOWN = 12;
    private static final int BTN_SUB_SCALE_UP = 13;
    private static final int BTN_DUR_DOWN = 14;
    private static final int BTN_DUR_UP = 15;
    private static final int BTN_FADE_IN_DOWN = 16;
    private static final int BTN_FADE_IN_UP = 17;
    private static final int BTN_FADE_OUT_DOWN = 18;
    private static final int BTN_FADE_OUT_UP = 19;
    private static final int BTN_ICON_SCALE_DOWN = 20;
    private static final int BTN_ICON_SCALE_UP = 21;
    private static final int BTN_ICON_OFFSET_DOWN = 22;
    private static final int BTN_ICON_OFFSET_UP = 23;
    private static final int BTN_POS_X_DOWN = 24;
    private static final int BTN_POS_X_UP = 25;
    private static final int BTN_POS_Y_DOWN = 26;
    private static final int BTN_POS_Y_UP = 27;
    private static final int BTN_COUNT_DOWN = 28;
    private static final int BTN_COUNT_UP = 29;
    private static final int BTN_RESET = 30;
    private static final int BTN_PREVIEW = 31;
    private static final int BTN_DONE = 32;
    private static final int BTN_EFFECT = 33;

    private final UUID questID;
    private final IQuest quest;

    private String style;
    private String showIcon;
    private String animation;
    private String particle;
    private String titleText;
    private String subtitleText;
    private BigItemStack confetti;
    private float titleScale;
    private float subtitleScale;
    private float iconScale;
    private float duration;
    private float fadeIn;
    private float fadeOut;
    private int iconOffsetY;
    private int posX;
    private int posY;
    private int particleCount;
    private int effectTier;

    private PanelButton btnStyle, btnIcon, btnAnimate, btnParticle, btnEffect, btnTitleText, btnSubText, btnConfetti;
    private PanelTextBox txtTitleScale, txtSubScale, txtIconScale, txtDuration, txtFadeIn, txtFadeOut, txtIconOffset,
        txtPosX, txtPosY, txtCount;

    public GuiQuestNotificationSettings(GuiScreen parent, UUID questID, IQuest quest) {
        super(parent);
        this.questID = questID;
        this.quest = quest;
        load();
    }

    private void load() {
        style = quest.getProperty(NativeProps.NOTIFICATION_STYLE);
        showIcon = quest.getProperty(NativeProps.NOTIFICATION_SHOW_ICON);
        animation = quest.getProperty(NativeProps.COMPLETION_ANIMATION);
        particle = quest.getProperty(NativeProps.COMPLETION_PARTICLE);
        titleText = quest.getProperty(NativeProps.NOTIFICATION_TITLE);
        subtitleText = quest.getProperty(NativeProps.NOTIFICATION_SUBTITLE);
        confetti = quest.getProperty(NativeProps.CONFETTI_ICON);
        titleScale = quest.getProperty(NativeProps.NOTIFICATION_TITLE_SCALE);
        subtitleScale = quest.getProperty(NativeProps.NOTIFICATION_SUBTITLE_SCALE);
        iconScale = quest.getProperty(NativeProps.NOTIFICATION_ICON_SCALE);
        duration = quest.getProperty(NativeProps.NOTIFICATION_DURATION);
        fadeIn = quest.getProperty(NativeProps.NOTIFICATION_FADE_IN);
        fadeOut = quest.getProperty(NativeProps.NOTIFICATION_FADE_OUT);
        iconOffsetY = quest.getProperty(NativeProps.NOTIFICATION_ICON_OFFSET_Y);
        posX = quest.getProperty(NativeProps.NOTIFICATION_POS_X);
        posY = quest.getProperty(NativeProps.NOTIFICATION_POS_Y);
        particleCount = quest.getProperty(NativeProps.PARTICLE_COUNT);
        effectTier = quest.getProperty(NativeProps.NOTIFICATION_EFFECT);
    }

    private NoticeConfig buildConfig() {
        NoticeConfig c = new NoticeConfig();
        c.style = style;
        c.showIcon = showIcon;
        c.animation = animation;
        c.particle = particle;
        c.subtitleText = subtitleText;
        c.duration = duration;
        c.fadeIn = fadeIn;
        c.fadeOut = fadeOut;
        c.titleScale = titleScale;
        c.subtitleScale = subtitleScale;
        c.iconScale = iconScale;
        c.iconOffsetY = iconOffsetY;
        c.posX = posX;
        c.posY = posY;
        c.particleCount = particleCount;
        c.effectTier = effectTier;
        ItemStack conf = confetti != null ? confetti.getBaseStack() : null;
        c.confettiIcon = (conf != null && conf.getItem() != Items.stick) ? conf : null;
        return c;
    }

    private void apply() {
        quest.setProperty(NativeProps.NOTIFICATION_STYLE, style);
        quest.setProperty(NativeProps.NOTIFICATION_SHOW_ICON, showIcon);
        quest.setProperty(NativeProps.COMPLETION_ANIMATION, animation);
        quest.setProperty(NativeProps.COMPLETION_PARTICLE, particle);
        quest.setProperty(NativeProps.NOTIFICATION_TITLE, titleText);
        quest.setProperty(NativeProps.NOTIFICATION_SUBTITLE, subtitleText);
        quest.setProperty(NativeProps.CONFETTI_ICON, confetti);
        quest.setProperty(NativeProps.NOTIFICATION_TITLE_SCALE, titleScale);
        quest.setProperty(NativeProps.NOTIFICATION_SUBTITLE_SCALE, subtitleScale);
        quest.setProperty(NativeProps.NOTIFICATION_ICON_SCALE, iconScale);
        quest.setProperty(NativeProps.NOTIFICATION_DURATION, duration);
        quest.setProperty(NativeProps.NOTIFICATION_FADE_IN, fadeIn);
        quest.setProperty(NativeProps.NOTIFICATION_FADE_OUT, fadeOut);
        quest.setProperty(NativeProps.NOTIFICATION_ICON_OFFSET_Y, iconOffsetY);
        quest.setProperty(NativeProps.NOTIFICATION_POS_X, posX);
        quest.setProperty(NativeProps.NOTIFICATION_POS_Y, posY);
        quest.setProperty(NativeProps.PARTICLE_COUNT, particleCount);
        quest.setProperty(NativeProps.NOTIFICATION_EFFECT, effectTier);
        NetQuestEdit.requestEdit(Collections.singletonMap(questID, quest));
    }

    private void preview() {
        apply();
        NoticeConfig cfg = buildConfig();
        if (!QuestNotification.wouldShow(cfg)) return;
        BigItemStack ico = quest.getProperty(NativeProps.ICON);
        String main = (titleText == null || titleText.isEmpty()) ? "betterquesting.notice.complete" : titleText;
        QuestNotification.setPendingScreen(new GuiQuestNotificationSettings(parent, questID, quest));
        QuestNotification.previewNotice(
            main,
            quest.getProperty(NativeProps.NAME),
            ico != null ? ico.getBaseStack() : null,
            quest.getProperty(NativeProps.SOUND_COMPLETE),
            cfg);
        Minecraft.getMinecraft()
            .displayGuiScreen(null);
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
            QuestTranslation.translate("betterquesting.notification.quest.title")).setAlignment(1)
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

        btnStyle = addCycle(scrollCan, y, centerW, "betterquesting.notification.style", BTN_STYLE, styleLabel());
        y += rowH;
        btnIcon = addCycle(scrollCan, y, centerW, "betterquesting.notification.showicon", BTN_ICON, iconLabel());
        y += rowH;
        btnAnimate = addCycle(scrollCan, y, centerW, "betterquesting.notification.animate", BTN_ANIMATE, animLabel());
        y += rowH;
        btnParticle = addCycle(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.particle",
            BTN_PARTICLE,
            particleLabel());
        y += rowH;
        btnEffect = addCycle(scrollCan, y, centerW, "betterquesting.notification.effect", BTN_EFFECT, effLabel());
        y += rowH;
        btnTitleText = addCycle(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.titletext",
            BTN_TITLE_TEXT,
            textLabel(titleText));
        y += rowH;
        btnSubText = addCycle(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.subtitletext",
            BTN_SUB_TEXT,
            textLabel(subtitleText));
        y += rowH;
        btnConfetti = addCycle(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.particle.item",
            BTN_CONFETTI,
            QuestTranslation.translate("betterquesting.btn.edit"));
        y += rowH;

        txtTitleScale = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.titlescale",
            scaleLabel(titleScale),
            BTN_TITLE_SCALE_DOWN,
            BTN_TITLE_SCALE_UP);
        y += rowH;
        txtSubScale = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.subtitlescale",
            scaleLabel(subtitleScale),
            BTN_SUB_SCALE_DOWN,
            BTN_SUB_SCALE_UP);
        y += rowH;
        txtDuration = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.duration",
            secLabel(duration),
            BTN_DUR_DOWN,
            BTN_DUR_UP);
        y += rowH;
        txtFadeIn = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.fadein",
            secLabel(fadeIn),
            BTN_FADE_IN_DOWN,
            BTN_FADE_IN_UP);
        y += rowH;
        txtFadeOut = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.fadeout",
            secLabel(fadeOut),
            BTN_FADE_OUT_DOWN,
            BTN_FADE_OUT_UP);
        y += rowH;
        txtIconScale = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.iconscale",
            scaleLabel(iconScale),
            BTN_ICON_SCALE_DOWN,
            BTN_ICON_SCALE_UP);
        y += rowH;
        txtIconOffset = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.iconoffset",
            intLabel(iconOffsetY, Integer.MIN_VALUE),
            BTN_ICON_OFFSET_DOWN,
            BTN_ICON_OFFSET_UP);
        y += rowH;
        txtPosX = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.posx",
            intLabel(posX, Integer.MIN_VALUE),
            BTN_POS_X_DOWN,
            BTN_POS_X_UP);
        y += rowH;
        txtPosY = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.posy",
            intLabel(posY, Integer.MIN_VALUE),
            BTN_POS_Y_DOWN,
            BTN_POS_Y_UP);
        y += rowH;
        txtCount = addNumber(
            scrollCan,
            y,
            centerW,
            "betterquesting.notification.particlecount",
            intLabel(particleCount, -1),
            BTN_COUNT_DOWN,
            BTN_COUNT_UP);

        int btnW = (centerW - 8) / 3;
        inCan.addPanel(
            new PanelButton(
                new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2, -16, btnW, 16, 0),
                BTN_RESET,
                QuestTranslation.translate("betterquesting.notification.reset")));
        inCan.addPanel(
            new PanelButton(
                new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2 + btnW + 4, -16, btnW, 16, 0),
                BTN_PREVIEW,
                QuestTranslation.translate("betterquesting.notification.preview")));
        inCan.addPanel(
            new PanelButton(
                new GuiTransform(new Vector4f(left, 1F, right, 1F), -centerW / 2 + (btnW + 4) * 2, -16, btnW, 16, 0),
                BTN_DONE,
                QuestTranslation.translate("betterquesting.notification.done")));
    }

    private PanelButton addCycle(IGuiCanvas canvas, int y, int w, String labelKey, int btnId, String btnLabel) {
        PanelTextBox lbl = new PanelTextBox(
            new GuiRectangle(0, y + 4, w / 2 - 4, 12, 0),
            QuestTranslation.translate(labelKey)).setAlignment(2)
                .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(lbl);
        PanelButton btn = new PanelButton(new GuiRectangle(w / 2 + 4, y, w / 2 - 4, 16, 0), btnId, btnLabel);
        canvas.addPanel(btn);
        return btn;
    }

    private PanelTextBox addNumber(IGuiCanvas canvas, int y, int w, String labelKey, String value, int down, int up) {
        PanelTextBox lbl = new PanelTextBox(
            new GuiRectangle(0, y + 4, w / 2 - 4, 12, 0),
            QuestTranslation.translate(labelKey)).setAlignment(2)
                .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(lbl);
        PanelTextBox val = new PanelTextBox(new GuiRectangle(w / 2 + 4, y + 4, w / 2 - 44, 12, 0), value)
            .setAlignment(1)
            .setColor(PresetColor.TEXT_MAIN.getColor());
        canvas.addPanel(val);
        canvas.addPanel(new PanelButton(new GuiRectangle(w - 36, y, 16, 16, 0), down, "-"));
        canvas.addPanel(new PanelButton(new GuiRectangle(w - 16, y, 16, 16, 0), up, "+"));
        return val;
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) onButtonPress((PEventButton) event);
    }

    private void onButtonPress(PEventButton event) {
        int id = event.getButton()
            .getButtonID();
        switch (id) {
            case BTN_STYLE:
                style = cycle(style, "default", "title", "classic", "off");
                btnStyle.setText(styleLabel());
                apply();
                break;
            case BTN_ICON:
                showIcon = cycle(showIcon, "default", "yes", "no");
                btnIcon.setText(iconLabel());
                apply();
                break;
            case BTN_ANIMATE:
                animation = cycle(
                    animation,
                    "default",
                    "none",
                    "fly_in",
                    "rise",
                    "slide",
                    "zoom",
                    "pop",
                    "spin",
                    "spin_reverse",
                    "bounce",
                    "wobble",
                    "swing",
                    "slam",
                    "tada");
                btnAnimate.setText(animLabel());
                apply();
                break;
            case BTN_PARTICLE:
                particle = cycle(particle, "default", "none", "confetti", "sparkle", "firework", "item_confetti");
                btnParticle.setText(particleLabel());
                apply();
                break;
            case BTN_EFFECT:
                effectTier = effectTier >= 6 ? -1 : effectTier + 1;
                if (effectTier == 1 || effectTier == 2) effectTier = 3; // spark/burst removed
                btnEffect.setText(effLabel());
                apply();
                break;
            case BTN_TITLE_TEXT:
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiTextEditor(this, titleText, false, value -> {
                        titleText = value == null ? "" : value;
                        apply();
                    }));
                break;
            case BTN_SUB_TEXT:
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiTextEditor(this, subtitleText, false, value -> {
                        subtitleText = value == null ? "" : value;
                        apply();
                    }));
                break;
            case BTN_CONFETTI:
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiItemSelection(this, confetti, value -> {
                        confetti = value;
                        apply();
                    }));
                break;
            case BTN_TITLE_SCALE_DOWN:
                titleScale = stepF(titleScale, false, 0.5f, 8.0f, 0.5f, 0.5f);
                txtTitleScale.setText(scaleLabel(titleScale));
                apply();
                break;
            case BTN_TITLE_SCALE_UP:
                titleScale = stepF(titleScale, true, 0.5f, 8.0f, 0.5f, 0.5f);
                txtTitleScale.setText(scaleLabel(titleScale));
                apply();
                break;
            case BTN_SUB_SCALE_DOWN:
                subtitleScale = stepF(subtitleScale, false, 0.5f, 8.0f, 0.5f, 0.5f);
                txtSubScale.setText(scaleLabel(subtitleScale));
                apply();
                break;
            case BTN_SUB_SCALE_UP:
                subtitleScale = stepF(subtitleScale, true, 0.5f, 8.0f, 0.5f, 0.5f);
                txtSubScale.setText(scaleLabel(subtitleScale));
                apply();
                break;
            case BTN_DUR_DOWN:
                duration = stepF(duration, false, 1.0f, 15.0f, 0.5f, 1.0f);
                txtDuration.setText(secLabel(duration));
                apply();
                break;
            case BTN_DUR_UP:
                duration = stepF(duration, true, 1.0f, 15.0f, 0.5f, 1.0f);
                txtDuration.setText(secLabel(duration));
                apply();
                break;
            case BTN_FADE_IN_DOWN:
                fadeIn = stepF(fadeIn, false, 0.0f, 5.0f, 0.5f, 0.0f);
                txtFadeIn.setText(secLabel(fadeIn));
                apply();
                break;
            case BTN_FADE_IN_UP:
                fadeIn = stepF(fadeIn, true, 0.0f, 5.0f, 0.5f, 0.0f);
                txtFadeIn.setText(secLabel(fadeIn));
                apply();
                break;
            case BTN_FADE_OUT_DOWN:
                fadeOut = stepF(fadeOut, false, 0.0f, 5.0f, 0.5f, 0.0f);
                txtFadeOut.setText(secLabel(fadeOut));
                apply();
                break;
            case BTN_FADE_OUT_UP:
                fadeOut = stepF(fadeOut, true, 0.0f, 5.0f, 0.5f, 0.0f);
                txtFadeOut.setText(secLabel(fadeOut));
                apply();
                break;
            case BTN_ICON_SCALE_DOWN:
                iconScale = stepF(iconScale, false, 0.5f, 8.0f, 0.5f, 0.5f);
                txtIconScale.setText(scaleLabel(iconScale));
                apply();
                break;
            case BTN_ICON_SCALE_UP:
                iconScale = stepF(iconScale, true, 0.5f, 8.0f, 0.5f, 0.5f);
                txtIconScale.setText(scaleLabel(iconScale));
                apply();
                break;
            case BTN_ICON_OFFSET_DOWN:
                iconOffsetY = stepI(iconOffsetY, false, -100, 100, 5, 0, Integer.MIN_VALUE);
                txtIconOffset.setText(intLabel(iconOffsetY, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_ICON_OFFSET_UP:
                iconOffsetY = stepI(iconOffsetY, true, -100, 100, 5, 0, Integer.MIN_VALUE);
                txtIconOffset.setText(intLabel(iconOffsetY, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_POS_X_DOWN:
                posX = stepI(posX, false, -1000, 1000, 5, 0, Integer.MIN_VALUE);
                txtPosX.setText(intLabel(posX, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_POS_X_UP:
                posX = stepI(posX, true, -1000, 1000, 5, 0, Integer.MIN_VALUE);
                txtPosX.setText(intLabel(posX, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_POS_Y_DOWN:
                posY = stepI(posY, false, -1000, 1000, 5, 0, Integer.MIN_VALUE);
                txtPosY.setText(intLabel(posY, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_POS_Y_UP:
                posY = stepI(posY, true, -1000, 1000, 5, 0, Integer.MIN_VALUE);
                txtPosY.setText(intLabel(posY, Integer.MIN_VALUE));
                apply();
                break;
            case BTN_COUNT_DOWN:
                particleCount = stepI(particleCount, false, 0, 500, 5, 0, -1);
                txtCount.setText(intLabel(particleCount, -1));
                apply();
                break;
            case BTN_COUNT_UP:
                particleCount = stepI(particleCount, true, 0, 500, 5, 0, -1);
                txtCount.setText(intLabel(particleCount, -1));
                apply();
                break;
            case BTN_RESET:
                resetAll();
                apply();
                Minecraft.getMinecraft()
                    .displayGuiScreen(new GuiQuestNotificationSettings(parent, questID, quest));
                break;
            case BTN_PREVIEW:
                preview();
                break;
            case BTN_DONE:
                apply();
                displayParent();
                break;
        }
    }

    private void resetAll() {
        style = "default";
        showIcon = "default";
        animation = "default";
        particle = "default";
        titleText = "";
        subtitleText = "";
        confetti = NativeProps.CONFETTI_ICON.getDefault();
        titleScale = -1f;
        subtitleScale = -1f;
        iconScale = -1f;
        duration = -1f;
        fadeIn = -1f;
        fadeOut = -1f;
        iconOffsetY = Integer.MIN_VALUE;
        posX = Integer.MIN_VALUE;
        posY = Integer.MIN_VALUE;
        particleCount = -1;
        effectTier = -1;
    }

    // ---- stepper: a value below min or above max snaps to the sentinel ("Default"); from sentinel, enter at base ----
    private static float stepF(float cur, boolean up, float min, float max, float step, float base) {
        if (cur < 0) return base;
        if (up) {
            float v = cur + step;
            return v > max ? -1f : v;
        }
        float v = cur - step;
        return v < min ? -1f : v;
    }

    private static int stepI(int cur, boolean up, int min, int max, int step, int base, int sentinel) {
        if (cur == sentinel) return base;
        if (up) {
            int v = cur + step;
            return v > max ? sentinel : v;
        }
        int v = cur - step;
        return v < min ? sentinel : v;
    }

    // ---- labels ----
    private static String cycle(String cur, String... order) {
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(cur)) return order[(i + 1) % order.length];
        }
        return order[0];
    }

    private static String def() {
        return QuestTranslation.translate("betterquesting.notification.default");
    }

    private String styleLabel() {
        switch (style) {
            case "title":
                return QuestTranslation.translate("betterquesting.notification.style.title");
            case "classic":
                return QuestTranslation.translate("betterquesting.notification.style.classic");
            case "off":
                return QuestTranslation.translate("betterquesting.notification.style.off");
            default:
                return def();
        }
    }

    private String iconLabel() {
        switch (showIcon) {
            case "yes":
                return QuestTranslation.translate("betterquesting.notification.icon.show");
            case "no":
                return QuestTranslation.translate("betterquesting.notification.icon.hide");
            default:
                return def();
        }
    }

    private String animLabel() {
        return "default".equals(animation) ? def() : GuiNotificationSettings.animLabel(animation);
    }

    private String effLabel() {
        return effectTier < 0 ? def() : GuiNotificationSettings.effectLabel(effectTier);
    }

    private String particleLabel() {
        switch (particle) {
            case "none":
                return QuestTranslation.translate("betterquesting.notification.particle.none");
            case "confetti":
                return QuestTranslation.translate("betterquesting.notification.particle.confetti");
            case "sparkle":
                return QuestTranslation.translate("betterquesting.notification.particle.sparkle");
            case "firework":
                return QuestTranslation.translate("betterquesting.notification.particle.firework");
            case "item_confetti":
                return QuestTranslation.translate("betterquesting.notification.particle.item");
            default:
                return def();
        }
    }

    private static String textLabel(String value) {
        return value == null || value.isEmpty() ? def() : value;
    }

    private static String scaleLabel(float v) {
        return v < 0 ? def() : String.format("%.1fx", v);
    }

    private static String secLabel(float v) {
        return v < 0 ? def() : String.format("%.1fs", v);
    }

    private static String intLabel(int v, int sentinel) {
        return v == sentinel ? def() : String.valueOf(v);
    }
}
