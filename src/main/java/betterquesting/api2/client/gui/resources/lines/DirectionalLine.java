package betterquesting.api2.client.gui.resources.lines;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;

import org.lwjgl.opengl.GL11;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;

public class DirectionalLine implements IGuiLine {

    public static final float DefArrowWidth = 0.5f;
    public static final float DefArrowSize = 0.75f;
    public static final float DefArrowOpacity = 0.2f;
    public static final float DefWidthScale = 1f;

    private final float arrowWidthBase;
    private final float arrowSizeBase;
    private final float arrowOpacity;
    private final float widthScale;

    public DirectionalLine() {
        this(DefArrowWidth, DefArrowSize, DefArrowOpacity, DefWidthScale);
    }

    public DirectionalLine(float arrowWidthBase, float arrowSizeBase, float arrowOpacity, float widthScale) {
        this.arrowWidthBase = arrowWidthBase;
        this.arrowSizeBase = arrowSizeBase;
        this.arrowOpacity = arrowOpacity;
        this.widthScale = widthScale;
    }

    @Override
    public void drawLine(IGuiRect startRect, IGuiRect endRect, int width, IGuiColor color, float partialTick,
        boolean animate) {
        float scaledWidth = width * widthScale;
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        float startX = startRect.getX() + startRect.getWidth() / 2f;
        float startY = startRect.getY() + startRect.getHeight() / 2f;
        float diffX = endRect.getX() + endRect.getWidth() / 2f - startX;
        float diffY = endRect.getY() + endRect.getHeight() / 2f - startY;
        float length = (float) Math.sqrt(diffX * diffX + diffY * diffY);
        float angle = (float) Math.atan2(diffY, diffX);
        int argb = color.getRGB();
        GL11.glTranslatef(startX, startY, 1);
        GL11.glRotated(Math.toDegrees(angle), 0, 0, 1);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >> 24 & 255);
        tessellator.addVertex(0, scaledWidth / 2f, 0);
        tessellator.addVertex(length, scaledWidth / 2f, 0);
        tessellator.addVertex(length, -scaledWidth / 2f, 0);
        tessellator.addVertex(0, -scaledWidth / 2f, 0);

        // Arrow
        if (BQ_Settings.showDependencyArrows && length > 0F) {
            tessellator.setColorRGBA(0, 0, 0, Math.round(arrowOpacity * (argb >> 24 & 255)));
            int numberOfArrows = MathHelper.ceiling_float_int(length / 20f);

            float arrowSize = scaledWidth * arrowSizeBase;
            float progressOffset = numberOfArrows % 2 == 1 ? 0 : (1f / (numberOfArrows + 1)) / 2f;
            float arrowWidth = arrowWidthBase * scaledWidth;
            for (int i = 0; i <= numberOfArrows; i++) {
                float progress = (float) i / (numberOfArrows + 1) + progressOffset;
                if (animate) {
                    double period = length * 50;
                    double time = System.currentTimeMillis() % period;
                    progress += (float) (time / period);
                    progress %= 1;
                }
                float arrowX = length * progress;

                tessellator.addVertex(arrowX - arrowWidth / 2f, scaledWidth / 2f, 0);
                tessellator.addVertex(arrowX + arrowWidth / 2f, scaledWidth / 2f, 0);
                tessellator.addVertex(arrowX + arrowSize + arrowWidth / 2f, 0, 0);
                tessellator.addVertex(arrowX + arrowSize - arrowWidth / 2f, 0, 0);

                tessellator.addVertex(arrowX - arrowWidth / 2f, -scaledWidth / 2f, 0);
                tessellator.addVertex(arrowX + arrowSize - arrowWidth / 2f, 0, 0);
                tessellator.addVertex(arrowX + arrowSize + arrowWidth / 2f, 0, 0);
                tessellator.addVertex(arrowX + arrowWidth / 2f, -scaledWidth / 2f, 0);
            }
        }

        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        GL11.glPopMatrix();
    }

    @Override
    public void drawLine(IGuiRect start, IGuiRect end, int width, IGuiColor color, float partialTick) {
        drawLine(start, end, width, color, partialTick, false);
    }
}
