package betterquesting.api2.client.gui.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TextureSizeHelper
{
	private static final Map<ResourceLocation, GuiRectangle> dimensions = new HashMap<>();

	static
	{
		//noinspection Convert2Lambda
		((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new IResourceManagerReloadListener()
		{
			@Override
			public void onResourceManagerReload(IResourceManager p_110549_1_)
			{
				dimensions.clear();
			}
		});
	}

	public static IGuiRect getDimension(ResourceLocation texture)
	{
		if(!dimensions.containsKey(texture))
		{
			try
			{
				dimensions.put(texture, loadDimension(texture));
			} catch(Exception e)
			{
				// ignore
			}
		}
		GuiRectangle dimension = dimensions.get(texture);
		if(dimension == null)
		{
			return new GuiRectangle(0, 0, 1, 1);  // will default to err texture, which is 1:1
		}
		return dimension;
	}

	private static GuiRectangle loadDimension(ResourceLocation texture) throws IOException
	{
		IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(texture);
		try(
				InputStream is = resource.getInputStream();
				ImageInputStream iis = ImageIO.createImageInputStream(is);
		)
		{
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if(!readers.hasNext())
				return null;
			ImageReader reader = readers.next();
			reader.setInput(iis);
			try
			{
				return new GuiRectangle(0, 0, reader.getWidth(0), reader.getHeight(0));
			} finally
			{
				reader.dispose();
			}
		}
	}

	public static final class ResourceDimension
	{
		public final int height, width;

		public ResourceDimension(int height, int width)
		{
			this.height = height;
			this.width = width;
		}
	}
}
