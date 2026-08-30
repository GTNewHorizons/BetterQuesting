package betterquesting.api2.client.gui.controls;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.resources.textures.SimpleTexture;
import betterquesting.api2.client.gui.resources.textures.SlicedTexture;

final class QuestFrameCache implements IGuiTexture {

    private static final int FRAME_SIZE = 24;
    private static final int MAX_ENTRIES = 128;
    private static final Map<IGuiTexture, QuestFrameCache> CACHE = new IdentityHashMap<>();
    private static final ArrayDeque<IGuiTexture> INSERTION_ORDER = new ArrayDeque<>();
    private static final IGuiColor WHITE_COLOR = new GuiColorStatic(0xFFFFFFFF);
    private static final IGuiColor NO_OP_COLOR = new GuiColorStatic(0xFFFFFFFF) {

        @Override
        public void applyGlColor() {}
    };

    static {
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(resourceManager -> clear());
    }

    private final IGuiTexture texture;
    private int displayList;
    private RenderState renderState;
    private boolean active = true;

    private QuestFrameCache(IGuiTexture texture) {
        this.texture = texture;
    }

    static IGuiTexture wrap(IGuiTexture texture) {
        if (texture == null || (texture.getClass() != SimpleTexture.class && texture.getClass() != SlicedTexture.class))
            return texture;

        QuestFrameCache cached = CACHE.get(texture);
        if (cached != null) return cached;

        if (CACHE.size() >= MAX_ENTRIES) {
            QuestFrameCache evicted = CACHE.remove(INSERTION_ORDER.removeFirst());
            evicted.active = false;
            evicted.deleteDisplayList();
        }

        QuestFrameCache created = new QuestFrameCache(texture);
        CACHE.put(texture, created);
        INSERTION_ORDER.addLast(texture);
        return created;
    }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zDepth, float partialTick) {
        drawTexture(x, y, width, height, zDepth, partialTick, WHITE_COLOR);
    }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zDepth, float partialTick, IGuiColor color) {
        if (!active || width != FRAME_SIZE || height != FRAME_SIZE) {
            texture.drawTexture(x, y, width, height, zDepth, partialTick, color);
            return;
        }

        if (displayList == 0 || !renderState.matches(texture)) compileDisplayList();
        if (displayList == 0) {
            texture.drawTexture(x, y, width, height, zDepth, partialTick, color);
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, zDepth);
        color.applyGlColor();
        GL11.glCallList(displayList);
        GL11.glPopMatrix();
    }

    @Override
    public ResourceLocation getTexture() {
        return texture.getTexture();
    }

    @Override
    public IGuiRect getBounds() {
        return texture.getBounds();
    }

    private void compileDisplayList() {
        deleteDisplayList();
        RenderState newState = new RenderState(texture);
        Minecraft.getMinecraft().renderEngine.bindTexture(texture.getTexture());
        int newDisplayList = GL11.glGenLists(1);
        if (newDisplayList == 0) {
            active = false;
            return;
        }

        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glNewList(newDisplayList, GL11.GL_COMPILE);
        boolean compiled = false;
        try {
            texture.drawTexture(0, 0, FRAME_SIZE, FRAME_SIZE, 0F, 0F, NO_OP_COLOR);
            compiled = true;
        } finally {
            GL11.glEndList();
            if (!compiled) GL11.glDeleteLists(newDisplayList, 1);
        }

        displayList = newDisplayList;
        renderState = newState;
    }

    private void deleteDisplayList() {
        if (displayList != 0) GL11.glDeleteLists(displayList, 1);
        displayList = 0;
        renderState = null;
    }

    private static void clear() {
        for (QuestFrameCache cached : CACHE.values()) {
            cached.deleteDisplayList();
        }
    }

    private static final class RenderState {

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int mode;

        private RenderState(IGuiTexture texture) {
            IGuiRect bounds = texture.getBounds();
            x = bounds.getX();
            y = bounds.getY();
            width = bounds.getWidth();
            height = bounds.getHeight();

            if (texture instanceof SlicedTexture) {
                SlicedTexture sliced = (SlicedTexture) texture;
                GuiPadding border = sliced.getBorder();
                left = border.getLeft();
                top = border.getTop();
                right = border.getRight();
                bottom = border.getBottom();
                mode = getMode(sliced);
            } else {
                left = 0;
                top = 0;
                right = 0;
                bottom = 0;
                mode = ((SimpleTexture) texture).isMaintainingAspect() ? 1 : 0;
            }
        }

        private boolean matches(IGuiTexture texture) {
            IGuiRect bounds = texture.getBounds();
            if (x != bounds.getX() || y != bounds.getY() || width != bounds.getWidth() || height != bounds.getHeight())
                return false;

            if (texture instanceof SlicedTexture) {
                SlicedTexture sliced = (SlicedTexture) texture;
                GuiPadding border = sliced.getBorder();
                return left == border.getLeft() && top == border.getTop()
                    && right == border.getRight()
                    && bottom == border.getBottom()
                    && mode == getMode(sliced);
            }

            return mode == (((SimpleTexture) texture).isMaintainingAspect() ? 1 : 0);
        }

        private static int getMode(SlicedTexture texture) {
            return texture.getSliceMode() == null ? -1
                : texture.getSliceMode()
                    .ordinal();
        }
    }
}
