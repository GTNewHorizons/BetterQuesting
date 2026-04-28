package betterquesting.api2.client.gui.popups;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasResizeable;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;

public class PopContextMenuHoverSub extends CanvasEmpty {

    private final List<MenuEntry> entries = new ArrayList<>();
    private final GuiRectangle rect;
    private final boolean autoClose;

    private int activeSubIndex = -1;
    private CanvasEmpty subPanel = null;
    private GuiRectangle subRect = null;

    public PopContextMenuHoverSub(GuiRectangle rect, boolean autoClose) {
        super(rect);
        this.rect = rect;
        this.autoClose = autoClose;
    }

    public void addButton(@Nonnull String text, @Nullable IGuiTexture icon, @Nullable Runnable action) {
        entries.add(new MenuEntry(text, icon, action, null));
    }

    public void addSubMenu(@Nonnull String text, @Nonnull List<SubMenuEntry> subEntries) {
        entries.add(new MenuEntry(text + " \u25b6", null, null, subEntries));
    }

    @Override
    public void initPanel() {
        super.initPanel();

        int listH = Math.min(entries.size() * 16, rect.getHeight());
        boolean needsScroll = (entries.size() * 16) > listH;
        int scrollBarW = needsScroll ? 8 : 0;
        int buttonWidth = rect.w - scrollBarW;

        if (getTransform().getParent() != null) {
            IGuiRect par = getTransform().getParent();
            rect.x += Math.min(0, (par.getX() + par.getWidth()) - (rect.getX() + rect.getWidth()));
            rect.y += Math.min(0, (par.getY() + par.getHeight()) - (rect.getY() + listH));
        }

        CanvasResizeable cvBG = new CanvasResizeable(
            new GuiRectangle(0, 0, 0, 0, 0),
            PresetTexture.PANEL_INNER.getTexture());
        this.addPanel(cvBG);
        cvBG.lerpToRect(new GuiRectangle(0, 0, buttonWidth, listH, 0), 100, true);

        CanvasScrolling cvScroll = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX));
        cvBG.addPanel(cvScroll);

        PanelVScrollBar scrollBar = new PanelVScrollBar(new GuiRectangle(rect.w - 8, 0, 8, listH, 0));
        this.addPanel(scrollBar);
        cvScroll.setScrollDriverY(scrollBar);

        for (int i = 0; i < entries.size(); i++) {
            MenuEntry entry = entries.get(i);
            PanelButton eBtn = new PanelButton(
                new GuiRectangle(0, i * 16, buttonWidth, 16, 0),
                -1,
                QuestTranslation.translate(entry.text));
            if (entry.action != null) {
                eBtn.setClickAction((b) -> entry.action.run());
            } else if (entry.subEntries == null) {
                eBtn.setActive(false);
            } else {
                // Sub-menu trigger: clicking also opens the submenu
                eBtn.setClickAction((b) -> {
                    // Do nothing on click, hover handles it
                });
            }
            cvScroll.addPanel(eBtn);
        }

        scrollBar.setEnabled(
            cvScroll.getScrollBounds()
                .getHeight() > 0);
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        super.drawPanel(mx, my, partialTick);

        // Check hover for sub-menu items
        int newActiveIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            MenuEntry entry = entries.get(i);
            if (entry.subEntries == null) continue;

            int btnY = rect.getY() + i * 16;
            int btnH = 16;
            if (mx >= rect.getX() && mx < rect.getX() + rect.getWidth() && my >= btnY && my < btnY + btnH) {
                newActiveIndex = i;
                break;
            }
        }

        // Also keep submenu active when hovering over the submenu itself
        if (newActiveIndex == -1 && subRect != null && activeSubIndex >= 0) {
            if (mx >= subRect.getX() && mx < subRect.getX() + subRect.getWidth()
                && my >= subRect.getY()
                && my < subRect.getY() + subRect.getHeight()) {
                newActiveIndex = activeSubIndex;
            }
        }

        if (newActiveIndex != activeSubIndex) {
            if (subPanel != null) {
                this.getChildren()
                    .remove(subPanel);
                subPanel = null;
                subRect = null;
            }

            if (newActiveIndex >= 0) {
                showSubMenu(newActiveIndex);
            }

            activeSubIndex = newActiveIndex;
        }

        // Draw submenu on top
        if (subPanel != null && subPanel.isEnabled()) {
            subPanel.drawPanel(mx, my, partialTick);
        }
    }

    private void showSubMenu(int index) {
        MenuEntry entry = entries.get(index);
        if (entry.subEntries == null || entry.subEntries.isEmpty()) return;

        int subMaxWidth = 0;
        for (SubMenuEntry sub : entry.subEntries) {
            int w = net.minecraft.client.Minecraft.getMinecraft().fontRenderer.getStringWidth(sub.text);
            if (w > subMaxWidth) subMaxWidth = w;
        }
        subMaxWidth += 12;

        int subX = rect.getX() + rect.getWidth();
        int subY = rect.getY() + index * 16;
        int subH = Math.min(entry.subEntries.size() * 16, 160);
        boolean needsScroll = (entry.subEntries.size() * 16) > subH;
        int scrollBarW = needsScroll ? 8 : 0;
        int buttonWidth = subMaxWidth;
        int totalWidth = subMaxWidth + scrollBarW;

        if (getTransform().getParent() != null) {
            IGuiRect par = getTransform().getParent();
            int screenRight = par.getX() + par.getWidth();
            int screenBottom = par.getY() + par.getHeight();
            if (subX + totalWidth > screenRight) {
                subX = rect.getX() - totalWidth;
            }
            if (subY + subH > screenBottom) {
                subY = screenBottom - subH;
            }
        }

        subRect = new GuiRectangle(subX, subY, totalWidth, subH, 0);
        subPanel = new CanvasEmpty(subRect);
        subRect.setParent(getTransform().getParent());
        subPanel.initPanel();

        CanvasResizeable cvSubBG = new CanvasResizeable(
            new GuiRectangle(0, 0, buttonWidth, subH, 0),
            PresetTexture.PANEL_INNER.getTexture());
        subPanel.addPanel(cvSubBG);

        CanvasScrolling cvSubScroll = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX));
        cvSubBG.addPanel(cvSubScroll);

        PanelVScrollBar subScrollBar = new PanelVScrollBar(new GuiRectangle(subMaxWidth, 0, 8, subH, 0));
        subPanel.addPanel(subScrollBar);
        cvSubScroll.setScrollDriverY(subScrollBar);

        for (int i = 0; i < entry.subEntries.size(); i++) {
            SubMenuEntry sub = entry.subEntries.get(i);
            PanelButton subBtn = new PanelButton(new GuiRectangle(0, i * 16, buttonWidth, 16, 0), -1, sub.text);
            if (sub.action != null) {
                subBtn.setClickAction((b) -> sub.action.run());
            }
            cvSubScroll.addPanel(subBtn);
        }

        subScrollBar.setEnabled(
            cvSubScroll.getScrollBounds()
                .getHeight() > 0);
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        // First check submenu
        if (subPanel != null && subPanel.isEnabled()) {
            if (subRect != null && subRect.contains(mx, my)) {
                return subPanel.onMouseClick(mx, my, click);
            }
        }

        boolean used = super.onMouseClick(mx, my, click);

        if (autoClose && !used) {
            boolean inMain = rect.contains(mx, my);
            boolean inSub = subRect != null && subRect.contains(mx, my);
            if (!inMain && !inSub && SceneController.getActiveScene() != null) {
                SceneController.getActiveScene()
                    .closePopup();
                return true;
            }
        }

        return used;
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        if (subPanel != null && subPanel.isEnabled()) {
            if (subRect != null && subRect.contains(mx, my)) {
                return subPanel.onMouseRelease(mx, my, click);
            }
        }
        return super.onMouseRelease(mx, my, click);
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        if (subPanel != null && subPanel.isEnabled()) {
            if (subRect != null && subRect.contains(mx, my)) {
                return subPanel.onMouseScroll(mx, my, scroll);
            }
        }
        return super.onMouseScroll(mx, my, scroll);
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        if (subPanel != null && subPanel.isEnabled()) {
            if (subRect != null && subRect.contains(mx, my)) {
                List<String> tt = subPanel.getTooltip(mx, my);
                if (tt != null) return tt;
            }
        }
        return super.getTooltip(mx, my);
    }

    public static class MenuEntry {

        final String text;
        final IGuiTexture icon;
        final Runnable action;
        final List<SubMenuEntry> subEntries;

        MenuEntry(@Nonnull String text, @Nullable IGuiTexture icon, @Nullable Runnable action,
            @Nullable List<SubMenuEntry> subEntries) {
            this.text = text;
            this.icon = icon;
            this.action = action;
            this.subEntries = subEntries;
        }
    }

    public static class SubMenuEntry {

        final String text;
        final Runnable action;

        public SubMenuEntry(@Nonnull String text, @Nullable Runnable action) {
            this.text = text;
            this.action = action;
        }
    }
}
