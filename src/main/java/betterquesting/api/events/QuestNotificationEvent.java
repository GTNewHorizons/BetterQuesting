package betterquesting.api.events;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@Cancelable
@SideOnly(Side.CLIENT)
public class QuestNotificationEvent extends Event {

    private final String questName;
    private final ItemStack icon;
    private final String sound;
    private String particleEffect;
    private String iconAnimation;
    private ItemStack confettiIcon;

    public QuestNotificationEvent(String questName, ItemStack icon, String sound, String particleEffect,
        String iconAnimation, ItemStack confettiIcon) {
        this.questName = questName;
        this.icon = icon;
        this.sound = sound;
        this.particleEffect = particleEffect;
        this.iconAnimation = iconAnimation;
        this.confettiIcon = confettiIcon;
    }

    public String getQuestName() {
        return questName;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public String getSound() {
        return sound;
    }

    public String getParticleEffect() {
        return particleEffect;
    }

    public void setParticleEffect(String particleEffect) {
        this.particleEffect = particleEffect;
    }

    public String getIconAnimation() {
        return iconAnimation;
    }

    public void setIconAnimation(String iconAnimation) {
        this.iconAnimation = iconAnimation;
    }

    public ItemStack getConfettiIcon() {
        return confettiIcon;
    }

    public void setConfettiIcon(ItemStack confettiIcon) {
        this.confettiIcon = confettiIcon;
    }
}
