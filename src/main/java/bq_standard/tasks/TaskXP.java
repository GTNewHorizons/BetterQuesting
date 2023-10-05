package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.XPHelper;
import bq_standard.client.gui.tasks.PanelTaskXP;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskXP;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class TaskXP extends TaskProgressableBase<Long> implements ITaskTickable {
    // region Properties
    public boolean levels = true;
    public int amount = 30;
    public boolean consume = true;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        amount = nbt.hasKey("amount", Constants.NBT.TAG_ANY_NUMERIC) ? nbt.getInteger("amount") : 30;
        levels = nbt.getBoolean("isLevels");
        consume = nbt.getBoolean("consume");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("amount", amount);
        nbt.setBoolean("isLevels", levels);
        nbt.setBoolean("consume", consume);
        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.xp";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskXP.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelTaskXP(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen screen, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
    // endregion Basic

    // region Progress
    @Override
    public Long getUsersProgress(UUID uuid) {
        Long n = userProgress.get(uuid);
        return n == null ? 0 : n;
    }

    @Override
    public Long readUserProgressFromNBT(NBTTagCompound nbt) {
        return nbt.getLong("value");
    }

    @Override
    public void writeUserProgressToNBT(NBTTagCompound nbt, Long progress) {
        nbt.setLong("value", progress);
    }
    // endregion Progress

    @Override
    public void detect(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest) {
        if (isComplete(pInfo.UUID)) return;

        long progress = getUsersProgress(pInfo.UUID);
        long rawXP = levels ? XPHelper.getLevelXP(amount) : amount;
        long plrXP = XPHelper.getPlayerXP(pInfo.PLAYER);
        long remaining = rawXP - progress;
        long cost = Math.min(remaining, plrXP);

        boolean changed = false;

        if (consume && cost != 0) {
            progress += cost;
            setUserProgress(pInfo.UUID, progress);
            XPHelper.addXP(pInfo.PLAYER, -cost);
            changed = true;
        } else if (!consume && progress != plrXP) {
            setUserProgress(pInfo.UUID, plrXP);
            changed = true;
        }

        long totalXP = getUsersProgress(pInfo.UUID);

        if (totalXP >= rawXP) {
            setComplete(pInfo.UUID);
            changed = true;
        }

        if (changed) // Needs to be here because even if no additional progress was added, a party memeber may have
        // completed the task anyway
        {
            pInfo.markDirty(quest.getKey());
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, @Nonnull Map.Entry<UUID, IQuest> quest) {
        if (consume || pInfo.PLAYER.ticksExisted % 60 != 0) return; // Every 3 seconds

        long curProg = getUsersProgress(pInfo.UUID);
        long nxtProg = XPHelper.getPlayerXP(pInfo.PLAYER);

        if (curProg != nxtProg) {
            setUserProgress(pInfo.UUID, XPHelper.getPlayerXP(pInfo.PLAYER));
            pInfo.markDirty(quest.getKey());
        }

        long rawXP = levels ? XPHelper.getLevelXP(amount) : amount;
        long totalXP = getUsersProgress(pInfo.UUID);

        if (totalXP >= rawXP) setComplete(pInfo.UUID);
    }
}
