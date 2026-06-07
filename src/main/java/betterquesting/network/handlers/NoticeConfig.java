package betterquesting.network.handlers;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Per-notice override bundle carried server -> packet -> client. Sentinels ("default" / -1 /
 * {@link Integer#MIN_VALUE}) mean "fall back to the player setting". The custom title text rides in
 * the packet's {@code mainText} field; everything else lives here.
 */
public class NoticeConfig {

    public String style = "default";
    public String showIcon = "default";
    public String subtitleText = "";
    public float duration = -1f;
    public float fadeIn = -1f;
    public float fadeOut = -1f;
    public float titleScale = -1f;
    public float subtitleScale = -1f;
    public float iconScale = -1f;
    public int iconOffsetY = Integer.MIN_VALUE;
    public int posX = Integer.MIN_VALUE;
    public int posY = Integer.MIN_VALUE;
    public String particle = "default";
    public String animation = "default";
    public ItemStack confettiIcon = null;
    public int particleCount = -1;
    public int effectTier = -1;

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setString("style", style);
        tag.setString("showIcon", showIcon);
        tag.setString("subtitleText", subtitleText);
        tag.setFloat("duration", duration);
        tag.setFloat("fadeIn", fadeIn);
        tag.setFloat("fadeOut", fadeOut);
        tag.setFloat("titleScale", titleScale);
        tag.setFloat("subtitleScale", subtitleScale);
        tag.setFloat("iconScale", iconScale);
        tag.setInteger("iconOffsetY", iconOffsetY);
        tag.setInteger("posX", posX);
        tag.setInteger("posY", posY);
        tag.setString("particle", particle);
        tag.setString("animation", animation);
        if (confettiIcon != null) tag.setTag("confettiIcon", confettiIcon.writeToNBT(new NBTTagCompound()));
        tag.setInteger("particleCount", particleCount);
        tag.setInteger("effectTier", effectTier);
        return tag;
    }

    public static NoticeConfig readFromNBT(NBTTagCompound tag) {
        NoticeConfig c = new NoticeConfig();
        if (tag.hasKey("style")) c.style = tag.getString("style");
        if (tag.hasKey("showIcon")) c.showIcon = tag.getString("showIcon");
        if (tag.hasKey("subtitleText")) c.subtitleText = tag.getString("subtitleText");
        if (tag.hasKey("duration")) c.duration = tag.getFloat("duration");
        if (tag.hasKey("fadeIn")) c.fadeIn = tag.getFloat("fadeIn");
        if (tag.hasKey("fadeOut")) c.fadeOut = tag.getFloat("fadeOut");
        if (tag.hasKey("titleScale")) c.titleScale = tag.getFloat("titleScale");
        if (tag.hasKey("subtitleScale")) c.subtitleScale = tag.getFloat("subtitleScale");
        if (tag.hasKey("iconScale")) c.iconScale = tag.getFloat("iconScale");
        if (tag.hasKey("iconOffsetY")) c.iconOffsetY = tag.getInteger("iconOffsetY");
        if (tag.hasKey("posX")) c.posX = tag.getInteger("posX");
        if (tag.hasKey("posY")) c.posY = tag.getInteger("posY");
        if (tag.hasKey("particle")) c.particle = tag.getString("particle");
        if (tag.hasKey("animation")) c.animation = tag.getString("animation");
        if (tag.hasKey("confettiIcon"))
            c.confettiIcon = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("confettiIcon"));
        if (tag.hasKey("particleCount")) c.particleCount = tag.getInteger("particleCount");
        if (tag.hasKey("effectTier")) c.effectTier = tag.getInteger("effectTier");
        return c;
    }
}
