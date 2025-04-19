package betterquesting.client.gui2;

import static betterquesting.api.storage.BQ_Settings.alwaysDrawImplicit;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.util.vector.Vector4f;

import com.google.common.collect.ImmutableList;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.questing.rewards.IReward;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
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
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasHoverTray;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.popups.PopChoiceExt;
import betterquesting.api2.client.gui.popups.PopContextMenu;
import betterquesting.api2.client.gui.resources.colors.GuiColorPulse;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.OreDictTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.api2.utils.Tuple2;
import betterquesting.client.BookmarkHandler;
import betterquesting.client.gui2.editors.GuiQuestLinesEditor;
import betterquesting.client.gui2.editors.designer.GuiDesigner;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.ConfigHandler;
import betterquesting.network.handlers.NetQuestAction;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import bq_standard.rewards.RewardChoice;

public class GuiQuestLines extends GuiScreenCanvas implements IPEventListener, INeedsRefresh {

    private ScrollPosition scrollPosition;

    public static class ScrollPosition {

        public ScrollPosition(int chapterScrollY) {
            this.chapterScrollY = chapterScrollY;
        }

        private int chapterScrollY;

        public int getChapterScrollY() {
            return chapterScrollY;
        }

        public void setChapterScrollY(int chapterScrollY) {
            this.chapterScrollY = chapterScrollY;
        }
    }

    private IQuestLine selectedLine = null;
    private static UUID selectedLineId = null;

    private final List<Tuple2<Map.Entry<UUID, IQuestLine>, Integer>> visChapters = new ArrayList<>();

    private CanvasQuestLine cvQuest;

    // Keep these separate for now
    private static CanvasHoverTray cvChapterTray;
    private static CanvasHoverTray cvDescTray;
    private static CanvasHoverTray cvFrame;

    private CanvasScrolling cvDesc;
    private PanelVScrollBar scDesc;
    private CanvasScrolling cvLines;
    private PanelVScrollBar scLines;

    private PanelGeneric icoChapter;
    private PanelTextBox txTitle;
    private PanelTextBox txDesc;
    private PanelTextBox completionText;

    private PanelButton claimAll;

    private static boolean trayLock;
    private static boolean viewMode;
    private int questsCompleted = 0;
    private int totalQuests = 0;

    private GuiQuestSearch searchGui;
    private GuiBookmarks bookmarksGui;

    private final List<PanelButtonStorage<Map.Entry<UUID, IQuestLine>>> btnListRef = new ArrayList<>();

    public GuiQuestLines(GuiScreen parent) {
        super(parent);
        trayLock = BQ_Settings.lockTray;
        viewMode = BQ_Settings.viewMode;

        if (scrollPosition == null) {
            scrollPosition = new ScrollPosition(0);
        }
    }

    @Override
    public void refreshGui() {
        refreshChapterVisibility();
        refreshContent();
    }

    @Override
    public void initPanel() {
        super.initPanel();

        GuiHome.bookmark = this;
        // If we come to quests gui - we set skip home to true
        if (!BQ_Settings.skipHome) {
            ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Skip home", false)
                .set(true);
            ConfigHandler.config.save();
            BQ_Settings.skipHome = true;
        }

        if (selectedLineId != null) {
            selectedLine = QuestLineDatabase.INSTANCE.get(selectedLineId);
            if (selectedLine == null) {
                selectedLineId = null;
            }
        } else {
            selectedLine = null;
        }

        boolean canEdit = QuestingAPI.getAPI(ApiReference.SETTINGS)
            .canUserEdit(mc.thePlayer);
        boolean preOpen = false;
        // First time load, if tray locked - let the tray open
        if (trayLock && cvChapterTray == null && cvDescTray == null) preOpen = true;
        if (trayLock && cvChapterTray != null && cvChapterTray.isTrayOpen()) preOpen = true;
        if (trayLock && cvDescTray != null && cvDescTray.isTrayOpen()) preOpen = true;

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);

        CanvasTextured cvBackground = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        PanelButton btnExit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -24, 32, 16, 0), -1, "")
            .setIcon(PresetIcon.ICON_PG_PREV.getTexture());
        btnExit.setClickAction((b) -> displayParent());
        btnExit.setTooltip(Collections.singletonList(QuestTranslation.translate("gui.back")));
        cvBackground.addPanel(btnExit);

        // Search button
        if (this.searchGui == null) this.searchGui = initSearchPanel();
        PanelButton btnSearch = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -40, 32, 16, 0), -1, "")
            .setIcon(PresetIcon.ICON_ZOOM.getTexture());
        btnSearch.setClickAction((button) -> { mc.displayGuiScreen(this.searchGui); });
        btnSearch.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.gui.search")));
        cvBackground.addPanel(btnSearch);

        // Pins button
        if (this.bookmarksGui == null) this.bookmarksGui = initBookmarksPanel();
        PanelButton btnBookmarks = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -56, 32, 16, 0), -1, "")
            .setIcon(PresetIcon.ICON_BOOKMARK.getTexture());
        btnBookmarks.setClickAction((button) -> { mc.displayGuiScreen(this.bookmarksGui); });
        btnBookmarks.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.gui.bookmarks")));
        cvBackground.addPanel(btnBookmarks);

        if (canEdit) {
            PanelButton btnEdit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -72, 16, 16, 0), -1, "")
                .setIcon(PresetIcon.ICON_GEAR.getTexture());
            btnEdit.setClickAction((b) -> mc.displayGuiScreen(new GuiQuestLinesEditor(this)));
            btnEdit.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.edit")));
            cvBackground.addPanel(btnEdit);

            PanelButton btnDesign = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 24, -72, 16, 16, 0), -1, "")
                .setIcon(PresetIcon.ICON_SORT.getTexture());
            btnDesign.setClickAction(
                (b) -> { if (selectedLine != null) mc.displayGuiScreen(new GuiDesigner(this, selectedLine)); });
            btnDesign.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.designer")));
            cvBackground.addPanel(btnDesign);
        }

        txTitle = new PanelTextBox(
            new GuiTransform(new Vector4f(0F, 0F, 0.5F, 0F), new GuiPadding(60, 12, 0, -24), 0),
            "");
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        completionText = new PanelTextBox(
            new GuiTransform(new Vector4f(0F, 0F, 0.5F, 0F), new GuiPadding(214, 12, -154, -24), 0),
            "");
        completionText.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(completionText);

        icoChapter = new PanelGeneric(new GuiTransform(GuiAlign.TOP_LEFT, 40, 8, 16, 16, 0), null);
        cvBackground.addPanel(icoChapter);

        cvFrame = new CanvasHoverTray(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40 + 150 + 24, 24, 8, 8), 0),
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40, 24, 8, 8), 0),
            PresetTexture.AUX_FRAME_0.getTexture());
        cvFrame.setManualOpen(true);
        // CanvasTextured cvFrame = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40, 24, 8, 8),
        // 0), PresetTexture.AUX_FRAME_0.getTexture());
        cvBackground.addPanel(cvFrame);
        cvFrame.setTrayState(!preOpen, 1);
        // These would probably be more annoying than useful if you just wanted to check a tray but not lose your
        // position
        // cvFrame.setOpenAction(() -> cvQuest.fitToWindow());
        // cvFrame.setCloseAction(() -> cvQuest.fitToWindow());

        // === TRAY STATE ===

        boolean chapterTrayOpened = trayLock && cvChapterTray != null && cvChapterTray.isTrayOpen();
        boolean descTrayOpened = trayLock && cvDescTray != null && cvDescTray.isTrayOpen();
        if (preOpen && !chapterTrayOpened && !descTrayOpened) chapterTrayOpened = true;

        // === CHAPTER TRAY ===

        cvChapterTray = new CanvasHoverTray(
            new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -24, 8), -1),
            new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -40 - 150 - 24, 8), -1),
            PresetTexture.PANEL_INNER.getTexture());
        cvChapterTray.setManualOpen(true);
        cvChapterTray.setOpenAction(() -> {
            cvDescTray.setTrayState(false, 200);
            cvFrame.setTrayState(false, 200);
            buildChapterList();
        });
        cvBackground.addPanel(cvChapterTray);

        cvLines = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 16, 8), 0));
        cvChapterTray.getCanvasOpen()
            .addPanel(cvLines);

        scLines = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-16, 8, 8, 8), 0));
        cvLines.setScrollDriverY(scLines);
        cvChapterTray.getCanvasOpen()
            .addPanel(scLines);

        // === DESCRIPTION TRAY ===

        cvDescTray = new CanvasHoverTray(
            new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -24, 8), -1),
            new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -40 - 150 - 24, 8), -1),
            PresetTexture.PANEL_INNER.getTexture());
        cvDescTray.setManualOpen(true);
        cvDescTray.setOpenAction(() -> {
            cvChapterTray.setTrayState(false, 200);
            cvFrame.setTrayState(false, 200);
            cvDesc.resetCanvas();
            if (selectedLine != null) {
                txDesc = new PanelTextBox(
                    new GuiRectangle(
                        0,
                        0,
                        cvDesc.getTransform()
                            .getWidth(),
                        0,
                        0),
                    QuestTranslation.translateQuestLineDescription(selectedLineId, selectedLine),
                    true);
                txDesc.setColor(PresetColor.TEXT_AUX_0.getColor());// .setFontSize(10);
                cvDesc.addCulledPanel(txDesc, false);
                cvDesc.refreshScrollBounds();
                scDesc.setEnabled(
                    cvDesc.getScrollBounds()
                        .getHeight() > 0);
            } else {
                scDesc.setEnabled(false);
            }
        });
        cvBackground.addPanel(cvDescTray);

        cvDesc = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 20, 8), 0));
        cvDescTray.getCanvasOpen()
            .addPanel(cvDesc);

        scDesc = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-16, 8, 8, 8), 0));
        cvDesc.setScrollDriverY(scDesc);
        cvDescTray.getCanvasOpen()
            .addPanel(scDesc);

        // === LEFT SIDEBAR ===
        int yOff = 24;
        PanelButton btnTrayToggle = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, 0), -1, "");
        btnTrayToggle.setIcon(
            PresetIcon.ICON_QUEST.getTexture(),
            selectedLineId == null && !chapterTrayOpened ? new GuiColorPulse(0xFFFFFFFF, 0xFF444444, 2F, 0F)
                : new GuiColorStatic(0xFFFFFFFF),
            0);
        btnTrayToggle.setClickAction((b) -> {
            cvFrame.setTrayState(cvChapterTray.isTrayOpen(), 200);
            cvChapterTray.setTrayState(!cvChapterTray.isTrayOpen(), 200);
            btnTrayToggle.setIcon(PresetIcon.ICON_QUEST.getTexture());
        });
        btnTrayToggle
            .setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.title.quest_lines")));
        cvBackground.addPanel(btnTrayToggle);
        yOff += 16;

        PanelButton btnDescToggle = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, 0), -1, "")
            .setIcon(PresetIcon.ICON_DESC.getTexture());
        btnDescToggle.setClickAction((b) -> {
            cvFrame.setTrayState(cvDescTray.isTrayOpen(), 200);
            cvDescTray.setTrayState(!cvDescTray.isTrayOpen(), 200);
        });
        btnDescToggle
            .setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.gui.description")));
        cvBackground.addPanel(btnDescToggle);
        yOff += 16;

        claimAll = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "");
        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture());
        claimAll.setClickAction((b) -> {
            Pair<List<UUID>, Integer> data = getAllPossibleClaims();
            List<UUID> claimIdList = data != null ? data.getLeft() : null;
            int numChoiceQuests = data != null ? data.getRight() : 0;

            if (BQ_Settings.claimAllConfirmation || isShiftKeyDown()) {
                PopChoiceExt popup = new PopChoiceExt(
                    QuestTranslation.translate("betterquesting.gui.claim_all_warning") + "\n\n"
                        + QuestTranslation.translate("betterquesting.gui.claim_all_confirm")
                        + "\n\n"
                        + QuestTranslation.translate(
                            "betterquesting.gui.claim_all_quest_count",
                            claimIdList != null ? claimIdList.size() : 0)
                        + "\n"
                        + QuestTranslation
                            .translate("betterquesting.gui.claim_all_choice_quest_count", numChoiceQuests),
                    PresetIcon.ICON_CHEST_ALL.getTexture());

                popup.addOption(
                    QuestTranslation.translate("gui.yes"),
                    btn -> claimAll(claimIdList, BQ_Settings.claimAllRandomChoice),
                    true);
                popup.addOption(QuestTranslation.translate("betterquesting.gui.yes_always"), btn -> {
                    ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Claim all requires confirmation", true)
                        .set(false);
                    ConfigHandler.config.save();
                    ConfigHandler.initConfigs();
                    claimAll(claimIdList, BQ_Settings.claimAllRandomChoice);
                }, true);
                popup.addOption(QuestTranslation.translate("gui.no"), null, true);
                popup.addOption(getForceChoiceString(), btn -> {
                    // Always save the result of the checkbox, this way it is "remembered" for next time
                    Property prop = ConfigHandler.config
                        .get(Configuration.CATEGORY_GENERAL, "Claim all random select choice rewards", false);
                    prop.set(!prop.getBoolean());
                    ConfigHandler.config.save();
                    ConfigHandler.initConfigs();
                    btn.setText(getForceChoiceString());
                },
                    false,
                    QuestTranslation.translate("betterquesting.gui.force_choice_detailed_1"),
                    QuestTranslation.translate("betterquesting.gui.force_choice_detailed_2"));

                openPopup(popup);
            } else {
                claimAll(claimIdList, BQ_Settings.claimAllRandomChoice);
            }
        });
        claimAll.setTooltip(
            ImmutableList.of(
                QuestTranslation.translate("betterquesting.btn.claim_all"),
                QuestTranslation.translate("betterquesting.btn.claim_all_detailed")));
        cvBackground.addPanel(claimAll);
        yOff += 16;

        PanelButton fitView = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), 5, "");
        fitView.setIcon(PresetIcon.ICON_BOX_FIT.getTexture());
        fitView.setClickAction((b) -> { if (cvQuest.getQuestLine() != null) cvQuest.fitToWindow(); });
        fitView.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.zoom_fit")));
        cvBackground.addPanel(fitView);
        yOff += 16;

        // The Jester1147 button
        PanelButton btnTrayLock = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "")
            .setIcon(trayLock ? PresetIcon.ICON_LOCKED.getTexture() : PresetIcon.ICON_UNLOCKED.getTexture());
        btnTrayLock.setClickAction((b) -> {
            trayLock = !trayLock;
            b.setIcon(trayLock ? PresetIcon.ICON_LOCKED.getTexture() : PresetIcon.ICON_UNLOCKED.getTexture());
            ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Lock tray", false)
                .set(trayLock);
            ConfigHandler.config.save();
            ConfigHandler.initConfigs();
        });
        btnTrayLock.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.lock_tray")));
        cvBackground.addPanel(btnTrayLock);
        yOff += 16;

        // View Mode Button
        if (BQ_Settings.viewModeBtn) {
            PanelButton btnViewMode = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "")
                .setIcon(
                    viewMode ? PresetIcon.ICON_VIEW_MODE_ON.getTexture() : PresetIcon.ICON_VIEW_MODE_OFF.getTexture());
            btnViewMode.setClickAction((b) -> {
                viewMode = !viewMode;
                b.setIcon(
                    viewMode ? PresetIcon.ICON_VIEW_MODE_ON.getTexture() : PresetIcon.ICON_VIEW_MODE_OFF.getTexture());
                ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "View mode", false)
                    .set(viewMode);
                ConfigHandler.config.save();
                ConfigHandler.initConfigs();
                refreshGui();
            });
            btnViewMode
                .setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.view_mode")));
            cvBackground.addPanel(btnViewMode);
            yOff += 16;
        }

        PanelButton btnViewMode = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "")
            .setIcon(
                alwaysDrawImplicit ? PresetIcon.ICON_VISIBILITY_IMPLICIT.getTexture()
                    : PresetIcon.ICON_VISIBILITY_NORMAL.getTexture());
        btnViewMode.setClickAction((b) -> {
            alwaysDrawImplicit = !alwaysDrawImplicit;
            b.setIcon(
                alwaysDrawImplicit ? PresetIcon.ICON_VISIBILITY_IMPLICIT.getTexture()
                    : PresetIcon.ICON_VISIBILITY_NORMAL.getTexture());
            ConfigHandler.config
                .get(
                    Configuration.CATEGORY_GENERAL,
                    "Always draw implicit dependency",
                    false,
                    "If true, always draw implicit dependency. This property can be changed by the GUI")
                .set(alwaysDrawImplicit);
            ConfigHandler.config.save();
            btnViewMode.setTooltip(
                Arrays.asList(
                    QuestTranslation.translate("betterquesting.btn.always_draw_implicit"),
                    QuestTranslation.translate("betterquesting.tooltip.cycle." + alwaysDrawImplicit)));
            ConfigHandler.initConfigs();
            refreshGui();
        });
        btnViewMode.setTooltip(
            Arrays.asList(
                QuestTranslation.translate("betterquesting.btn.always_draw_implicit"),
                QuestTranslation.translate("betterquesting.tooltip.cycle." + alwaysDrawImplicit)));
        cvBackground.addPanel(btnViewMode);

        // === CHAPTER VIEWPORT ===

        CanvasQuestLine oldCvQuest = cvQuest;
        cvQuest = new CanvasQuestLine(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), 2);
        CanvasEmpty cvQuestPopup = new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0)) {

            @Override
            public boolean onMouseClick(int mx, int my, int click) {
                if (cvQuest.getQuestLine() == null || !this.getTransform()
                    .contains(mx, my)) return false;
                if (click == 1) {
                    FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

                    PanelButtonQuest btnQuest = cvQuest.getButtonAt(mx, my);

                    if (btnQuest != null) {
                        UUID questId = btnQuest.getStoredValue()
                            .getKey();
                        int maxWidth = RenderUtils
                            .getStringWidth(QuestTranslation.translate("betterquesting.btn.share_quest"), fr);

                        PopContextMenu popup = new PopContextMenu(new GuiRectangle(mx, my, maxWidth + 12, 48), true);

                        Runnable pinQuest = () -> {
                            boolean bookmarked = BookmarkHandler.bookmarkQuest(questId);
                            btnQuest.setBookmarked(bookmarked);
                            closePopup();
                        };

                        String pinPopupText;
                        if (BookmarkHandler.isBookmarked(questId)) {
                            pinPopupText = QuestTranslation.translate("betterquesting.btn.unbookmark_quest");
                        } else {
                            pinPopupText = QuestTranslation.translate("betterquesting.btn.bookmark_quest");
                        }
                        popup.addButton(pinPopupText, null, pinQuest);

                        Runnable questSharer = () -> {
                            mc.thePlayer
                                .sendChatMessage("betterquesting.msg.sharequest:" + UuidConverter.encodeUuid(questId));
                            closePopup();
                        };
                        popup
                            .addButton(QuestTranslation.translate("betterquesting.btn.share_quest"), null, questSharer);

                        Runnable copyQuestId = () -> {
                            StringSelection stringToCopy = new StringSelection(UuidConverter.encodeUuid(questId));
                            try {
                                Toolkit.getDefaultToolkit()
                                    .getSystemClipboard()
                                    .setContents(stringToCopy, null);
                                mc.thePlayer.addChatMessage(
                                    new ChatComponentText(
                                        QuestTranslation.translate("betterquesting.msg.copy_quest_copied")));
                                mc.thePlayer.addChatMessage(
                                    new ChatComponentText(
                                        "  " + EnumChatFormatting.AQUA + UuidConverter.encodeUuid(questId)));
                            } catch (IllegalStateException e) {
                                mc.thePlayer.addChatMessage(
                                    new ChatComponentText(
                                        QuestTranslation.translate("betterquesting.msg.copy_quest_failed")));
                            }
                            closePopup();
                        };
                        popup.addButton(QuestTranslation.translate("betterquesting.btn.copy_quest"), null, copyQuestId);

                        openPopup(popup);
                        return true;
                    }
                }
                return false;
            }
        };
        cvFrame.addPanel(cvQuest);
        cvFrame.addPanel(cvQuestPopup);

        if (selectedLine != null) {
            cvQuest.setQuestLine(selectedLine);

            if (oldCvQuest != null) {
                cvQuest.setZoom(oldCvQuest.getZoom());
                cvQuest.setScrollX(oldCvQuest.getScrollX());
                cvQuest.setScrollY(oldCvQuest.getScrollY());
                cvQuest.refreshScrollBounds();
                cvQuest.updatePanelScroll();
            }

            refreshQuestCompletion();
            txTitle.setText(QuestTranslation.translateQuestLineName(selectedLineId, selectedLine));
            icoChapter
                .setTexture(new OreDictTexture(1F, selectedLine.getProperty(NativeProps.ICON), false, true), null);
        }

        // === MISC ===

        cvChapterTray.setTrayState(chapterTrayOpened, 1);
        cvDescTray.setTrayState(descTrayOpened, 1);

        refreshChapterVisibility();
        refreshClaimAll();

        cvLines.setScrollY(scrollPosition.getChapterScrollY());
        cvLines.updatePanelScroll();
    }

    private String getForceChoiceString() {
        String key = BQ_Settings.claimAllRandomChoice ? "betterquesting.gui.force_choice_yes"
            : "betterquesting.gui.force_choice_no";
        return QuestTranslation.translate(key);
    }

    private GuiBookmarks initBookmarksPanel() {
        GuiBookmarks pinsGui = new GuiBookmarks(this);
        pinsGui.setCallback(entry -> {
            openQuestLine(entry.getQuestLineEntry());
            UUID selectedQuestId = entry.getQuest()
                .getKey();
            Optional<PanelButtonQuest> targetQuestButton = cvQuest.getQuestButtons()
                .stream()
                .filter(
                    panelButtonQuest -> panelButtonQuest.getStoredValue()
                        .getKey()
                        .equals(selectedQuestId))
                .findFirst();
            targetQuestButton.ifPresent(this::highlightButton);
        });
        return pinsGui;
    }

    private GuiQuestSearch initSearchPanel() {
        GuiQuestSearch searchGui = new GuiQuestSearch(this);
        searchGui.setCallback(entry -> {
            openQuestLine(entry.getQuestLineEntry());
            UUID selectedQuestId = entry.getQuest()
                .getKey();
            Optional<PanelButtonQuest> targetQuestButton = cvQuest.getQuestButtons()
                .stream()
                .filter(
                    panelButtonQuest -> panelButtonQuest.getStoredValue()
                        .getKey()
                        .equals(selectedQuestId))
                .findFirst();
            targetQuestButton.ifPresent(this::highlightButton);
        });

        return searchGui;
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        try {
            return super.onMouseRelease(mx, my, click);
        } finally {
            if (cvLines != null) {
                scrollPosition.setChapterScrollY(cvLines.getScrollY());
            }
        }
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        try {
            return super.onMouseScroll(mx, my, scroll);
        } finally {
            if (cvLines != null) {
                scrollPosition.setChapterScrollY(cvLines.getScrollY());
            }
        }
    }

    private Pair<List<UUID>, Integer> getAllPossibleClaims() {
        if (cvQuest.getQuestButtons()
            .isEmpty()) {
            return null;
        }

        List<UUID> claimIdList = new ArrayList<>();
        int numChoiceRewards = 0;

        for (PanelButtonQuest pbQuest : cvQuest.getQuestButtons()) {
            IQuest q = pbQuest.getStoredValue()
                .getValue();

            if (q.getRewards()
                .size() == 0) continue;

            // always true here for the maximum possible quests
            if (q.canClaim(mc.thePlayer, true)) {
                claimIdList.add(
                    pbQuest.getStoredValue()
                        .getKey());
            }

            for (DBEntry<IReward> rewardEntry : q.getRewards()
                .getEntries()) {
                IReward reward = rewardEntry.getValue();
                if (reward instanceof RewardChoice) {
                    numChoiceRewards++;
                    break;
                }
            }
        }

        return Pair.of(claimIdList, numChoiceRewards);
    }

    private void claimAll(List<UUID> claimIdList, boolean forceChoice) {
        if (claimIdList == null || claimIdList.isEmpty()) return;
        if (forceChoice) {
            NetQuestAction.requestClaimForced(claimIdList);
        } else {
            NetQuestAction.requestClaim(claimIdList);
        }
        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    // TODO: Change CanvasQuestLine to NOT need these panel events anymore
    private void onButtonPress(PEventButton event) {
        Minecraft mc = Minecraft.getMinecraft();
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) // Quest Instance Select
        {
            @SuppressWarnings("unchecked")
            Map.Entry<UUID, IQuest> quest = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>) btn).getStoredValue();
            GuiHome.bookmark = new GuiQuest(this, quest.getKey());

            mc.displayGuiScreen(GuiHome.bookmark);
        }
    }

    private void refreshChapterVisibility() {
        boolean canEdit = QuestingAPI.getAPI(ApiReference.SETTINGS)
            .canUserEdit(mc.thePlayer);
        List<Map.Entry<UUID, IQuestLine>> lineList = QuestLineDatabase.INSTANCE.getOrderedEntries();
        this.visChapters.clear();
        UUID playerID = QuestingAPI.getQuestingUUID(mc.thePlayer);

        for (Map.Entry<UUID, IQuestLine> entry : lineList) {
            IQuestLine ql = entry.getValue();
            EnumQuestVisibility vis = ql.getProperty(NativeProps.VISIBILITY);
            if (!canEdit && vis == EnumQuestVisibility.HIDDEN) {
                continue;
            }

            boolean show = false;
            boolean unlocked = false;
            boolean complete = false;
            boolean allComplete = true;
            boolean pendingClaim = false;

            if (canEdit) {
                show = true;
                unlocked = true;
                complete = true;
            }

            if (BQ_Settings.viewMode) {
                show = true;
            }

            if (BQ_Settings.viewModeAllQuestLine) {
                unlocked = true;
            }

            for (Map.Entry<UUID, IQuestLineEntry> qID : ql.entrySet()) {
                IQuest q = QuestDatabase.INSTANCE.get(qID.getKey());
                if (q == null) continue;

                if (allComplete && !isQuestCompletedForQuestline(playerID, q)) allComplete = false;
                if (!pendingClaim && q.canClaimBasically(mc.thePlayer)) pendingClaim = true;
                if (!unlocked && q.isUnlocked(playerID)) unlocked = true;
                if (!complete && q.isComplete(playerID)) complete = true;
                if (!show && QuestCache.isQuestShown(q, playerID, mc.thePlayer)) show = true;
                if (unlocked && complete && show && pendingClaim && !allComplete) break;
            }

            if (vis == EnumQuestVisibility.COMPLETED && !complete) {
                continue;
            } else if (vis == EnumQuestVisibility.UNLOCKED && !unlocked) {
                continue;
            }

            int val = pendingClaim ? 1 : 0;
            if (allComplete) {
                val |= 2;
            }
            if (!show) {
                val |= 4;
            }

            visChapters.add(new Tuple2<>(entry, val));
        }

        if (cvChapterTray.isTrayOpen()) buildChapterList();
    }

    private boolean isQuestCompletedForQuestline(UUID playerID, IQuest q) {
        if (q.isComplete(playerID)) return true; // Completed quest
        if (q.getProperty(NativeProps.VISIBILITY) == EnumQuestVisibility.HIDDEN) return true; // Always hidden quest
        if (q.getProperty(NativeProps.LOGIC_QUEST) == EnumLogic.XOR) { // Quest with choice
            int reqCount = 0;
            for (UUID qRequirementId : q.getRequirements()) {
                IQuest quest = QuestDatabase.INSTANCE.get(qRequirementId);
                if (quest.isComplete(playerID)) reqCount++;
                if (reqCount == 2) return true;
            }
        }

        return false;
    }

    private void buildChapterList() {
        cvLines.resetCanvas();
        btnListRef.clear();

        int listW = cvLines.getTransform()
            .getWidth();

        for (int n = 0; n < visChapters.size(); n++) {
            Map.Entry<UUID, IQuestLine> entry = visChapters.get(n)
                .getFirst();
            int vis = visChapters.get(n)
                .getSecond();

            cvLines.addPanel(
                new PanelGeneric(
                    new GuiRectangle(0, n * 16, 16, 16, 0),
                    new OreDictTexture(
                        1F,
                        entry.getValue()
                            .getProperty(NativeProps.ICON),
                        false,
                        true)));

            if ((vis & 1) > 0) {
                cvLines.addPanel(
                    new PanelGeneric(
                        new GuiRectangle(8, n * 16 + 8, 8, 8, -1),
                        new GuiTextureColored(PresetIcon.ICON_NOTICE.getTexture(), new GuiColorStatic(0xFFFFFF00))));
            } else if ((vis & 2) > 0) {
                cvLines.addPanel(
                    new PanelGeneric(
                        new GuiRectangle(8, n * 16 + 8, 8, 8, -1),
                        new GuiTextureColored(PresetIcon.ICON_TICK.getTexture(), new GuiColorStatic(0xFF00FF00))));
            }
            PanelButtonStorage<Map.Entry<UUID, IQuestLine>> btnLine = new PanelButtonStorage<>(
                new GuiRectangle(16, n * 16, listW - 16, 16, 0),
                1,
                QuestTranslation.translateQuestLineName(entry),
                entry);
            btnLine.setTextAlignment(0);
            btnLine.setActive(
                (vis & 4) == 0 && !entry.getKey()
                    .equals(selectedLineId));
            btnLine.setCallback(this::openQuestLine);
            cvLines.addPanel(btnLine);
            btnListRef.add(btnLine);
        }

        cvLines.refreshScrollBounds();
        scLines.setEnabled(
            cvLines.getScrollBounds()
                .getHeight() > 0);
    }

    private void refreshQuestCompletion() {
        EntityPlayer player = mc.thePlayer;
        UUID playerUUId = QuestingAPI.getQuestingUUID(player);

        if (selectedLine == null) {
            return;
        }

        questsCompleted = 0;
        totalQuests = 0;

        for (Map.Entry<UUID, IQuestLineEntry> entry : selectedLine.entrySet()) {
            IQuest quest = QuestingAPI.getAPI(ApiReference.QUEST_DB)
                .get(entry.getKey());
            if (quest == null) {
                if (BQ_Settings.logNullQuests) {
                    BetterQuesting.logger.warn("Skipping null quest with ID {}", entry.getKey());
                }
                continue;
            }

            totalQuests++;

            if (quest.isComplete(playerUUId) || !quest.isUnlockable(playerUUId)) {
                questsCompleted++;
            }
        }
        completionText
            .setText(QuestTranslation.translate("betterquesting.title.completion", questsCompleted, totalQuests));
    }

    private void openQuestLine(Map.Entry<UUID, IQuestLine> q) {
        selectedLine = q.getValue();
        selectedLineId = q.getKey();
        for (int i = 0; i < btnListRef.size(); i++) {
            btnListRef.get(i)
                .setActive(
                    (visChapters.get(i)
                        .getSecond() & 4) == 0 && !btnListRef.get(i)
                            .getStoredValue()
                            .getKey()
                            .equals(selectedLineId));
        }

        cvQuest.setQuestLine(q.getValue());
        icoChapter.setTexture(
            new OreDictTexture(
                1F,
                q.getValue()
                    .getProperty(NativeProps.ICON),
                false,
                true),
            null);
        txTitle.setText(QuestTranslation.translateQuestLineName(q));
        refreshQuestCompletion();

        if (!trayLock) {
            cvFrame.setTrayState(true, 200);
            cvChapterTray.setTrayState(false, 200);
            cvQuest.fitToWindow();
        }
        refreshClaimAll();
    }

    private void refreshContent() {
        if (selectedLineId != null) {
            selectedLine = QuestLineDatabase.INSTANCE.get(selectedLineId);
            if (selectedLine == null) {
                selectedLineId = null;
            }
        } else {
            selectedLine = null;
        }

        float zoom = cvQuest.getZoom();
        int sx = cvQuest.getScrollX();
        int sy = cvQuest.getScrollY();
        /* if(cvQuest.getQuestLine() != selectedLine) */ cvQuest.setQuestLine(selectedLine);
        cvQuest.setZoom(zoom);
        cvQuest.setScrollX(sx);
        cvQuest.setScrollY(sy);
        cvQuest.refreshScrollBounds();
        cvQuest.updatePanelScroll();

        if (selectedLine != null) {
            refreshQuestCompletion();
            txTitle.setText(QuestTranslation.translateQuestLineName(selectedLineId, selectedLine));
            icoChapter
                .setTexture(new OreDictTexture(1F, selectedLine.getProperty(NativeProps.ICON), false, true), null);
        } else {
            txTitle.setText("");
            icoChapter.setTexture(null, null);
        }

        refreshClaimAll();
    }

    private void refreshClaimAll() {
        if (cvQuest.getQuestLine() == null || cvQuest.getQuestButtons()
            .isEmpty()) {
            claimAll.setActive(false);
            claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
            return;
        }

        for (PanelButtonQuest btn : cvQuest.getQuestButtons()) {
            // Always check with forceChoice true to ensure the button can appear even if
            // there are only choice reward quests available to claim currently
            if (btn.getStoredValue()
                .getValue()
                .canClaim(mc.thePlayer, true)) {
                claimAll.setActive(true);
                claimAll.setIcon(
                    PresetIcon.ICON_CHEST_ALL.getTexture(),
                    new GuiColorPulse(0xFFFFFFFF, 0xFF444444, 2F, 0F),
                    0);
                return;
            }
        }

        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
        claimAll.setActive(false);
    }

    private void highlightButton(PanelButtonQuest panelButtonQuest) {
        GuiTextureColored newTexture = new GuiTextureColored(
            panelButtonQuest.txFrame,
            new GuiColorPulse(new GuiColorStatic(0, 0, 0, 255), new GuiColorStatic(255, 191, 0, 255), 0.7, 0));
        panelButtonQuest.setTextures(newTexture, newTexture, newTexture);
        cvQuest.setZoom(2f);
        cvQuest.centerOn(panelButtonQuest);
    }
}
