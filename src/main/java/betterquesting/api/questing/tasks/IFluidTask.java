package betterquesting.api.questing.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.utils.ParticipantInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fluids.FluidStack;

import java.util.Map;
import java.util.UUID;

public interface IFluidTask extends ITask
{
	boolean canAcceptFluid(UUID owner, Map.Entry<UUID, IQuest> quest, FluidStack fluid);
	FluidStack submitFluid(EntityPlayerMP player, Map.Entry<UUID, IQuest> quest, FluidStack fluid);

    /**
     * @param fluids read-only list of fluids
     */
    default void retrieveFluids(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, FluidStack[] fluids) {}
}
