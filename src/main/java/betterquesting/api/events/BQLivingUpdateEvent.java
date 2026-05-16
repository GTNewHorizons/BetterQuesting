package betterquesting.api.events;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * A replacement for LivingUpdateEvent, which could run several times per tick.
 * This event is being fired for every player on the server on server tick
 */
public class BQLivingUpdateEvent extends Event {

    public final EntityPlayerMP entityLiving;

    public BQLivingUpdateEvent(EntityPlayerMP player) {
        this.entityLiving = player;
    }
}
