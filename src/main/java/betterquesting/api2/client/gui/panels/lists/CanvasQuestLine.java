package betterquesting.api2.client.gui.panels.lists;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.Maps;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestState;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuest.RequirementType;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelLine.ShouldDrawPredicate;
import betterquesting.api2.client.gui.resources.colors.GuiColorPulse;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.resources.lines.IGuiLine;
import betterquesting.api2.client.gui.resources.textures.SimpleTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.core.BetterQuesting;

/**
 * My class for lazy quest line setup on a scrolling canvas
 */
public class CanvasQuestLine extends CanvasScrolling {

    private static final QuestButtonCache BUTTON_CACHE = new QuestButtonCache();

    private final List<PanelButtonQuest> btnList = new ArrayList<>();

    private final int buttonId;
    private IQuestLine lastQL;
    private int zoomToFitMargin = 24;
    private boolean suppressButtonRendering;
    private boolean suppressLineRendering;

    public CanvasQuestLine(IGuiRect rect, int buttonId) {
        super(rect);
        this.setupAdvanceScroll(true, true, 3000);
        this.buttonId = buttonId;
    }

    public Collection<PanelButtonQuest> getQuestButtons() {
        return Collections.unmodifiableCollection(this.btnList);
    }

    public static void releaseButtonCache() {
        BUTTON_CACHE.release();
    }

    public PanelButtonQuest getButtonAt(int mx, int my) {
        float zs = zoomScale.readValue();
        int tx = getTransform().getX();
        int ty = getTransform().getY();
        int smx = (int) ((mx - tx) / zs) + lsx;
        int smy = (int) ((my - ty) / zs) + lsy;

        for (PanelButtonQuest btn : btnList) {
            if (btn.rect.contains(smx, smy)) return btn;
        }

        return null;
    }

    public IQuestLine getQuestLine() {
        return lastQL;
    }

    public void refreshQuestLine() {
        setQuestLine(lastQL);
    }

    @Override
    public void resetCanvas() {
        BUTTON_CACHE.invalidate();
        super.resetCanvas();
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        boolean batchLines = isBlockingEnabled() && areLinesBatchable();
        boolean cacheButtons = isBlockingEnabled() && BUTTON_CACHE.isEnabled() && areButtonsTopLayer();
        boolean cacheLines = batchLines && cacheButtons;
        suppressButtonRendering = cacheButtons || batchLines;
        suppressLineRendering = batchLines;
        try {
            super.drawPanel(mx, my, partialTick);
        } finally {
            suppressButtonRendering = false;
            suppressLineRendering = false;
        }

        float zoom = lsz;
        if (batchLines && !cacheLines) {
            drawQuestLines(mx, my, partialTick, zoom, true);
            if (!cacheButtons) drawQuestButtons(mx, my, partialTick, zoom, true);
        }

        if (cacheButtons) {
            try {
                BUTTON_CACHE.draw(
                    this,
                    getTransform(),
                    lsx,
                    lsy,
                    zoom,
                    getButtonStateHash(),
                    () -> drawQuestContent(mx, my, partialTick, zoom, cacheLines, true),
                    () -> drawQuestContent(mx, my, partialTick, zoom, cacheLines, false));
            } catch (RuntimeException e) {
                BUTTON_CACHE.fail(e);
                drawQuestContent(mx, my, partialTick, zoom, cacheLines, true);
            }
        }
    }

    @Override
    protected boolean shouldDrawPanel(IGuiPanel panel) {
        return (!suppressButtonRendering || !(panel instanceof PanelButtonQuest))
            && (!suppressLineRendering || !(panel instanceof PanelLine));
    }

    private boolean areLinesBatchable() {
        boolean foundLine = false;
        boolean foundButton = false;
        for (IGuiPanel panel : getChildren()) {
            if (!panel.isEnabled()) continue;
            if (panel instanceof PanelLine) {
                if (foundButton || !((PanelLine) panel).isBatchable()) return false;
                foundLine = true;
            } else if (panel instanceof PanelButtonQuest) {
                foundButton = true;
            } else if (foundLine) {
                return false;
            }
        }
        return foundLine;
    }

    private boolean areButtonsTopLayer() {
        boolean foundButton = false;
        for (IGuiPanel panel : getChildren()) {
            if (!panel.isEnabled()) continue;
            if (panel instanceof PanelButtonQuest) {
                foundButton = true;
            } else if (foundButton) {
                return false;
            }
        }
        return foundButton;
    }

    private int getButtonStateHash() {
        int hash = 1;
        for (IGuiPanel panel : getVisiblePanels()) {
            if (panel instanceof PanelButtonQuest) {
                PanelButtonQuest button = (PanelButtonQuest) panel;
                hash = 31 * hash + System.identityHashCode(button);
                hash = 31 * hash + (button.isEnabled() ? 1 : 0);
                hash = 31 * hash + (button.isActive() ? 1 : 0);
                hash = 31 * hash + (button.isBookmarked() ? 1 : 0);
            }
        }
        return hash;
    }

    private void drawQuestContent(int mx, int my, float partialTick, float zs, boolean includeLines, boolean clip) {
        if (includeLines) {
            if (!clip) {
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            try {
                drawQuestLines(mx, my, partialTick, zs, clip);
            } finally {
                if (!clip) {
                    GL11.glDisable(GL11.GL_BLEND);
                    OpenGlHelper.glBlendFunc(
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA);
                }
            }
        }
        drawQuestButtons(mx, my, partialTick, zs, clip);
    }

    private void drawQuestLines(int mx, int my, float partialTick, float zs, boolean clip) {
        IGuiRect bounds = getTransform();
        int tx = bounds.getX();
        int ty = bounds.getY();
        int smx = (int) ((mx - tx) / zs) + lsx;
        int smy = (int) ((my - ty) / zs) + lsy;

        GL11.glPushMatrix();
        if (clip) RenderUtils.startScissor(bounds);
        try {
            GL11.glTranslatef(tx - lsx * zs, ty - lsy * zs, 0F);
            GL11.glScalef(zs, zs, zs);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            RuntimeException failure = null;
            try {
                int batchQuads = 0;
                for (IGuiPanel panel : getVisiblePanels()) {
                    if (panel instanceof PanelLine && panel.isEnabled()) {
                        batchQuads = ((PanelLine) panel).addToBatch(tessellator, smx, smy, partialTick, batchQuads);
                    }
                }
            } catch (RuntimeException e) {
                failure = e;
                throw e;
            } finally {
                try {
                    tessellator.draw();
                } catch (RuntimeException e) {
                    if (failure == null) throw e;
                    failure.addSuppressed(e);
                }
            }
        } finally {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            if (clip) RenderUtils.endScissor();
            GL11.glPopMatrix();
        }
    }

    private void drawQuestButtons(int mx, int my, float partialTick, float zs, boolean clip) {
        IGuiRect bounds = getTransform();
        int tx = bounds.getX();
        int ty = bounds.getY();
        int smx = (int) ((mx - tx) / zs) + lsx;
        int smy = (int) ((my - ty) / zs) + lsy;

        GL11.glPushMatrix();
        if (clip) RenderUtils.startScissor(bounds);
        try {
            GL11.glTranslatef(tx - lsx * zs, ty - lsy * zs, 0F);
            GL11.glScalef(zs, zs, zs);

            for (IGuiPanel panel : getVisiblePanels()) {
                if (panel instanceof PanelButtonQuest && panel.isEnabled()) {
                    panel.drawPanel(smx, smy, partialTick);
                }
            }
        } finally {
            if (clip) RenderUtils.endScissor();
            GL11.glPopMatrix();
        }
    }

    /**
     * Loads in quests and connecting lines
     * 
     * @param line The quest line to load
     */
    public void setQuestLine(IQuestLine line) {
        // Rest contents
        this.resetCanvas();
        this.btnList.clear();
        lastQL = line;

        if (line == null) {
            return;
        }

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        UUID pid = QuestingAPI.getQuestingUUID(player);

        String bgString = line.getProperty(NativeProps.BG_IMAGE);

        if (!StringUtils.isNullOrEmpty(bgString)) {
            int bgSize = line.getProperty(NativeProps.BG_SIZE);
            this.addCulledPanel(
                new PanelGeneric(
                    new GuiRectangle(0, 0, bgSize, bgSize, 1),
                    new SimpleTexture(new ResourceLocation(bgString), new GuiRectangle(0, 0, 256, 256))),
                false);
        }

        HashMap<UUID, PanelButtonQuest> questBtns = new HashMap<>();

        for (Map.Entry<UUID, IQuestLineEntry> qle : line.entrySet()) {
            IQuest quest = QuestingAPI.getAPI(ApiReference.QUEST_DB)
                .get(qle.getKey());

            if (!QuestCache.isQuestShown(quest, pid, player)) continue;

            GuiRectangle rect = new GuiRectangle(
                qle.getValue()
                    .getPosX(),
                qle.getValue()
                    .getPosY(),
                qle.getValue()
                    .getSizeX(),
                qle.getValue()
                    .getSizeY());
            PanelButtonQuest paBtn = new PanelButtonQuest(rect, buttonId, "", Maps.immutableEntry(qle.getKey(), quest));

            this.btnList.add(paBtn);
            questBtns.put(qle.getKey(), paBtn);
        }

        for (Map.Entry<UUID, PanelButtonQuest> entry : questBtns.entrySet()) {
            Map.Entry<UUID, IQuest> quest = entry.getValue()
                .getStoredValue();

            Map<UUID, IQuest> reqMap = QuestingAPI.getAPI(ApiReference.QUEST_DB)
                .filterKeys(
                    quest.getValue()
                        .getRequirements());

            if (reqMap.isEmpty()) {
                continue;
            }

            boolean main = quest.getValue()
                .getProperty(NativeProps.MAIN);
            EnumQuestState qState = quest.getValue()
                .getState(player);
            IGuiLine lineRender = null;
            IGuiColor defaultTxLineCol = null;

            switch (qState) {
                case LOCKED:
                    lineRender = PresetLine.QUEST_LOCKED.getLine();
                    defaultTxLineCol = PresetColor.QUEST_LINE_LOCKED.getColor();
                    break;
                case UNLOCKED:
                    lineRender = PresetLine.QUEST_UNLOCKED.getLine();
                    defaultTxLineCol = PresetColor.QUEST_LINE_UNLOCKED.getColor();
                    break;
                case UNCLAIMED:
                    lineRender = PresetLine.QUEST_PENDING.getLine();
                    defaultTxLineCol = PresetColor.QUEST_LINE_PENDING.getColor();
                    break;
                case COMPLETED:
                    lineRender = PresetLine.QUEST_COMPLETE.getLine();
                    defaultTxLineCol = PresetColor.QUEST_LINE_COMPLETE.getColor();
                    break;
                case REPEATABLE:
                    lineRender = PresetLine.QUEST_REPEATABLE.getLine();
                    defaultTxLineCol = PresetColor.QUEST_LINE_REPEATABLE.getColor();
                    break;
            }

            for (Map.Entry<UUID, IQuest> req : reqMap.entrySet()) {
                PanelButtonQuest parBtn = questBtns.get(req.getKey());
                IGuiColor txLineCol = defaultTxLineCol;

                if (parBtn != null) {
                    RequirementType type = quest.getValue()
                        .getRequirementType(req.getKey());
                    ShouldDrawPredicate predicate = null;
                    ShouldDrawPredicate animatePredicate = null;

                    PanelButtonQuest reqQuestButton = questBtns.get(req.getKey());
                    PanelButtonQuest depQuestButton = questBtns.get(quest.getKey());

                    if (BQ_Settings.animateDependencyArrows) {
                        animatePredicate = (mx, my, partialTicks) -> reqQuestButton.rect.contains(mx, my)
                            || depQuestButton.rect.contains(mx, my);
                    }

                    switch (type) {
                        case NORMAL:
                            break;
                        case IMPLICIT:
                            if (BQ_Settings.alwaysDrawImplicit) break;
                            predicate = (mx, my, partialTicks) -> reqQuestButton.rect.contains(mx, my)
                                || depQuestButton.rect.contains(mx, my)
                                || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                            txLineCol = new GuiColorPulse(
                                txLineCol,
                                PresetColor.QUEST_LINE_IMPLICIT_MIXIN.getColor(),
                                2F,
                                0F);
                            break;
                        default:
                            // bail early
                            continue;
                    }

                    this.addCulledPanel(
                        new PanelLine(
                            parBtn.getTransform(),
                            entry.getValue()
                                .getTransform(),
                            lineRender,
                            main ? 8 : 4,
                            txLineCol,
                            1,
                            predicate,
                            animatePredicate),
                        false);
                }
            }
        }

        for (PanelButtonQuest button : btnList) {
            this.addPanel(button);
        }

        fitToWindow();
    }

    public void fitToWindow() {
        // Used later to center focus the quest line within the window
        boolean flag = false;
        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;

        for (PanelButtonQuest btn : btnList) {
            GuiRectangle rect = btn.rect;

            if (!flag) {
                minX = rect.getX();
                minY = rect.getY();
                maxX = minX + rect.getWidth();
                maxY = minY + rect.getHeight();
                flag = true;
            } else {
                minX = Math.min(minX, rect.getX());
                minY = Math.min(minY, rect.getY());
                maxX = Math.max(maxX, rect.getX() + rect.getWidth());
                maxY = Math.max(maxY, rect.getY() + rect.getHeight());
            }
        }

        minX -= zoomToFitMargin;
        minY -= zoomToFitMargin;
        maxX += zoomToFitMargin;
        maxY += zoomToFitMargin;

        this.setZoom(
            Math.min(
                getTransform().getWidth() / (float) (maxX - minX),
                getTransform().getHeight() / (float) (maxY - minY)));
        this.refreshScrollBounds();

        IGuiRect bounds = getScrollBounds();
        this.setScrollX(bounds.getX() + bounds.getWidth() / 2);
        this.setScrollY(bounds.getY() + bounds.getHeight() / 2);
        this.updatePanelScroll();
    }

    public void centerOn(PanelButtonQuest btn) {
        int x = btn.rect.x;
        int y = btn.rect.y;
        int width = btn.rect.w;
        int height = btn.rect.h;
        int btnCenterX = x + width / 2;
        int btnCenterY = y + height / 2;

        this.setScrollX(btnCenterX - scrollWindow.w / 2);
        this.setScrollY(btnCenterY - scrollWindow.h / 2);
    }

    private static final class QuestButtonCache {

        private Framebuffer framebuffer;
        private boolean valid;
        private int lastMode = -1;
        private long nextRefresh;
        private long retryAt;
        private WeakReference<Object> lastOwner = new WeakReference<>(null);
        private int lastX;
        private int lastY;
        private int lastWidth;
        private int lastHeight;
        private int lastScrollX;
        private int lastScrollY;
        private int lastContentHash;
        private float lastZoom;

        private QuestButtonCache() {
            ((IReloadableResourceManager) Minecraft.getMinecraft()
                .getResourceManager()).registerReloadListener(resourceManager -> invalidate());
        }

        private boolean isEnabled() {
            int mode = Math.max(0, Math.min(2, BQ_Settings.questIconCacheMode));
            if (mode != lastMode) {
                lastMode = mode;
                retryAt = 0;
                invalidate();
                if (mode == 0) release();
            }
            return mode > 0 && OpenGlHelper.isFramebufferEnabled() && System.currentTimeMillis() >= retryAt;
        }

        private void invalidate() {
            valid = false;
            nextRefresh = 0;
        }

        private void release() {
            invalidate();
            if (framebuffer != null) framebuffer.deleteFramebuffer();
            framebuffer = null;
            lastOwner.clear();
        }

        private void fail(RuntimeException e) {
            invalidate();
            retryAt = System.currentTimeMillis() + 5000L;
            BetterQuesting.logger.warn("Unable to cache quest icons", e);
        }

        private void draw(Object owner, IGuiRect bounds, int scrollX, int scrollY, float zoom, int contentHash,
            Runnable drawButtons, Runnable captureButtons) {
            int mode = Math.max(0, Math.min(2, BQ_Settings.questIconCacheMode));
            boolean viewChanged = updateView(owner, bounds, scrollX, scrollY, zoom, contentHash);

            if (viewChanged) {
                invalidate();
                drawButtons.run();
                return;
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            Framebuffer cache = getFramebuffer(minecraft);
            long now = System.currentTimeMillis();
            if (!valid || cache.framebufferWidth != minecraft.displayWidth
                || cache.framebufferHeight != minecraft.displayHeight
                || mode == 1 && now >= nextRefresh) {
                capture(cache, minecraft, captureButtons);
                nextRefresh = mode == 1 ? now + 1000L / Math.max(1, BQ_Settings.questIconCacheFps) : Long.MAX_VALUE;
                valid = true;
            }

            render(cache, minecraft, bounds);
        }

        private boolean updateView(Object owner, IGuiRect bounds, int scrollX, int scrollY, float zoom,
            int contentHash) {
            boolean changed = owner != lastOwner.get() || bounds.getX() != lastX
                || bounds.getY() != lastY
                || bounds.getWidth() != lastWidth
                || bounds.getHeight() != lastHeight
                || scrollX != lastScrollX
                || scrollY != lastScrollY
                || contentHash != lastContentHash
                || Float.floatToIntBits(zoom) != Float.floatToIntBits(lastZoom);
            lastOwner = new WeakReference<>(owner);
            lastX = bounds.getX();
            lastY = bounds.getY();
            lastWidth = bounds.getWidth();
            lastHeight = bounds.getHeight();
            lastScrollX = scrollX;
            lastScrollY = scrollY;
            lastContentHash = contentHash;
            lastZoom = zoom;
            return changed;
        }

        private Framebuffer getFramebuffer(Minecraft minecraft) {
            if (framebuffer == null) {
                framebuffer = new Framebuffer(1, 1, true);
                framebuffer.setFramebufferColor(0F, 0F, 0F, 0F);
                minecraft.getFramebuffer()
                    .bindFramebuffer(false);
            }
            return framebuffer;
        }

        private void capture(Framebuffer cache, Minecraft minecraft, Runnable drawButtons) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            try {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                if (cache.framebufferWidth != minecraft.displayWidth
                    || cache.framebufferHeight != minecraft.displayHeight) {
                    cache.createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);
                    cache.setFramebufferFilter(GL11.GL_NEAREST);
                } else {
                    cache.framebufferClear();
                }
                cache.bindFramebuffer(false);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(true);
                OpenGlHelper.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1F, 1F, 1F, 1F);
                drawButtons.run();
            } finally {
                GL11.glPopAttrib();
                minecraft.getFramebuffer()
                    .bindFramebuffer(false);
            }
        }

        private void render(Framebuffer cache, Minecraft minecraft, IGuiRect bounds) {
            ScaledResolution resolution = new ScaledResolution(
                minecraft,
                minecraft.displayWidth,
                minecraft.displayHeight);
            double screenWidth = resolution.getScaledWidth_double();
            double screenHeight = resolution.getScaledHeight_double();
            double left = bounds.getX();
            double top = bounds.getY();
            double right = left + bounds.getWidth();
            double bottom = top + bounds.getHeight();
            double u0 = left / screenWidth;
            double u1 = right / screenWidth;
            double v0 = 1D - bottom / screenHeight;
            double v1 = 1D - top / screenHeight;

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            cache.bindFramebufferTexture();
            try {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                OpenGlHelper
                    .glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1F, 1F, 1F, 1F);
                GL11.glEnable(GL11.GL_TEXTURE_2D);

                Tessellator tessellator = Tessellator.instance;
                tessellator.startDrawingQuads();
                tessellator.addVertexWithUV(left, bottom, 0D, u0, v0);
                tessellator.addVertexWithUV(right, bottom, 0D, u1, v0);
                tessellator.addVertexWithUV(right, top, 0D, u1, v1);
                tessellator.addVertexWithUV(left, top, 0D, u0, v1);
                tessellator.draw();
            } finally {
                cache.unbindFramebufferTexture();
                GL11.glPopAttrib();
                GL11.glColor4f(1F, 1F, 1F, 1F);
            }
        }
    }
}
