package betterquesting.api2.client.gui.misc;

import net.minecraft.client.renderer.Tessellator;

public class TexturedModalRectRenderer {

    Tessellator tessellator = Tessellator.instance;
    private boolean isDrawing = false;

    public TexturedModalRectRenderer() {
        startIfNeeded();
    }

    private void startIfNeeded() {
        if (!isDrawing) {
            tessellator.startDrawingQuads();
            isDrawing = true;
        }
    }

    public void addQuad(int x, int y, int u, int v, int width, int height, float zLevel) {
        startIfNeeded();
        float var7 = 0.00390625F;
        float var8 = 0.00390625F;
        tessellator.addVertexWithUV((x), (y + height), zLevel, ((u) * var7), ((v + height) * var8));
        tessellator.addVertexWithUV((x + width), (y + height), zLevel, ((u + width) * var7), ((v + height) * var8));
        tessellator.addVertexWithUV((x + width), (y), zLevel, ((u + width) * var7), ((v) * var8));
        tessellator.addVertexWithUV((x), (y), zLevel, ((u) * var7), ((v) * var8));
    }

    public void draw() {
        if (isDrawing) {
            tessellator.draw();
            isDrawing = false;
        }
    }
}
