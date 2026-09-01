package betterquesting.api2.client.gui.resources.textures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import cpw.mods.fml.client.config.GuiUtils;

public class SlicedTexture implements IGuiTexture {

    // The 4096th textured quad forces Tessellator past its retained 0x20000-int buffer.
    private static final int MAX_BATCH_QUADS = 4095;
    private static final float TEXEL = 1F / 256F;
    private static final IGuiColor defColor = new GuiColorStatic(255, 255, 255, 255);

    private final ResourceLocation texture;
    private final IGuiRect texBounds;
    private final GuiPadding texBorder;
    private SliceMode sliceMode = SliceMode.SLICED_TILE;

    public SlicedTexture(ResourceLocation tex, IGuiRect bounds, GuiPadding border) {
        this.texture = tex;
        this.texBounds = bounds;
        this.texBorder = border;
    }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick) {
        drawTexture(x, y, width, height, zLevel, partialTick, defColor);
    }

    @Override
    public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick, IGuiColor color) {
        if (width <= 0 || height <= 0) return;

        int w = Math.max(width, texBorder.getLeft() + texBorder.getRight());
        int h = Math.max(height, texBorder.getTop() + texBorder.getBottom());
        int dx = x;
        int dy = y;

        GL11.glPushMatrix();
        color.applyGlColor();

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper
            .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        if (w != width || h != height) {
            dx = 0;
            dy = 0;
            GL11.glTranslatef(x, y, 0);
            GL11.glScaled(width / (double) w, height / (double) h, 1D);
        }

        if (sliceMode == SliceMode.SLICED_TILE) {
            drawContinuousTexturedBox(
                texture,
                dx,
                dy,
                texBounds.getX(),
                texBounds.getY(),
                w,
                h,
                texBounds.getWidth(),
                texBounds.getHeight(),
                texBorder.getTop(),
                texBorder.getBottom(),
                texBorder.getLeft(),
                texBorder.getRight(),
                zLevel);
        } else if (sliceMode == SliceMode.SLICED_STRETCH) {
            int iu = texBounds.getX() + texBorder.getLeft();
            int iv = texBounds.getY() + texBorder.getTop();
            int iw = texBounds.getWidth() - texBorder.getLeft() - texBorder.getRight();
            int ih = texBounds.getHeight() - texBorder.getTop() - texBorder.getBottom();
            if (iw < 0 || ih < 0) {
                GL11.glPopMatrix();
                return;
            }
            int sw = w - texBorder.getLeft() - texBorder.getRight();
            int sh = h - texBorder.getTop() - texBorder.getBottom();

            Minecraft.getMinecraft().renderEngine.bindTexture(texture);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            int batchSize = 0;

            // TOP LEFT
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx,
                dy,
                texBorder.getLeft(),
                texBorder.getTop(),
                texBounds.getX(),
                texBounds.getY(),
                texBorder.getLeft(),
                texBorder.getTop(),
                zLevel);

            // TOP SIDE
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx + texBorder.getLeft(),
                dy,
                sw,
                texBorder.getTop(),
                texBounds.getX() + texBorder.getLeft(),
                texBounds.getY(),
                iw,
                texBorder.getTop(),
                zLevel);

            // TOP RIGHT
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx + w - texBorder.getRight(),
                dy,
                texBorder.getRight(),
                texBorder.getTop(),
                texBounds.getX() + texBorder.getLeft() + iw,
                texBounds.getY(),
                texBorder.getRight(),
                texBorder.getTop(),
                zLevel);

            // LEFT SIDE
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx,
                dy + texBorder.getTop(),
                texBorder.getLeft(),
                sh,
                texBounds.getX(),
                texBounds.getY() + texBorder.getTop(),
                texBorder.getLeft(),
                ih,
                zLevel);

            // MIDDLE
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx + texBorder.getLeft(),
                dy + texBorder.getTop(),
                sw,
                sh,
                iu,
                iv,
                iw,
                ih,
                zLevel);

            // RIGHT SIDE
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx + w - texBorder.getRight(),
                dy + texBorder.getTop(),
                texBorder.getRight(),
                sh,
                texBounds.getX() + texBorder.getLeft() + iw,
                texBounds.getY() + texBorder.getTop(),
                texBorder.getRight(),
                ih,
                zLevel);

            // BOTTOM LEFT
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx,
                dy + h - texBorder.getBottom(),
                texBorder.getLeft(),
                texBorder.getBottom(),
                texBounds.getX(),
                texBounds.getY() + texBorder.getTop() + ih,
                texBorder.getLeft(),
                texBorder.getBottom(),
                zLevel);

            // BOTTOM SIDE
            batchSize = addTexturedQuad(
                tessellator,
                batchSize,
                dx + texBorder.getLeft(),
                dy + h - texBorder.getBottom(),
                sw,
                texBorder.getBottom(),
                texBounds.getX() + texBorder.getLeft(),
                texBounds.getY() + texBorder.getTop() + ih,
                iw,
                texBorder.getBottom(),
                zLevel);

            // BOTTOM RIGHT
            addTexturedQuad(
                tessellator,
                batchSize,
                dx + w - texBorder.getRight(),
                dy + h - texBorder.getBottom(),
                texBorder.getRight(),
                texBorder.getBottom(),
                texBounds.getX() + texBorder.getLeft() + iw,
                texBounds.getY() + texBorder.getTop() + ih,
                texBorder.getRight(),
                texBorder.getBottom(),
                zLevel);
            tessellator.draw();
        } else {
            float sx = (float) w / (float) texBounds.getWidth();
            float sy = (float) h / (float) texBounds.getHeight();
            GL11.glTranslatef(dx, dy, 0F);
            GL11.glScalef(sx, sy, 1F);

            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper
                .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Minecraft.getMinecraft().renderEngine.bindTexture(texture);
            GuiUtils.drawTexturedModalRect(
                0,
                0,
                texBounds.getX(),
                texBounds.getY(),
                texBounds.getWidth(),
                texBounds.getHeight(),
                zLevel);
        }

        GL11.glPopMatrix();
    }

    @Override
    public ResourceLocation getTexture() {
        return this.texture;
    }

    @Override
    public IGuiRect getBounds() {
        return this.texBounds;
    }

    public GuiPadding getBorder() {
        return this.texBorder;
    }

    public SliceMode getSliceMode() {
        return sliceMode;
    }

    /**
     * Enables texture slicing. Will stretch to fit if disabled
     */
    public SlicedTexture setSliceMode(SliceMode mode) {
        this.sliceMode = mode;
        return this;
    }

    public static SlicedTexture readFromJson(JsonObject json) {
        ResourceLocation res = new ResourceLocation(JsonHelper.GetString(json, "texture", "minecraft:missingno"));
        int slice = JsonHelper.GetNumber(json, "sliceMode", 1)
            .intValue();

        JsonObject jOut = JsonHelper.GetObject(json, "coordinates");
        int ox = JsonHelper.GetNumber(jOut, "u", 0)
            .intValue();
        int oy = JsonHelper.GetNumber(jOut, "v", 0)
            .intValue();
        int ow = JsonHelper.GetNumber(jOut, "w", 48)
            .intValue();
        int oh = JsonHelper.GetNumber(jOut, "h", 48)
            .intValue();

        JsonObject jIn = JsonHelper.GetObject(json, "border");
        int il = JsonHelper.GetNumber(jIn, "l", 16)
            .intValue();
        int it = JsonHelper.GetNumber(jIn, "t", 16)
            .intValue();
        int ir = JsonHelper.GetNumber(jIn, "r", 16)
            .intValue();
        int ib = JsonHelper.GetNumber(jIn, "b", 16)
            .intValue();

        return new SlicedTexture(res, new GuiRectangle(ox, oy, ow, oh), new GuiPadding(il, it, ir, ib))
            .setSliceMode(SliceMode.VALUES[slice % 3]);
    }

    // Slightly modified version from GuiUtils.class
    private static void drawContinuousTexturedBox(ResourceLocation res, int x, int y, int u, int v, int width,
        int height, int textureWidth, int textureHeight, int topBorder, int bottomBorder, int leftBorder,
        int rightBorder, float zLevel) {
        Minecraft.getMinecraft().renderEngine.bindTexture(res);

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper
            .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int fillerWidth = textureWidth - leftBorder - rightBorder;
        int fillerHeight = textureHeight - topBorder - bottomBorder;
        if (fillerWidth <= 0 || fillerHeight <= 0) return;
        int canvasWidth = width - leftBorder - rightBorder;
        int canvasHeight = height - topBorder - bottomBorder;
        int xPasses = canvasWidth / fillerWidth;
        int remainderWidth = canvasWidth % fillerWidth;
        int yPasses = canvasHeight / fillerHeight;
        int remainderHeight = canvasHeight % fillerHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        int batchSize = 0;

        // Draw Border
        // Top Left
        batchSize = addTexturedTile(tessellator, batchSize, x, y, u, v, leftBorder, topBorder, zLevel);
        // Top Right
        batchSize = addTexturedTile(
            tessellator,
            batchSize,
            x + leftBorder + canvasWidth,
            y,
            u + leftBorder + fillerWidth,
            v,
            rightBorder,
            topBorder,
            zLevel);
        // Bottom Left
        batchSize = addTexturedTile(
            tessellator,
            batchSize,
            x,
            y + topBorder + canvasHeight,
            u,
            v + topBorder + fillerHeight,
            leftBorder,
            bottomBorder,
            zLevel);
        // Bottom Right
        batchSize = addTexturedTile(
            tessellator,
            batchSize,
            x + leftBorder + canvasWidth,
            y + topBorder + canvasHeight,
            u + leftBorder + fillerWidth,
            v + topBorder + fillerHeight,
            rightBorder,
            bottomBorder,
            zLevel);

        for (int i = 0; i < xPasses + (remainderWidth > 0 ? 1 : 0); i++) {
            int drawWidth = i == xPasses ? remainderWidth : fillerWidth;
            // Top Border
            batchSize = addTexturedTile(
                tessellator,
                batchSize,
                x + leftBorder + (i * fillerWidth),
                y,
                u + leftBorder,
                v,
                drawWidth,
                topBorder,
                zLevel);
            // Bottom Border
            batchSize = addTexturedTile(
                tessellator,
                batchSize,
                x + leftBorder + (i * fillerWidth),
                y + topBorder + canvasHeight,
                u + leftBorder,
                v + topBorder + fillerHeight,
                drawWidth,
                bottomBorder,
                zLevel);

            // Throw in some filler for good measure
            for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
                int drawHeight = j == yPasses ? remainderHeight : fillerHeight;
                batchSize = addTexturedTile(
                    tessellator,
                    batchSize,
                    x + leftBorder + (i * fillerWidth),
                    y + topBorder + (j * fillerHeight),
                    u + leftBorder,
                    v + topBorder,
                    drawWidth,
                    drawHeight,
                    zLevel);
            }
        }

        // Side Borders
        for (int j = 0; j < yPasses + (remainderHeight > 0 ? 1 : 0); j++) {
            int drawHeight = j == yPasses ? remainderHeight : fillerHeight;
            // Left Border
            batchSize = addTexturedTile(
                tessellator,
                batchSize,
                x,
                y + topBorder + (j * fillerHeight),
                u,
                v + topBorder,
                leftBorder,
                drawHeight,
                zLevel);
            // Right Border
            batchSize = addTexturedTile(
                tessellator,
                batchSize,
                x + leftBorder + canvasWidth,
                y + topBorder + (j * fillerHeight),
                u + leftBorder + fillerWidth,
                v + topBorder,
                rightBorder,
                drawHeight,
                zLevel);
        }
        tessellator.draw();
    }

    private static int addTexturedTile(Tessellator tessellator, int batchSize, int x, int y, int u, int v, int width,
        int height, float zLevel) {
        return addTexturedQuad(tessellator, batchSize, x, y, width, height, u, v, width, height, zLevel);
    }

    private static int addTexturedQuad(Tessellator tessellator, int batchSize, int x, int y, int width, int height,
        int u, int v, int textureWidth, int textureHeight, float zLevel) {
        if (width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) return batchSize;
        if (batchSize >= MAX_BATCH_QUADS) {
            tessellator.draw();
            tessellator.startDrawingQuads();
            batchSize = 0;
        }

        float minU = u * TEXEL;
        float minV = v * TEXEL;
        float maxU = (u + textureWidth) * TEXEL;
        float maxV = (v + textureHeight) * TEXEL;
        tessellator.addVertexWithUV(x, y + height, zLevel, minU, maxV);
        tessellator.addVertexWithUV(x + width, y + height, zLevel, maxU, maxV);
        tessellator.addVertexWithUV(x + width, y, zLevel, maxU, minV);
        tessellator.addVertexWithUV(x, y, zLevel, minU, minV);
        return batchSize + 1;
    }

    public enum SliceMode {

        STRETCH,
        SLICED_TILE,
        SLICED_STRETCH;

        public static final SliceMode[] VALUES = values();
    }
}
