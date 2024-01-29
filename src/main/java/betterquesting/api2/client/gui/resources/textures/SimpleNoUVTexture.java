package betterquesting.api2.client.gui.resources.textures;

import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Like SimpleTexture, but blindly draws the entire texture without consideration over u/v
 */
public class SimpleNoUVTexture implements IGuiTexture
{
	private static final IGuiColor defColor = new GuiColorStatic(255, 255, 255, 255);

	private final ResourceLocation texture;
	private final IGuiRect texBounds;
	private boolean maintainAspect = false;

	public SimpleNoUVTexture(ResourceLocation texture, IGuiRect bounds)
	{
		this.texture = texture;
		this.texBounds = bounds;
	}
	
	public SimpleNoUVTexture maintainAspect(boolean enable)
	{
		this.maintainAspect = enable;
		return this;
	}
	
	@Override
	public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick)
	{
		drawTexture(x, y, width, height, zLevel, partialTick, defColor);
	}
	
	@Override
	public void drawTexture(int x, int y, int width, int height, float zLevel, float partialTick, IGuiColor color)
	{
	    if(width <= 0 || height <= 0) return;
	    
		GL11.glPushMatrix();
		
		float sx = (float)width / (float)texBounds.getWidth();
		float sy = (float)height / (float)texBounds.getHeight();
		
		if(maintainAspect)
		{
			float sa = Math.min(sx, sy);
			float dx = (sx - sa) * texBounds.getWidth() / 2F;
			float dy = (sy - sa) * texBounds.getHeight() / 2F;
			sx = sa;
			sy = sa;
			GL11.glTranslatef(x + dx, y + dy, 0F);
		} else
		{
			GL11.glTranslatef(x, y, 0F);
		}

		color.applyGlColor();
		
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
		
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(0, texBounds.getHeight() * sy, zLevel, 0, 1);
		tessellator.addVertexWithUV(texBounds.getWidth() * sx, texBounds.getHeight() * sy, zLevel, 1, 1);
		tessellator.addVertexWithUV(texBounds.getWidth() * sx, 0, zLevel, 1, 0);
		tessellator.addVertexWithUV(0, 0, zLevel, 0, 0);
		tessellator.draw();

		GL11.glPopMatrix();
	}
	
	@Override
	public ResourceLocation getTexture()
	{
		return this.texture;
	}
	
	@Override
	public IGuiRect getBounds()
	{
		return this.texBounds;
	}
}
