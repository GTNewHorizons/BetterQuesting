package betterquesting.api2.client.gui.panels.content;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.misc.URIHandlers;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.core.BetterQuesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.util.MathHelper;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static betterquesting.api.storage.BQ_Settings.textWidthCorrection;

public class PanelTextBox implements IGuiPanel
{
	private static final Pattern url = Pattern.compile("\\[url] *(.*?) *\\[/url]");
	private static final String defaultUrlProtocol = "https";
	private static final Set<String> supportedUrlProtocol = ImmutableSet.of("http", "https");
	private final GuiRectText transform;
	private final List<HotZone> hotZones = new ArrayList<>();
	private boolean enabled = true;

	// List of colors set for specific themes, usually dark ones, to improve readability
	private static final ImmutableMap<String, String> urlColors = ImmutableMap.of(
			"betterquesting:dark", "§9§n",
			"betterquesting:stronghold", "§9§n",
			"betterquesting:overworld", "§9§n");

	private static final ImmutableMap<String, String> warningColors = ImmutableMap.of(
			"betterquesting:dark", "§c",
			"betterquesting:stronghold", "§c",
			"betterquesting:overworld", "§c");

	private static final ImmutableMap<String, String> noteColors = ImmutableMap.of(
			"betterquesting:stronghold", "§b",
			"betterquesting:ender", "§b",
			"betterquesting:overworld", "§b");

	private static final ImmutableMap<String, String> questReferenceColors = ImmutableMap.of(
			"betterquesting:dark", "§a§n",
			"betterquesting:stronghold", "§a§n",
			"betterquesting:overworld", "§a§n");

	private static final Pattern urlTagStart = Pattern.compile("\\[url]");
	private static final Pattern warningTagStart = Pattern.compile("\\[warn]");
	private static final Pattern noteTagStart = Pattern.compile("\\[note]");
	private static final Pattern questRefTagStart = Pattern.compile("\\[quest]");
	private static final Pattern allTagEnds = Pattern.compile("\\[/url]|\\[/warn]|\\[/note]|\\[/quest]");

	private String text = "", rawText = "";
	private boolean shadow = false;
	private IGuiColor color = new GuiColorStatic(255, 255, 255, 255);
	private final boolean autoFit;
	private int align = 0;
	private int fontScale = 12;
	
	private int lines = 1; // Cached number of lines
	private boolean hyperlinkAware;

	public PanelTextBox(IGuiRect rect, String text)
	{
		this(rect, text, false);
	}
	
	public PanelTextBox(IGuiRect rect, String text, boolean autoFit)
	{
		this(rect, text, autoFit, false);
	}

	public PanelTextBox(IGuiRect rect, String text, boolean autoFit, boolean hyperlinkAware)
	{
		this.transform = new GuiRectText(rect, autoFit);
		this.autoFit = autoFit;
		this.hyperlinkAware = hyperlinkAware;
		this.setText(text);
	}

	public boolean isHyperlinkAware()
	{
		return hyperlinkAware;
	}

	public PanelTextBox setHyperlinkAware(boolean hyperlinkAware)
	{
		this.hyperlinkAware = hyperlinkAware;
		bakeHotZones(null);
		return this;
	}
	
	public PanelTextBox setText(String text)
	{
		if(hyperlinkAware)
		{
			String coloredText = "";
			String currentTheme = BQ_Settings.curTheme;
			String urlColor = urlColors.getOrDefault(currentTheme, "§1§n"); // default dark blue + underlined
			String warningColor = warningColors.getOrDefault(currentTheme, "§4"); // default dark red
			String noteColor = noteColors.getOrDefault(currentTheme, "§3"); // default dark aqua
			String questRefColor = questReferenceColors.getOrDefault(currentTheme, "§2§n"); // default dark green + bold

			this.rawText = text;
			coloredText = warningTagStart.matcher(text).replaceAll(warningColor);
			coloredText = noteTagStart.matcher(coloredText).replaceAll(noteColor);
			coloredText = questRefTagStart.matcher(coloredText).replaceAll(questRefColor);
			coloredText = urlTagStart.matcher(coloredText).replaceAll(urlColor);
			this.text = allTagEnds.matcher(coloredText).replaceAll("§r");
		} else
		{
			this.text = text;
		}

		IGuiRect bounds = this.getTransform();
		FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
		
		if(autoFit)
		{
			float scale = fontScale / 12F;
			List<String> sl = RenderUtils.splitStringWithoutFormat(this.text, (int)Math.floor(bounds.getWidth() / scale / textWidthCorrection), fr);
			lines = sl.size() - 1;
			
			this.transform.h = fr.FONT_HEIGHT * sl.size();

			bakeHotZones(sl);
		} else
		{
			lines = (bounds.getHeight() / fr.FONT_HEIGHT) - 1;
		}
		
		return this;
	}

	private void bakeHotZones(List<String> lines) {
		hotZones.clear();
		if (!isHyperlinkAware()) return; // not enabled
		if (StringUtils.isBlank(text)) return; // nothing to do
		FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
		IGuiRect fullbox = getTransform();
		if (lines == null)
		{
			float scale = fontScale / 12F;
			lines = RenderUtils.splitStringWithoutFormat(this.text, (int) Math.floor(fullbox.getWidth() / scale / textWidthCorrection), fr);
		}

		Matcher matcher = url.matcher(rawText);
		// removal of [url] and whitespace on either side of the url can affect string pos
		int toDeduct = 0;

		while(matcher.find())
		{
			String url = matcher.group(1);
			int start = matcher.start() - toDeduct;
			int end = start + url.length();

			int currentPos = 0;
			boolean foundUrlStart = false;
			for(int lineIndex = 0, lineCount = lines.size(); lineIndex < lineCount; currentPos += lines.get(lineIndex++).length())
			{
				String line = lines.get(lineIndex);
				if(!foundUrlStart)
				{
					if(start < currentPos + line.length())
					{
						int left = RenderUtils.getStringWidth(line.substring(0, start - currentPos), fr);
						if (end <= currentPos + line.length())
						{
							// url on same line, early exit
							int right = RenderUtils.getStringWidth(line.substring(0, end - currentPos), fr);
							GuiTransform location = new GuiTransform(GuiAlign.FULL_BOX, left, fr.FONT_HEIGHT * lineIndex, right - left, fr.FONT_HEIGHT, 0);
							location.setParent(fullbox);
							hotZones.add(new HotZone(location, url));
							break;
						}
						// url span multiple lines
						foundUrlStart = true;
						GuiTransform location = new GuiTransform(GuiAlign.FULL_BOX, left, fr.FONT_HEIGHT * lineIndex, fullbox.getWidth(), fr.FONT_HEIGHT, 0);
						location.setParent(fullbox);
						hotZones.add(new HotZone(location, url));
					}
				} else
				{
					if (end <= currentPos + line.length())
					{
						// url ends at current line
						GuiTransform location = new GuiTransform(GuiAlign.FULL_BOX, 0, fr.FONT_HEIGHT * lineIndex, RenderUtils.getStringWidth(line.substring(0, end - currentPos), fr), fr.FONT_HEIGHT, 0);
						location.setParent(fullbox);
						hotZones.add(new HotZone(location, url));
						break;
					} else
					{
						// url still going...
						GuiTransform location = new GuiTransform(GuiAlign.FULL_BOX, 0, fr.FONT_HEIGHT * lineIndex, fullbox.getWidth(), fr.FONT_HEIGHT, 0);
						location.setParent(fullbox);
						hotZones.add(new HotZone(location, url));
					}
				}
			}
			toDeduct += matcher.end() - matcher.start() - url.length();
		}
	}

	public PanelTextBox setColor(IGuiColor color)
	{
		this.color = color;
		return this;
	}
	
	public PanelTextBox setAlignment(int align)
	{
		this.align = MathHelper.clamp_int(align, 0, 2);
		return this;
	}
	
	public PanelTextBox setFontSize(int size)
    {
        this.fontScale = size;
        return this;
    }
	
	public PanelTextBox enableShadow(boolean enable)
	{
		this.shadow = enable;
		return this;
	}
	
	@Override
	public IGuiRect getTransform()
	{
		return transform;
	}
	
	@Override
	public void initPanel()
	{
		IGuiRect bounds = this.getTransform();
		FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
		float scale = fontScale / 12F;
		
		if(!autoFit)
		{
			lines = (int)Math.floor(bounds.getHeight() / (fr.FONT_HEIGHT * scale)) - 1;
			return;
		}
		
		List<String> sl = RenderUtils.splitStringWithoutFormat(text, (int)Math.floor(bounds.getWidth() / scale / textWidthCorrection), fr);
		lines = sl.size() - 1;
		bakeHotZones(sl);

		this.transform.h = (int)Math.floor(fr.FONT_HEIGHT * sl.size() * scale);
	}
	
	@Override
	public void setEnabled(boolean state)
	{
		this.enabled = state;
	}
	
	@Override
	public boolean isEnabled()
	{
		return this.enabled;
	}
	
	@Override
	public void drawPanel(int mx, int my, float partialTick)
	{
		IGuiRect bounds = this.getTransform();
		FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
		
		float s = fontScale / 12F;
		int w = (int)Math.ceil(RenderUtils.getStringWidth(text, fr) * s);
		int bw = (int)Math.floor(bounds.getWidth() / s / textWidthCorrection);
		
		if(bw <= 0) return;
        
        GL11.glPushMatrix();
        GL11.glTranslatef(bounds.getX(), bounds.getY(), 1);
        GL11.glScalef(s, s, 1F);
        
		if(align == 2 && bw >= w)
		{
			RenderUtils.drawSplitString(fr, text, bw - w, 0, bw, color.getRGB(), shadow, 0, lines);
		} else if(align == 1 && bw >= w)
		{
			RenderUtils.drawSplitString(fr, text, bw/2 - w/2, 0, bw, color.getRGB(), shadow, 0, lines);
		} else
		{
			RenderUtils.drawSplitString(fr, text, 0, 0, bw, color.getRGB(), shadow, 0, lines);
		}

		if(BQ_Settings.urlDebug)
		{
			for(int i = 0, hotZonesSize = hotZones.size(); i < hotZonesSize; i++)
			{
				RenderUtils.drawHighlightBox(hotZones.get(i).location, new GuiColorStatic(i % 3 == 0 ? 255 : 0, i % 3 == 1 ? 255 : 0, i % 3 == 2 ? 255 : 0, 255));
			}
		}

        GL11.glPopMatrix();
	}
	
	@Override
	public boolean onMouseClick(int mx, int my, int click)
	{
		int mxt = mx + getTransform().getX(), myt = my + getTransform().getY();
		for(HotZone hotZone : hotZones)
		{
			if (hotZone.location.contains(mxt, myt)) {
				URI uri;
				try {
					URI tmp;
					tmp = new URI(hotZone.url);
					if (tmp.getScheme() == null)
						tmp = new URI(defaultUrlProtocol + "://" + hotZone.url);
					uri = tmp;
				} catch(URISyntaxException ex) {
					return false;
				}
				Predicate<URI> handler = URIHandlers.get(uri.getScheme());
				if (handler == null) return false;
				return handler.test(uri);
			}
		}
		return false;
	}

	private static void openURL(URI p_146407_1_)
	{
		try
		{
			Class<?> oclass = Class.forName("java.awt.Desktop");
			Object object = oclass.getMethod("getDesktop").invoke(null);
			oclass.getMethod("browse", URI.class).invoke(object, p_146407_1_);
		}
		catch (Throwable throwable)
		{
			BetterQuesting.logger.error("Couldn't open link", throwable);
		}
	}

	@Override
	public boolean onMouseRelease(int mx, int my, int click)
	{
		return false;
	}
	
	@Override
	public boolean onMouseScroll(int mx, int my, int scroll)
	{
		return false;
	}
	
	@Override
	public boolean onKeyTyped(char c, int keycode)
	{
		return false;
	}
	
	@Override
	public List<String> getTooltip(int mx, int my)
	{
		return null;
	}
	
	private static class GuiRectText implements IGuiRect
	{
		private final IGuiRect proxy;
		private final boolean useH;
		private int h;
		
		public GuiRectText(IGuiRect proxy, boolean useH)
		{
			this.proxy = proxy;
			this.useH = useH;
		}
		
		@Override
		public int getX()
		{
			return proxy.getX();
		}
		
		@Override
		public int getY()
		{
			return proxy.getY();
		}
		
		@Override
		public int getWidth()
		{
			return proxy.getWidth();
		}
		
		@Override
		public int getHeight()
		{
			return useH ? h : proxy.getHeight();
		}
		
		@Override
		public int getDepth()
		{
			return proxy.getDepth();
		}
		
		@Override
		public IGuiRect getParent()
		{
			return proxy.getParent();
		}
		
		@Override
		public void setParent(IGuiRect rect)
		{
			proxy.setParent(rect);
		}
		
		@Override
		public boolean contains(int x, int y)
		{
			int x1 = this.getX();
			int x2 = x1 + this.getWidth();
			int y1 = this.getY();
			int y2 = y1 + this.getHeight();
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
		
		/*@Override
		public void translate(int x, int y)
		{
			proxy.translate(x, y);
		}*/
		
		@Override
		public int compareTo(IGuiRect o)
		{
			return proxy.compareTo(o);
		}
	}

	private static class HotZone {
		final IGuiRect location;
		final String url;

		public HotZone(IGuiRect location, String url)
		{
			this.location = location;
			this.url = url;
		}
	}
}
