package betterquesting.api2.client.gui.resources.lines;

import net.minecraft.util.MathHelper;

import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;

public class DirectionalLine implements IGuiLine {

    public static final float DefArrowWidth = 0.5f;
    public static final float DefArrowSize = 0.75f;
    public static final float DefArrowOpacity = 0.2f;

    private final float arrowWidthBase;
    private final float arrowSizeBase;
    private final float arrowOpacity;

    public DirectionalLine() {
        this(DefArrowWidth, DefArrowSize, DefArrowOpacity);
    }

    public DirectionalLine(float arrowWidthBase, float arrowSizeBase, float arrowOpacity) {
        this.arrowWidthBase = arrowWidthBase;
        this.arrowSizeBase = arrowSizeBase;
        this.arrowOpacity = arrowOpacity;
    }

    @Override
    public void drawLine(IGuiRect startRect, IGuiRect endRect, int width, IGuiColor color, float partialTick,
        boolean animate) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Vector2f start = new Vector2f(
            startRect.getX() + startRect.getWidth() / 2f,
            startRect.getY() + startRect.getHeight() / 2f);
        Vector2f end = new Vector2f(
            endRect.getX() + endRect.getWidth() / 2f,
            endRect.getY() + endRect.getHeight() / 2f);
        Vector2f diff = new Vector2f(end).sub(start);
        float length = diff.length();
        float angle = (float) Math.atan2(diff.y, diff.x);
        color.applyGlColor();
        GL11.glTranslatef(start.x, start.y, 1);
        GL11.glRotated(Math.toDegrees(angle), 0, 0, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(0, width / 2f);
        GL11.glVertex2f(length, width / 2f);
        GL11.glVertex2f(length, -width / 2f);
        GL11.glVertex2f(0, -width / 2f);

        // Arrow
        if (BQ_Settings.showDependencyArrows) {
            GL11.glColor4f(0, 0, 0, arrowOpacity * color.getAlpha());
            int numberOfArrows = MathHelper.ceiling_float_int(length / 20f);

            float arrowSize = width * arrowSizeBase;
            float progressOffset = numberOfArrows % 2 == 1 ? 0 : (1f / (numberOfArrows + 1)) / 2f;
            float arrowWidth = arrowWidthBase * width;
            for (int i = 0; i <= numberOfArrows; i++) {
                float progress = (float) i / (numberOfArrows + 1) + progressOffset;
                if (animate) {
                    double period = length * 50;
                    double time = System.currentTimeMillis() % period;
                    progress += (float) (time / period);
                    progress %= 1;
                }
                float arrowX = length * progress;

                GL11.glVertex2f(arrowX - arrowWidth / 2f, width / 2f);
                GL11.glVertex2f(arrowX + arrowWidth / 2f, width / 2f);
                GL11.glVertex2f(arrowX + arrowSize + arrowWidth / 2f, 0f);
                GL11.glVertex2f(arrowX + arrowSize - arrowWidth / 2f, 0f);

                GL11.glVertex2f(arrowX - arrowWidth / 2f, -width / 2f);
                GL11.glVertex2f(arrowX + arrowSize - arrowWidth / 2f, 0f);
                GL11.glVertex2f(arrowX + arrowSize + arrowWidth / 2f, 0f);
                GL11.glVertex2f(arrowX + arrowWidth / 2f, -width / 2f);
            }
        }

        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        GL11.glPopMatrix();
    }
}
