package betterquesting.api.utils;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.vector.Matrix4f;

import com.gtnewhorizon.gtnhlib.util.font.FontRendering;

import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

// TODO: Move text related stuff to its own utility class
@SideOnly(Side.CLIENT)
public class RenderUtils {

    public static final String REGEX_NUMBER = "[^\\.0123456789-]"; // I keep screwing this up so now it's reusable
    public static final RenderItem itemRender = new RenderItem();

    private static final int SPLIT_STRING_TRIAL_LIMIT = 1000;
    private static final Stack<IGuiRect> scissorStack = new Stack<>();

    public static void RenderItemStack(Minecraft mc, ItemStack stack, int x, int y, String text) {
        RenderItemStack(mc, stack, x, y, 16F, text, 0xFFFFFFFF);
    }

    public static void RenderItemStack(Minecraft mc, ItemStack stack, int x, int y, String text, Color color) {
        RenderItemStack(mc, stack, x, y, 16F, text, color.getRGB());
    }

    public static void RenderItemStack(Minecraft mc, ItemStack stack, int x, int y, String text, int color) {
        RenderItemStack(mc, stack, x, y, 16F, text, color);
    }

    public static void RenderItemStack(Minecraft mc, ItemStack stack, int x, int y, float z, String text, int color) {
        if (stack == null) return;

        GL11.glPushMatrix();
        float preZ = itemRender.zLevel;

        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glColor3f(r, g, b);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        GL11.glTranslatef(0.0F, 0.0F, z);
        itemRender.zLevel = -50F; // Counters internal Z depth change so that GL translation makes sense // NOTE:
        // Slightly different depth in 1.7.10

        FontRenderer font = stack.getItem()
            .getFontRenderer(stack);
        if (font == null) font = mc.fontRenderer;

        try {
            itemRender.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x, y);

            if (stack.stackSize != 1 || text != null) {
                GL11.glPushMatrix();

                int w = getStringWidth(text, font);
                float tx;
                float ty;
                float s = 1F;

                if (w > 17) {
                    s = 17F / w;
                    tx = 0;
                    ty = 17 - font.FONT_HEIGHT * s;
                } else {
                    tx = 17 - w;
                    ty = 18 - font.FONT_HEIGHT;
                }

                GL11.glTranslatef(x + tx, y + ty, 0);
                GL11.glScalef(s, s, 1F);

                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_BLEND);

                font.drawString(text, 0, 0, 16777215, true);

                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);

                GL11.glPopMatrix();
            }

            itemRender.renderItemOverlayIntoGUI(font, mc.getTextureManager(), stack, x, y, "");
        } catch (Exception e) {
            BetterQuesting.logger.warn("Unabled to render item " + stack, e);
        }

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderHelper.disableStandardItemLighting();

        itemRender.zLevel = preZ; // Just in case

        GL11.glPopMatrix();
    }

    public static void RenderEntity(int posX, int posY, int scale, float rotation, float pitch, Entity entity) {
        RenderEntity(posX, posY, 64F, scale, rotation, pitch, entity);
    }

    public static void RenderEntity(float posX, float posY, float posZ, int scale, float rotation, float pitch,
        Entity entity) {
        try {
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glTranslatef(posX, posY, posZ);
            GL11.glScalef((float) -scale, (float) scale, (float) scale); // Not entirely sure why mobs are flipped but
            // this is how vanilla GUIs fix it so...
            GL11.glRotatef(180F, 0F, 0F, 1F);
            GL11.glRotatef(pitch, 1F, 0F, 0F);
            GL11.glRotatef(rotation, 0F, 1F, 0F);
            RenderHelper.enableStandardItemLighting();
            RenderManager.instance.playerViewY = 180F;
            RenderManager.instance.renderEntityWithPosYaw(entity, 0D, 0D, 0D, 0F, 1F);
            doSpecialRenders(entity);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D); // Breaks subsequent text rendering if not included
        } catch (Exception e) {
            // Hides rendering errors with entities which are common for invalid/technical entities
        }
    }

    private static void doSpecialRenders(Entity entity) {
        String entityString = entity.getClass()
            .getSimpleName();
        if (entityString.equals("EntityTFNaga")) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            int bodySize = 11; // 0 to 12
            Entity part = entity.getParts()[0];
            float[][] xyzYaw = getNagaXyzYaw();
            for (int i = 0; i < bodySize; i++) RenderManager.instance
                .renderEntityWithPosYaw(part, xyzYaw[i][0], xyzYaw[i][1], xyzYaw[i][2], xyzYaw[i][3], 1F);
        } else if (entityString.equals("EntityTFHydra")) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            int headsNumber = 7; // 0 to 7, optimal 3, 5, 7
            Entity part = EntityList.createEntityByName("TwilightForest.HydraHead", Minecraft.getMinecraft().theWorld);
            part.rotationYaw = 0;
            part.rotationPitch = 0;
            float[][] xyz = getHydraHeadXyz();
            for (int i = 0; i < headsNumber; i++)
                RenderManager.instance.renderEntityWithPosYaw(part, xyz[i][0], xyz[i][1], xyz[i][2], 0F, 1F);
            part = entity.getParts()[4];
            xyz = getHydraNeckXyz();
            for (int i = 0; i < 5 * headsNumber; i++)
                RenderManager.instance.renderEntityWithPosYaw(part, xyz[i][0], xyz[i][1], xyz[i][2], 0F, 1F);
        }
    }

    private static float[][] getHydraHeadXyz() {
        return new float[][] { { 0.0F, 9.1F, 3.6F }, { -7.5F, 5.5F, 4.4F }, { 7.5F, 5.5F, 4.4F }, { -5.1F, 9.1F, 0.0F },
            { 5.1F, 9.1F, 0.0F }, { -8.9F, 1.5F, 0.0F }, { 8.9F, 1.5F, 0.0F }, };
    }

    private static float[][] getHydraNeckXyz() {
        return new float[][] { { 0.0F, 9.0F, 2.6F }, { 0.0F, 7.5F, 1.7F }, { 0.0F, 6.0F, 0.8F }, { 0.0F, 4.5F, -0.1F },
            { 0.0F, 3.0F, -1.0F }, { -7.4F, 5.4F, 3.4F }, { -6.3F, 4.8F, 2.3F }, { -5.2F, 4.2F, 1.2F },
            { -4.1F, 3.6F, 0.1F }, { -3.0F, 3.0F, -1.0F }, { 7.4F, 5.4F, 3.4F }, { 6.3F, 4.8F, 2.3F },
            { 5.2F, 4.2F, 1.2F }, { 4.1F, 3.6F, 0.1F }, { 3.0F, 3.0F, -1.0F }, { -5.0F, 9.0F, -1.0F },
            { -4.1F, 7.5F, -1.4F }, { -3.2F, 6.0F, -1.9F }, { -2.3F, 4.5F, -2.3F }, { -1.4F, 3.0F, -2.8F },
            { 5.0F, 9.0F, -1.0F }, { 4.1F, 7.5F, -1.4F }, { 3.2F, 6.0F, -1.9F }, { 2.3F, 4.5F, -2.3F },
            { 1.4F, 3.0F, -2.8F }, { -8.8F, 1.4F, -1.0F }, { -7.3F, 1.8F, -1.8F }, { -5.8F, 2.2F, -2.6F },
            { -4.3F, 2.6F, -3.4F }, { -2.8F, 3.0F, -4.2F }, { 8.8F, 1.4F, -1.0F }, { 7.3F, 1.8F, -1.8F },
            { 5.8F, 2.2F, -2.6F }, { 4.3F, 2.6F, -3.4F }, { 2.8F, 3.0F, -4.2F } };
    }

    private static float[][] getNagaXyzYaw() {
        return new float[][] { { 0, 0, -2, 0 }, { 0, -2, -2, 0 }, { -1.366F, -2, -1.634F, -30 },
            { -2.366F, -2, -.634F, -60 }, { -2.732F, -2, .732F, -90 }, { -2.366F, -2, 2.098F, -120 },
            { -1.366F, -2, 3.098F, -150 }, { 0, -2, 3.464F, 0 }, { 1.366F, -2, 3.098F, -30 },
            { 2.366F, -2, 2.098F, -60 }, { 2.732F, -2, .732F, -90 }, { 2.366F, -2, -.634F, -120 } };
    }

    public static void DrawLine(int x1, int y1, int x2, int y2, float width, int color) {
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, 1F);
        GL11.glLineWidth(width);

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        GL11.glPopMatrix();
    }

    public static void drawSplitString(FontRenderer renderer, String string, int x, int y, int width, int color,
        boolean shadow) {
        drawSplitString(
            renderer,
            string,
            x,
            y,
            width,
            color,
            shadow,
            0,
            splitString(string, width, renderer).size() - 1);
    }

    // TODO: Clean this up. The list of parameters is getting a bit excessive

    public static void drawSplitString(FontRenderer renderer, String string, int x, int y, int width, int color,
        boolean shadow, int start, int end) {
        drawHighlightedSplitString(renderer, string, x, y, width, color, shadow, start, end, 0, 0, 0);
    }

    public static void drawHighlightedSplitString(FontRenderer renderer, String string, int x, int y, int width,
        int color, boolean shadow, int highlightColor, int highlightStart, int highlightEnd) {
        drawHighlightedSplitString(
            renderer,
            string,
            x,
            y,
            width,
            color,
            shadow,
            0,
            splitString(string, width, renderer).size() - 1,
            highlightColor,
            highlightStart,
            highlightEnd);
    }

    public static void drawHighlightedSplitString(FontRenderer renderer, String string, int x, int y, int width,
        int color, boolean shadow, int start, int end, int highlightColor, int highlightStart, int highlightEnd) {
        if (renderer == null || string == null || string.length() <= 0 || start > end) {
            return;
        }

        string = string.replaceAll("\r", ""); // Line endings from localizations break things so we remove them

        List<String> list = splitString(string, width, renderer);
        List<String> noFormat = splitStringWithoutFormat(string, width, renderer); // Needed for accurate highlight
        // index positions

        if (list.size() != noFormat.size()) {
            // BetterQuesting.logger.error("Line count mismatch (" + list.size() + " != " + noFormat.size() + ") while
            // drawing formatted text!");
            return;
        }

        int hlStart = Math.min(highlightStart, highlightEnd);
        int hlEnd = Math.max(highlightStart, highlightEnd);
        int idxStart = 0;

        for (int i = 0; i < start; i++) {
            if (i >= noFormat.size()) {
                break;
            }

            idxStart += noFormat.get(i)
                .length();
        }

        for (int i = start; i <= end; i++) {
            if (i < 0 || i >= list.size()) {
                continue;
            }

            renderer.drawString(list.get(i), x, y + (renderer.FONT_HEIGHT * (i - start)), color, shadow);

            // DEBUG
            /*
             * boolean b = (System.currentTimeMillis()/1000)%2 == 0;
             * if(b)
             * {
             * renderer.drawString(i + ": " + list.get(i), x, y + (renderer.FONT_HEIGHT * (i - start)), color, shadow);
             * }
             * if(i >= noFormat.size())
             * {
             * continue;
             * }
             * if(!b)
             * {
             * renderer.drawString(i + ": " + noFormat.get(i), x, y + (renderer.FONT_HEIGHT * (i - start)), color,
             * shadow);
             * }
             */

            int lineSize = noFormat.get(i)
                .length();
            int idxEnd = idxStart + lineSize;

            int i1 = Math.max(idxStart, hlStart) - idxStart;
            int i2 = Math.min(idxEnd, hlEnd) - idxStart;

            if (!(i1 == i2 || i1 < 0 || i2 < 0 || i1 > lineSize || i2 > lineSize)) {
                String lastFormat = getFormatFromString(list.get(i));
                int x1 = getStringWidth(
                    lastFormat + noFormat.get(i)
                        .substring(0, i1),
                    renderer);
                int x2 = getStringWidth(
                    lastFormat + noFormat.get(i)
                        .substring(0, i2),
                    renderer);

                drawHighlightBox(
                    x + x1,
                    y + (renderer.FONT_HEIGHT * (i - start)),
                    x + x2,
                    y + (renderer.FONT_HEIGHT * (i - start)) + renderer.FONT_HEIGHT,
                    highlightColor);
            }

            idxStart = idxEnd;
        }
    }

    public static void drawHighlightedString(FontRenderer renderer, String string, int x, int y, int color,
        boolean shadow, int highlightColor, int highlightStart, int highlightEnd) {
        if (renderer == null || string == null || string.length() <= 0) {
            return;
        }

        renderer.drawString(string, x, y, color, shadow);

        int hlStart = Math.min(highlightStart, highlightEnd);
        int hlEnd = Math.max(highlightStart, highlightEnd);
        int size = string.length();

        int i1 = MathHelper.clamp_int(hlStart, 0, size);
        int i2 = MathHelper.clamp_int(hlEnd, 0, size);

        if (i1 != i2) {
            int x1 = getStringWidth(string.substring(0, i1), renderer);
            int x2 = getStringWidth(string.substring(0, i2), renderer);

            drawHighlightBox(x + x1, y, x + x2, y + renderer.FONT_HEIGHT, highlightColor);
        }
    }

    public static void drawHighlightBox(IGuiRect rect, IGuiColor color) {
        drawHighlightBox(
            rect.getX(),
            rect.getY(),
            rect.getX() + rect.getWidth(),
            rect.getY() + rect.getHeight(),
            color.getRGB());
    }

    public static void drawHighlightBox(int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom) {
            int j = top;
            top = bottom;
            bottom = j;
        }

        float f3 = (float) (color >> 24 & 255) / 255.0F;
        float f = (float) (color >> 16 & 255) / 255.0F;
        float f1 = (float) (color >> 8 & 255) / 255.0F;
        float f2 = (float) (color & 255) / 255.0F;

        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);

        Tessellator tessellator = Tessellator.instance;
        // VertexBuffer bufferbuilder = tessellator.getBuffer();
        GL11.glColor4f(f, f1, f2, f3);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glLogicOp(GL11.GL_OR_REVERSE);
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) left, (double) bottom, 0.0D);
        tessellator.addVertex((double) right, (double) bottom, 0.0D);
        tessellator.addVertex((double) right, (double) top, 0.0D);
        tessellator.addVertex((double) left, (double) top, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glPopMatrix();
    }

    public static void drawColoredRect(IGuiRect rect, IGuiColor color) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        color.applyGlColor();
        tessellator.startDrawingQuads();
        tessellator.addVertex((double) rect.getX(), (double) rect.getY() + rect.getHeight(), 0.0D);
        tessellator.addVertex((double) rect.getX() + rect.getWidth(), (double) rect.getY() + rect.getHeight(), 0.0D);
        tessellator.addVertex((double) rect.getX() + rect.getWidth(), (double) rect.getY(), 0.0D);
        tessellator.addVertex((double) rect.getX(), (double) rect.getY(), 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    /**
     * Performs a OpenGL scissor based on Minecraft's resolution instead of display resolution and adds it to the stack
     * of ongoing scissors.
     * Not using this method will result in incorrect scissoring and scaling of parent/child GUIs
     */
    public static void startScissor(IGuiRect rect) {
        if (scissorStack.size() >= 255) {
            throw new IndexOutOfBoundsException("Exceeded the maximum number of nested scissor (255)");
        }

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int f = r.getScaleFactor();

        // Have to do all this fancy stuff because glScissor() isn't affected by glScale() or glTranslate() and rather
        // than try and convince devs to use some custom hack
        // we'll just deal with it by reading from the current MODELVIEW MATRIX to convert between screen spaces at
        // their relative scales and translations.
        FloatBuffer fb = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, fb);
        fb.rewind();
        Matrix4f fm = new Matrix4f();
        fm.load(fb);

        // GL screenspace rectangle
        GuiRectangle sRect = new GuiRectangle(
            (int) (rect.getX() * f * fm.m00 + (fm.m30 * f)),
            (r.getScaledHeight() - (int) ((rect.getY() + rect.getHeight()) * fm.m11 + fm.m31)) * f,
            (int) (rect.getWidth() * f * fm.m00),
            (int) (rect.getHeight() * f * fm.m11));

        if (!scissorStack.empty()) {
            IGuiRect parentRect = scissorStack.peek();
            int x = Math.max(parentRect.getX(), sRect.getX());
            int y = Math.max(parentRect.getY(), sRect.getY());
            int w = Math.min(parentRect.getX() + parentRect.getWidth(), sRect.getX() + sRect.getWidth());
            int h = Math.min(parentRect.getY() + parentRect.getHeight(), sRect.getY() + sRect.getHeight());
            w = Math.max(0, w - x); // Clamp to 0 to prevent OpenGL errors
            h = Math.max(0, h - y); // Clamp to 0 to prevent OpenGL errors
            sRect = new GuiRectangle(x, y, w, h, 0);
        } else {
            sRect.w = Math.max(0, sRect.w);
            sRect.h = Math.max(0, sRect.h);
        }

        GL11.glScissor(sRect.getX(), sRect.getY(), sRect.getWidth(), sRect.getHeight());
        scissorStack.add(sRect);
    }

    /**
     * Pops the last scissor off the stack and returns to the last parent scissor or disables it if there are none
     */
    public static void endScissor() {
        scissorStack.pop();

        if (scissorStack.empty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            IGuiRect rect = scissorStack.peek();
            GL11.glScissor(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
        }
    }

    private static Locale getLocale() {
        var lang = Minecraft.getMinecraft()
            .getLanguageManager()
            .getCurrentLanguage();

        var locale = Locale.forLanguageTag(
            lang.toString()
                .replace(" (", "-")
                .replace(")", ""));

        if (locale.getCountry()
            .isEmpty()) {
            locale = Locale.forLanguageTag(lang.getLanguageCode());
        }
        if (locale.getCountry()
            .isEmpty()) {
            locale = Locale.ENGLISH;
        }
        return locale;
    }

    /**
     * Similar to normally splitting a string with the fontRenderer however this variant does
     * not attempt to preserve the formatting between lines. This is particularly important when the
     * index positions in the text are required to match the original unwrapped text.
     * 
     * @return The lines used for editing, with exact characters matching the original text on each index
     */
    public static List<String> splitStringWithoutFormat(String str, int wrapWidth, FontRenderer font) {
        return splitString(str, wrapWidth, font, false);
    }

    /**
     * @return The lines used for rendering, preserving formatting codes across lines
     */
    public static List<String> splitString(String str, int wrapWidth, FontRenderer font) {
        return splitString(str, wrapWidth, font, true);
    }

    private static List<String> splitString(final String str, final int wrapWidth, final FontRenderer font,
        final boolean withFormat) {
        if (str == null || str.isEmpty()) return Collections.emptyList();

        var lines = str.split("\n", -1);
        var locale = getLocale();
        final List<String> wraps = new ArrayList<>();
        String format = "";

        int l = 1;
        for (var line : lines) {
            final BreakIterator breaker = BreakIterator.getLineInstance(locale);
            breaker.setText(line);

            int start = breaker.first();
            int width = 0;
            StringBuilder buf = new StringBuilder(format);

            for (int end = breaker.next(); end != BreakIterator.DONE;) {
                String candidate = line.substring(start, end);
                String stripped = stripTrailing(candidate);
                int needWidth = getStringWidth(stripped, font);
                int realWidth = getStringWidth(candidate, font);

                if (width + needWidth <= wrapWidth) {
                    buf.append(candidate);
                    width += realWidth;
                    format = withFormat ? getFormatFromString(format + candidate) : "";
                    start = end;
                    end = breaker.next();
                } else if (needWidth > wrapWidth) {
                    int i = sizeStringToWidth(candidate, wrapWidth, font);
                    buf.append(candidate, 0, i);
                    String currentLine = buf.toString();
                    wraps.add(currentLine);
                    // Continue gradient smoothly across wrap
                    if (withFormat && (isAmpGradient(format) || isSectionGradient(format))) {
                        String remaining = candidate.substring(i) + line.substring(end < 0 ? line.length() : end);
                        format = continueGradient(format, currentLine, remaining);
                    }
                    buf = new StringBuilder(format);
                    width = 0;
                    start += i;
                } else {
                    String currentLine = buf.toString();
                    wraps.add(currentLine);
                    // Continue gradient smoothly across wrap
                    if (withFormat && (isAmpGradient(format) || isSectionGradient(format))) {
                        String remaining = candidate + line.substring(end < 0 ? line.length() : end);
                        format = continueGradient(format, currentLine, remaining);
                    }
                    buf = new StringBuilder(format);
                    width = 0;
                }
            }

            // add back newlines eaten in order to keep exact the same text length when !withFormat, used for text
            // editing calculations
            if (!withFormat && l++ != lines.length) buf.append('\n');
            // noinspection SizeReplaceableByIsEmpty
            wraps.add(buf.length() == 0 ? format : buf.toString());
        }
        return wraps;

    }

    private static String stripTrailing(String value) {
        int length = value.length();
        while (length > 0 && Character.isWhitespace(value.charAt(length - 1))) {
            length--;
        }
        return value.substring(0, length);
    }

    /**
     * Returns the index position under a given set of coordinates in a piece of text
     */
    public static int getCursorPos(String text, int x, FontRenderer font) {
        if (text.length() <= 0) {
            return 0;
        }

        int i = 0;

        for (; i < text.length(); i++) {
            if (getStringWidth(text.substring(0, i + 1), font) > x) {
                break;
            }
        }

        if (i - 1 >= 0 && text.charAt(i - 1) == '\n') {
            return i - 1;
        }

        return i;
    }

    /**
     * Returns the index position under a given set of coordinates in a wrapped piece of text
     */
    public static int getCursorPos(String text, int x, int y, int width, FontRenderer font) {
        List<String> tLines = RenderUtils.splitStringWithoutFormat(text, width, font);

        if (tLines.size() <= 0) {
            return 0;
        }

        int row = MathHelper.clamp_int(y / font.FONT_HEIGHT, 0, tLines.size() - 1);
        String lastFormat = "";
        String line;
        int idx = 0;

        for (int i = 0; i < row; i++) {
            line = tLines.get(i);
            idx += line.length();
            lastFormat = getFormatFromString(lastFormat + line);
        }

        return idx + getCursorPos(lastFormat + tLines.get(row), x, font) - lastFormat.length();
    }

    /**
     * Validate that at position pos we have §x followed by 6 pairs of §+hex_digit (14 chars total).
     */
    public static boolean isValidSectionX(String str, int pos) {
        // §x§R§R§G§G§B§B = 14 chars
        if (pos + 14 > str.length()) return false;
        if (str.charAt(pos) != '\u00a7' || Character.toLowerCase(str.charAt(pos + 1)) != 'x') return false;
        for (int k = 0; k < 6; k++) {
            int p = pos + 2 + k * 2;
            if (str.charAt(p) != '\u00a7') return false;
            if (!isHexChar(str.charAt(p + 1))) return false;
        }
        return true;
    }

    private static boolean isHex6(String str, int start) {
        if (start + 6 > str.length()) return false;
        for (int k = 0; k < 6; k++) {
            if (!isHexChar(str.charAt(start + k))) return false;
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isValidAmpCode(char c) {
        char cl = Character.toLowerCase(c);
        return (cl >= '0' && cl <= '9') || (cl >= 'a' && cl <= 'f')
            || (cl >= 'k' && cl <= 'o')
            || cl == 'r'
            || cl == 'x'
            || cl == 'y'
            || cl == 'w'
            || cl == 'j'
            || cl == 'g';
    }

    // --- Gradient continuation helpers ---

    /** Check if format string is an &g gradient (18 chars: &g&#RRGGBB&#RRGGBB). */
    private static boolean isAmpGradient(String fmt) {
        return fmt.length() >= 18 && fmt.charAt(0) == '&'
            && Character.toLowerCase(fmt.charAt(1)) == 'g'
            && fmt.charAt(2) == '&'
            && fmt.charAt(3) == '#';
    }

    /** Check if format string is a §g gradient (30 chars). */
    private static boolean isSectionGradient(String fmt) {
        return fmt.length() >= 30 && fmt.charAt(0) == '\u00a7' && Character.toLowerCase(fmt.charAt(1)) == 'g';
    }

    /** Parse 6-digit hex at offset in text (e.g. "FF00AA") into int RGB. Returns -1 on failure. */
    private static int parseHex6(String text, int offset) {
        if (offset + 6 > text.length()) return -1;
        int val = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(text.charAt(offset + i), 16);
            if (d == -1) return -1;
            val = (val << 4) | d;
        }
        return val;
    }

    private static int lerpRgb(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) (((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static String buildAmpHexColor(int rgb) {
        return String.format("&#%06X", rgb & 0xFFFFFF);
    }

    /**
     * Count visible characters in text, skipping §X pairs and &-codes.
     * Stops at gradient-terminating codes (color changes, reset, new gradient, rainbow)
     * so we only count chars that fall under the current gradient.
     */
    private static int countVisibleCharsInGradient(String text, int startIdx) {
        int count = 0;
        for (int i = startIdx; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                // Gradient terminators: reset, color codes, new gradient, rainbow
                if (code == 'r' || isFormatColor(text.charAt(i + 1)) || code == 'x' || code == 'y' || code == 'g') {
                    break;
                }
                i++; // skip style codes (k-o, w, j)
            } else if (c == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                char lo = Character.toLowerCase(next);
                // &g gradient
                if (lo == 'g' && i + 17 < text.length()
                    && text.charAt(i + 2) == '&'
                    && text.charAt(i + 3) == '#'
                    && isHex6(text, i + 4)
                    && text.charAt(i + 10) == '&'
                    && text.charAt(i + 11) == '#'
                    && isHex6(text, i + 12)) {
                    break; // new gradient terminates current
                }
                // &#RRGGBB color
                if (next == '#' && i + 7 < text.length() && isHex6(text, i + 2)) {
                    break; // explicit color terminates gradient
                }
                // &y rainbow or &r reset or &0-f color
                if (lo == 'y' || lo == 'r' || isFormatColor(next)) {
                    break;
                }
                // &w, &j, &k-o — style toggles, don't terminate gradient
                if (isValidAmpCode(next)) {
                    i += 1;
                } else {
                    count++; // literal &
                }
            } else {
                count++;
            }
        }
        return count;
    }

    /**
     * Given a gradient format string and the text already rendered on the current line (beforeWrap)
     * plus the text remaining after the wrap, compute a continuation gradient prefix.
     * Preserves style suffixes (bold, wave, etc.) that were active alongside the gradient.
     */
    static String continueGradient(String gradientFmt, String beforeWrap, String afterWrap) {
        int startRgb, endRgb;
        int gradPrefixLen;
        String styleSuffix;

        if (isAmpGradient(gradientFmt)) {
            startRgb = parseHex6(gradientFmt, 4);
            endRgb = parseHex6(gradientFmt, 12);
            gradPrefixLen = 18;
            styleSuffix = gradientFmt.length() > 18 ? gradientFmt.substring(18) : "";
        } else if (isSectionGradient(gradientFmt)) {
            startRgb = parseRgbFromSectionX(gradientFmt, 2);
            endRgb = parseRgbFromSectionX(gradientFmt, 16);
            gradPrefixLen = 30;
            styleSuffix = gradientFmt.length() > 30 ? gradientFmt.substring(30) : "";
        } else {
            return gradientFmt;
        }

        if (startRgb == -1 || endRgb == -1) return gradientFmt;

        // Find where the gradient spec actually starts in beforeWrap
        // (on first line it may be mid-line, on continuation lines it's at position 0)
        int gradPos;
        if (isAmpGradient(gradientFmt)) {
            gradPos = beforeWrap.lastIndexOf("&g&#");
        } else {
            gradPos = beforeWrap.lastIndexOf("\u00a7g\u00a7");
        }
        if (gradPos < 0) gradPos = 0;

        // Count visible chars rendered under this gradient on the current line
        int visOnLine = countVisibleCharsInGradient(beforeWrap, gradPos + gradPrefixLen);
        // Count visible chars remaining under this gradient after the wrap
        int visRemaining = countVisibleCharsInGradient(afterWrap, 0);
        int total = visOnLine + visRemaining;

        if (total <= 1) return buildAmpHexColor(endRgb) + styleSuffix;

        float t = (float) visOnLine / (total - 1);
        t = Math.min(t, 1f);
        int interpRgb = lerpRgb(startRgb, endRgb, t);

        return "&g" + buildAmpHexColor(interpRgb) + buildAmpHexColor(endRgb) + styleSuffix;
    }

    /** Parse RGB from §x§R§R§G§G§B§B starting at offset. */
    private static int parseRgbFromSectionX(String text, int offset) {
        if (offset + 14 > text.length()) return -1;
        int val = 0;
        for (int i = 0; i < 6; i++) {
            int d = Character.digit(text.charAt(offset + 3 + i * 2), 16);
            if (d == -1) return -1;
            val = (val << 4) | d;
        }
        return val;
    }

    // extract the format codes from one line to be applied to the following lines
    public static String getFormatFromString(String p_78282_0_) {
        StringBuilder s1 = new StringBuilder();
        int len = p_78282_0_.length();

        for (int i = 0; i < len; i++) {
            char ch = p_78282_0_.charAt(i);

            if (ch == '\u00a7' && i + 1 < len) {
                char c0 = p_78282_0_.charAt(i + 1);
                char c0l = Character.toLowerCase(c0);

                // §g + two §x sequences = gradient (30 chars)
                if (c0l == 'g' && i + 30 <= len
                    && isValidSectionX(p_78282_0_, i + 2)
                    && isValidSectionX(p_78282_0_, i + 16)) {
                    s1 = new StringBuilder(p_78282_0_.substring(i, i + 30));
                    i += 29;
                }
                // §x§R§R§G§G§B§B = 14-char BungeeCord RGB
                else if (c0l == 'x' && isValidSectionX(p_78282_0_, i)) {
                    s1 = new StringBuilder(p_78282_0_.substring(i, i + 14));
                    i += 13;
                }
                // §y — rainbow, acts as a color (resets styles)
                else if (c0l == 'y') {
                    s1 = new StringBuilder("\u00a7" + c0);
                    i += 1;
                }
                // §w, §j — wave/flip toggles (accumulate like style codes)
                else if (c0l == 'w' || c0l == 'j') {
                    s1.append("\u00a7")
                        .append(c0);
                    i += 1;
                }
                // Standard color codes (§0-f) — reset styles
                else if (isFormatColor(c0)) {
                    s1 = new StringBuilder("\u00a7" + c0);
                    i += 1;
                }
                // Style/special codes (§k-o, §r)
                else if (isFormatSpecial(c0)) {
                    s1.append("\u00a7")
                        .append(c0);
                    i += 1;
                } else {
                    i += 1; // skip unknown §X pair
                }
            } else if (ch == '&' && i + 1 < len) {
                char c0 = p_78282_0_.charAt(i + 1);
                char c0l = Character.toLowerCase(c0);

                // &g&#RRGGBB&#RRGGBB = 18-char gradient
                if (c0l == 'g' && i + 18 <= len
                    && p_78282_0_.charAt(i + 2) == '&'
                    && p_78282_0_.charAt(i + 3) == '#'
                    && isHex6(p_78282_0_, i + 4)
                    && p_78282_0_.charAt(i + 10) == '&'
                    && p_78282_0_.charAt(i + 11) == '#'
                    && isHex6(p_78282_0_, i + 12)) {
                    s1 = new StringBuilder(p_78282_0_.substring(i, i + 18));
                    i += 17;
                }
                // &#RRGGBB = 8-char hex color (resets styles)
                else if (c0 == '#' && isHex6(p_78282_0_, i + 2)) {
                    s1 = new StringBuilder(p_78282_0_.substring(i, i + 8));
                    i += 7;
                }
                // &y — rainbow (color-like, resets styles)
                else if (c0l == 'y') {
                    s1 = new StringBuilder("&" + c0);
                    i += 1;
                }
                // &w, &j — wave/flip toggles (accumulate)
                else if (c0l == 'w' || c0l == 'j') {
                    s1.append("&")
                        .append(c0);
                    i += 1;
                }
                // &0-f — color codes (reset styles)
                else if (isFormatColor(c0)) {
                    s1 = new StringBuilder("&" + c0);
                    i += 1;
                }
                // &k-o, &r — style/special codes
                else if (isFormatSpecial(c0)) {
                    s1.append("&")
                        .append(c0);
                    i += 1;
                }
                // Literal & — not a valid code, skip
            }
        }

        if (s1.length() <= 0) return "";
        // §r/&r means reset
        char last = s1.charAt(s1.length() - 1);
        if (last == 'r' || last == 'R') return "";

        return s1.toString();
    }

    private static int sizeStringToWidth(String str, int wrapWidth, FontRenderer font) {
        if (BetterQuesting.isGTNHLibLoaded) {
            // GTNHLib replacement that works as it should and supports Angelica's custom fonts
            return FontRendering.sizeStringToWidth(str, wrapWidth, font);
        }
        int i = str.length();
        int j = 0;
        int k = 0;
        int l = -1;

        for (boolean flag = false; k < i; ++k) {
            char c0 = str.charAt(k);

            switch (c0) {
                case '\n':
                    --k;
                    break;
                case ' ':
                    l = k;
                default:
                    j += font.getCharWidth(c0);

                    if (flag) {
                        ++j;
                    }

                    break;
                case '\u00a7':

                    if (k < i - 1) {
                        ++k;
                        char c1 = str.charAt(k);

                        if (c1 != 'l' && c1 != 'L') {
                            if (c1 == 'r' || c1 == 'R' || isFormatColor(c1)) {
                                flag = false;
                            }
                        } else {
                            flag = true;
                        }
                    }
            }

            if (c0 == '\n') {
                ++k;
                l = k;
                break;
            }

            if (j > wrapWidth) {
                break;
            }
        }

        return k != i && l != -1 && l < k ? l : k;
    }

    private static boolean isFormatColor(char colorChar) {
        return colorChar >= '0' && colorChar <= '9' || colorChar >= 'a' && colorChar <= 'f'
            || colorChar >= 'A' && colorChar <= 'F';
    }

    private static boolean isFormatSpecial(char p_78270_0_) {
        return p_78270_0_ >= 107 && p_78270_0_ <= 111 || p_78270_0_ >= 75 && p_78270_0_ <= 79
            || p_78270_0_ == 114
            || p_78270_0_ == 82;
    }

    public static float lerpFloat(float f1, float f2, float blend) {
        return (f2 * blend) + (f1 * (1F - blend));
    }

    public static double lerpDouble(double d1, double d2, double blend) {
        return (d2 * blend) + (d1 * (1D - blend));
    }

    public static int lerpRGB(int c1, int c2, float blend) {
        float a1 = c1 >> 24 & 255;
        float r1 = c1 >> 16 & 255;
        float g1 = c1 >> 8 & 255;
        float b1 = c1 & 255;

        float a2 = c2 >> 24 & 255;
        float r2 = c2 >> 16 & 255;
        float g2 = c2 >> 8 & 255;
        float b2 = c2 & 255;

        int a3 = (int) lerpFloat(a1, a2, blend);
        int r3 = (int) lerpFloat(r1, r2, blend);
        int g3 = (int) lerpFloat(g1, g2, blend);
        int b3 = (int) lerpFloat(b1, b2, blend);

        return (a3 << 24) + (r3 << 16) + (g3 << 8) + b3;
    }

    public static void drawHoveringText(List<String> textLines, int mouseX, int mouseY, int screenWidth,
        int screenHeight, int maxTextWidth, FontRenderer font) {
        drawHoveringText(null, textLines, mouseX, mouseY, screenWidth, screenHeight, maxTextWidth, font);
    }

    /**
     * Modified version of Forge's tooltip rendering that doesn't adjust Z depth
     */
    public static void drawHoveringText(final ItemStack stack, List<String> textLines, int mouseX, int mouseY,
        int screenWidth, int screenHeight, int maxTextWidth, FontRenderer font) {
        if (textLines == null || textLines.isEmpty()) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glTranslatef(0F, 0F, 32F);
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        // GlStateManager.enableDepth();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        int tooltipTextWidth = 0;

        for (String textLine : textLines) {
            int textLineWidth = getStringWidth(textLine, font);

            if (textLineWidth > tooltipTextWidth) {
                tooltipTextWidth = textLineWidth;
            }
        }

        boolean needsWrap = false;

        int titleLinesCount = 1;
        int tooltipX = mouseX + 12;

        if (tooltipX + tooltipTextWidth + 4 > screenWidth) {
            tooltipX = mouseX - 16 - tooltipTextWidth;

            if (tooltipX < 4) // if the tooltip doesn't fit on the screen
            {
                if (mouseX > screenWidth / 2) {
                    tooltipTextWidth = mouseX - 12 - 8;
                } else {
                    tooltipTextWidth = screenWidth - 16 - mouseX;
                }
                needsWrap = true;
            }
        }

        if (maxTextWidth > 0 && tooltipTextWidth > maxTextWidth) {
            tooltipTextWidth = maxTextWidth;
            needsWrap = true;
        }

        if (needsWrap) {
            int wrappedTooltipWidth = 0;
            List<String> wrappedTextLines = new ArrayList<>();

            for (int i = 0; i < textLines.size(); i++) {
                String textLine = textLines.get(i);
                List<String> wrappedLine = font.listFormattedStringToWidth(textLine, tooltipTextWidth);
                if (i == 0) {
                    titleLinesCount = wrappedLine.size();
                }

                for (String line : wrappedLine) {
                    int lineWidth = getStringWidth(line, font);
                    if (lineWidth > wrappedTooltipWidth) {
                        wrappedTooltipWidth = lineWidth;
                    }
                    wrappedTextLines.add(line);
                }
            }

            tooltipTextWidth = wrappedTooltipWidth;
            textLines = wrappedTextLines;

            if (mouseX > screenWidth / 2) {
                tooltipX = mouseX - 16 - tooltipTextWidth;
            } else {
                tooltipX = mouseX + 12;
            }
        }

        int tooltipY = mouseY - 12;
        int tooltipHeight = 8;

        if (textLines.size() > 1) {
            tooltipHeight += (textLines.size() - 1) * 10;

            if (textLines.size() > titleLinesCount) {
                tooltipHeight += 2; // gap between title lines and next lines
            }
        }

        if (tooltipY < 4) {
            tooltipY = 4;
        } else if (tooltipY + tooltipHeight + 4 > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 4;
        }

        int backgroundColor = 0xF0100010;
        int borderColorStart = 0x505000FF;
        int borderColorEnd = (borderColorStart & 0xFEFEFE) >> 1 | borderColorStart & 0xFF000000;

        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY - 4,
            tooltipX + tooltipTextWidth + 3,
            tooltipY - 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            tooltipX + tooltipTextWidth + 3,
            tooltipY + tooltipHeight + 4,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + tooltipTextWidth + 3,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            0,
            tooltipX - 4,
            tooltipY - 3,
            tooltipX - 3,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            0,
            tooltipX + tooltipTextWidth + 3,
            tooltipY - 3,
            tooltipX + tooltipTextWidth + 4,
            tooltipY + tooltipHeight + 3,
            backgroundColor,
            backgroundColor);
        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY - 3 + 1,
            tooltipX - 3 + 1,
            tooltipY + tooltipHeight + 3 - 1,
            borderColorStart,
            borderColorEnd);
        drawGradientRect(
            0,
            tooltipX + tooltipTextWidth + 2,
            tooltipY - 3 + 1,
            tooltipX + tooltipTextWidth + 3,
            tooltipY + tooltipHeight + 3 - 1,
            borderColorStart,
            borderColorEnd);
        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY - 3,
            tooltipX + tooltipTextWidth + 3,
            tooltipY - 3 + 1,
            borderColorStart,
            borderColorStart);
        drawGradientRect(
            0,
            tooltipX - 3,
            tooltipY + tooltipHeight + 2,
            tooltipX + tooltipTextWidth + 3,
            tooltipY + tooltipHeight + 3,
            borderColorEnd,
            borderColorEnd);

        int tooltipTop = tooltipY;

        GL11.glTranslatef(0F, 0F, 0.1F);

        for (int lineNumber = 0; lineNumber < textLines.size(); ++lineNumber) {
            String line = textLines.get(lineNumber);
            font.drawStringWithShadow(line, tooltipX, tooltipY, -1);

            if (lineNumber + 1 == titleLinesCount) {
                tooltipY += 2;
            }

            tooltipY += 10;
        }

        GL11.glEnable(GL11.GL_LIGHTING);
        // GlStateManager.disableDepth();
        // GlStateManager.enableDepth();
        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
    }

    public static void drawGradientRect(int zDepth, int p_drawGradientRect_1_, int p_drawGradientRect_2_,
        int p_drawGradientRect_3_, int p_drawGradientRect_4_, int p_drawGradientRect_5_, int p_drawGradientRect_6_) {
        float var7 = (float) (p_drawGradientRect_5_ >> 24 & 255) / 255.0F;
        float var8 = (float) (p_drawGradientRect_5_ >> 16 & 255) / 255.0F;
        float var9 = (float) (p_drawGradientRect_5_ >> 8 & 255) / 255.0F;
        float var10 = (float) (p_drawGradientRect_5_ & 255) / 255.0F;
        float var11 = (float) (p_drawGradientRect_6_ >> 24 & 255) / 255.0F;
        float var12 = (float) (p_drawGradientRect_6_ >> 16 & 255) / 255.0F;
        float var13 = (float) (p_drawGradientRect_6_ >> 8 & 255) / 255.0F;
        float var14 = (float) (p_drawGradientRect_6_ & 255) / 255.0F;
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glDisable(3008);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(7425);
        Tessellator var15 = Tessellator.instance;
        var15.startDrawingQuads();
        var15.setColorRGBA_F(var8, var9, var10, var7);
        var15.addVertex(p_drawGradientRect_3_, p_drawGradientRect_2_, zDepth);
        var15.addVertex(p_drawGradientRect_1_, p_drawGradientRect_2_, zDepth);
        var15.setColorRGBA_F(var12, var13, var14, var11);
        var15.addVertex(p_drawGradientRect_1_, p_drawGradientRect_4_, zDepth);
        var15.addVertex(p_drawGradientRect_3_, p_drawGradientRect_4_, zDepth);
        var15.draw();
        GL11.glShadeModel(7424);
        GL11.glDisable(3042);
        GL11.glEnable(3008);
        GL11.glEnable(3553);
    }

    /**
     * A version of getStringWidth that actually behaves according to the format resetting rules of colour codes.
     * Minecraft's built in one is busted!
     */
    public static int getStringWidth(String text, FontRenderer font) {
        if (BetterQuesting.isGTNHLibLoaded) {
            // GTNHLib replacement that works as it should and supports Angelica's custom fonts
            return FontRendering.getStringWidth(text, font);
        }

        if (text == null || text.length() == 0) return 0;

        int i = 0;
        boolean flag = false;

        for (int j = 0; j < text.length(); ++j) {
            char c0 = text.charAt(j);
            int k = font.getCharWidth(c0);

            if (k < 0 && j < text.length() - 1) // k should only be negative when the section sign has been used!
            {
                ++j;
                c0 = text.charAt(j);

                if (c0 != 'l' && c0 != 'L') {
                    int ci = "0123456789abcdefklmnor".indexOf(
                        String.valueOf(c0)
                            .toLowerCase(Locale.ROOT)
                            .charAt(0));
                    // if (c0 == 'r' || c0 == 'R') // Minecraft's original implemention. This is broken...
                    if (ci < 16 || ci == 21) // Colour or reset code!
                    {
                        flag = false;
                    }
                } else {
                    flag = true;
                }

                k = 0;
            }

            i += k;

            if (flag && k > 0) {
                ++i;
            }
        }

        return i;
    }
}
