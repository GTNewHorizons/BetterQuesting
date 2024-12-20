package betterquesting.api.questing.rewards;

import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;

public abstract class AbstractReward implements IReward {

    protected boolean ignoreDisabled = getDefaultIgnoreDisabled();

    @Override
    public final void claimReward(EntityPlayer player, Entry<UUID, IQuest> quest) {
        if (!ignoreDisabled && BQ_Settings.noRewards) return;
        claimReward0(player, quest);
    }

    /**
     *
     * @return a value independent of {@link #ignoreDisabled}. preferably a constant
     */
    protected boolean getDefaultIgnoreDisabled() {
        return false;
    }

    protected abstract void claimReward0(EntityPlayer player, Entry<UUID, IQuest> quest);

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("ignoreDisabled")) ignoreDisabled = nbt.getBoolean("ignoreDisabled");
        else ignoreDisabled = getDefaultIgnoreDisabled();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("ignoreDisabled", ignoreDisabled);
        return nbt;
    }

    public boolean isIgnoreDisabled() {
        return ignoreDisabled;
    }

    public void setIgnoreDisabled(boolean ignoreDisabled) {
        this.ignoreDisabled = ignoreDisabled;
    }
}
