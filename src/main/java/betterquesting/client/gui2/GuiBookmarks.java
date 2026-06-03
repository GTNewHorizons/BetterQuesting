package betterquesting.client.gui2;

import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import betterquesting.api.utils.RenderUtils;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestBookmarks;
import betterquesting.api2.client.gui.popups.PopContextMenu;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.BookmarkHandler;
import betterquesting.misc.QuestSearchEntry;

public class GuiBookmarks extends GuiScreenCanvas {

    private Consumer<QuestSearchEntry> callback;
    private CanvasQuestBookmarks cvBookmarks;

    public GuiBookmarks(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initPanel() {
        super.initPanel();
        CanvasTextured cvBackground = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        CanvasEmpty cvInner = new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 8, 8), 0));
        cvBackground.addPanel(cvInner);

        createExitButton(cvInner);

        PanelTextBox txtDb = new PanelTextBox(
            new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0),
            QuestTranslation.translate("betterquesting.gui.bookmarks")).setAlignment(1)
                .setColor(PresetColor.TEXT_MAIN.getColor());
        cvInner.addPanel(txtDb);

        cvBookmarks = createMainCanvas();
        cvInner.addPanel(cvBookmarks);

        cvInner.addPanel(createPopupMenu());

        PanelVScrollBar scDb = new PanelVScrollBar(
            new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 32, 0, 24), 0));
        cvInner.addPanel(scDb);
        cvBookmarks.setScrollDriverY(scDb);
    }

    private CanvasQuestBookmarks createMainCanvas() {
        CanvasQuestBookmarks pins = new CanvasQuestBookmarks(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 32, 8, 24), 0),
            mc.thePlayer);
        pins.setQuestOpenCallback(pinEntry -> {
            acceptCallback(pinEntry);
            GuiHome.bookmark = new GuiQuest(
                parent,
                pinEntry.getQuest()
                    .getKey());
            mc.displayGuiScreen(GuiHome.bookmark);
        });
        pins.setQuestHighlightCallback(pinEntry -> {
            mc.displayGuiScreen(parent);
            acceptCallback(pinEntry);
        });
        return pins;
    }

    private void createExitButton(CanvasEmpty cvInner) {
        PanelButton btnExit = new PanelButton(
            new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0),
            0,
            QuestTranslation.translate("gui.back"));
        btnExit.setClickAction((b) -> mc.displayGuiScreen(parent));
        cvInner.addPanel(btnExit);
    }

    private CanvasEmpty createPopupMenu() {
        return new CanvasEmpty(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0)) {

            @Override
            public boolean onMouseClick(int mx, int my, int click) {
                if (!this.getTransform()
                    .contains(mx, my)) return false;
                if (click == 1) {
                    FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
                    PanelButtonQuest btn = cvBookmarks.getButtonAt(mx, my);

                    if (btn != null) {
                        UUID questId = btn.getStoredValue()
                            .getKey();
                        int maxWidth = RenderUtils
                            .getStringWidth(QuestTranslation.translate("betterquesting.btn.share_quest"), fr);

                        PopContextMenu popup = new PopContextMenu(new GuiRectangle(mx, my, maxWidth + 12, 48), true);

                        Runnable pinQuest = () -> {
                            boolean bookmarked = BookmarkHandler.bookmarkQuest(questId);
                            btn.setBookmarked(bookmarked);
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
                            String questIdString = UuidConverter.encodeUuid(questId);
                            GuiScreen.setClipboardString(questIdString);
                            mc.thePlayer.addChatMessage(
                                new ChatComponentText(
                                    QuestTranslation.translate("betterquesting.msg.copy_quest_copied")));
                            mc.thePlayer
                                .addChatMessage(new ChatComponentText("  " + EnumChatFormatting.AQUA + questIdString));
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
    }

    public void setCallback(Consumer<QuestSearchEntry> callback) {
        this.callback = callback;
    }

    private void acceptCallback(QuestSearchEntry questSearchEntry) {
        if (callback != null) callback.accept(questSearchEntry);
    }
}
