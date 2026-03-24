package betterquesting.api2.client.gui.panels.content;

import java.util.List;

import org.lwjgl.opengl.GL11;

import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.client.gui.resources.lines.IGuiLine;

public class PanelLine implements IGuiPanel {

    /**
     * Bounds aren't used in the drawing of the line, merely for determining draw order
     */
    private final IGuiRect bounds;
    private final ShouldDrawPredicate shouldDraw;
    private final IGuiLine line;
    private final IGuiRect start;
    private final IGuiRect end;
    private final IGuiColor color;
    private final int width;
    private final boolean drawArrowHead;

    private boolean enabled = true;

    public PanelLine(IGuiRect start, IGuiRect end, IGuiLine line, int width, IGuiColor color, int drawOrder) {
        this(start, end, line, width, color, drawOrder, null, false);
    }

    public PanelLine(IGuiRect start, IGuiRect end, IGuiLine line, int width, IGuiColor color, int drawOrder,
        ShouldDrawPredicate shouldDraw) {
        this(start, end, line, width, color, drawOrder, shouldDraw, false);
    }

    public PanelLine(IGuiRect start, IGuiRect end, IGuiLine line, int width, IGuiColor color, int drawOrder,
        ShouldDrawPredicate shouldDraw, boolean drawArrowHead) {
        this.start = start;
        this.end = end;
        this.line = line;
        this.width = width;
        this.color = color;
        this.drawArrowHead = drawArrowHead;
        this.bounds = new GuiRectangle(0, 0, 0, 0, drawOrder);
        this.shouldDraw = shouldDraw;
        this.bounds.setParent(start);
    }

    @Override
    public IGuiRect getTransform() {
        return bounds;
    }

    @Override
    public void initPanel() {}

    @Override
    public void setEnabled(boolean state) {
        this.enabled = state;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        if (shouldDraw == null || shouldDraw.shouldDraw(mx, my, partialTick)) {
            GL11.glPushMatrix();
            line.drawLine(start, end, width, color, partialTick);
            if (drawArrowHead) {
                drawArrowMarkers();
            }
            GL11.glPopMatrix();
        }
    }

    private void drawArrowMarkers() {
        float startX = start.getX() + start.getWidth() / 2F;
        float startY = start.getY() + start.getHeight() / 2F;
        float endX = end.getX() + end.getWidth() / 2F;
        float endY = end.getY() + end.getHeight() / 2F;
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float lineLength = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (lineLength <= 0.001F) {
            return;
        }

        float dirX = deltaX / lineLength;
        float dirY = deltaY / lineLength;
        float defaultMarkerLength = Math.max(3F, width * 0.85F);
        float markerSpacing = 24F;
        float startEdge = getEdgeDistance(start, dirX, dirY);
        float endEdge = getEdgeDistance(end, dirX, dirY);
        float visibleStart = startEdge;
        float visibleEnd = lineLength - endEdge;
        float visibleLength = Math.max(0F, visibleEnd - visibleStart);
        float markerLength = Math.min(defaultMarkerLength, Math.max(1.5F, visibleLength - 2F));
        float markerWidth = Math.max(1F, markerLength * 0.45F);
        float halfMarkerLength = markerLength / 2F;
        float minMarkerCenter = visibleStart + halfMarkerLength;
        float maxMarkerCenter = visibleEnd - halfMarkerLength;
        float centerPos = (visibleStart + visibleEnd) / 2F;

        if (maxMarkerCenter < minMarkerCenter) {
            minMarkerCenter = maxMarkerCenter = centerPos;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_STIPPLE);
        color.applyGlColor();
        GL11.glLineWidth(Math.max(1F, width * 0.4F));

        GL11.glBegin(GL11.GL_LINES);
        for (int step = 0;; step++) {
            float offset = step * markerSpacing;
            boolean drewAny = false;

            if (step == 0) {
                if (centerPos >= minMarkerCenter && centerPos <= maxMarkerCenter) {
                    addArrowMarker(startX, startY, dirX, dirY, centerPos, markerLength, markerWidth);
                    drewAny = true;
                }
            } else {
                float lowerPos = centerPos - offset;
                float upperPos = centerPos + offset;

                if (lowerPos >= minMarkerCenter && lowerPos <= maxMarkerCenter) {
                    addArrowMarker(startX, startY, dirX, dirY, lowerPos, markerLength, markerWidth);
                    drewAny = true;
                }

                if (upperPos >= minMarkerCenter && upperPos <= maxMarkerCenter) {
                    addArrowMarker(startX, startY, dirX, dirY, upperPos, markerLength, markerWidth);
                    drewAny = true;
                }
            }

            if (!drewAny && centerPos - offset < minMarkerCenter && centerPos + offset > maxMarkerCenter) {
                break;
            }
        }
        GL11.glEnd();

        GL11.glLineWidth(1F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private void addArrowMarker(float startX, float startY, float dirX, float dirY, float linePos, float markerLength,
        float markerWidth) {
        float halfMarkerLength = markerLength / 2F;
        float centerX = startX + dirX * linePos;
        float centerY = startY + dirY * linePos;
        float tipX = centerX + dirX * halfMarkerLength;
        float tipY = centerY + dirY * halfMarkerLength;
        float baseX = centerX - dirX * halfMarkerLength;
        float baseY = centerY - dirY * halfMarkerLength;
        float normalX = -dirY;
        float normalY = dirX;

        GL11.glVertex2f(tipX, tipY);
        GL11.glVertex2f(baseX + normalX * markerWidth, baseY + normalY * markerWidth);
        GL11.glVertex2f(tipX, tipY);
        GL11.glVertex2f(baseX - normalX * markerWidth, baseY - normalY * markerWidth);
    }

    private float getEdgeDistance(IGuiRect rect, float dirX, float dirY) {
        float halfWidth = rect.getWidth() / 2F;
        float halfHeight = rect.getHeight() / 2F;
        float distX = Math.abs(dirX) < 0.001F ? Float.POSITIVE_INFINITY : halfWidth / Math.abs(dirX);
        float distY = Math.abs(dirY) < 0.001F ? Float.POSITIVE_INFINITY : halfHeight / Math.abs(dirY);
        return Math.min(distX, distY);
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button) {
        return false;
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int button) {
        return false;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        return false;
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        return null;
    }

    public interface ShouldDrawPredicate {

        boolean shouldDraw(int mx_mc, int my_mc, float partialTicks);
    }
}
