package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.client.gui.editors.tasks.GuiEditTaskMeeting;
import bq_standard.client.gui.tasks.PanelTaskMeeting;
import bq_standard.tasks.base.TaskBase;
import bq_standard.tasks.factory.FactoryTaskMeeting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TaskMeeting extends TaskBase implements ITaskTickable {
    // region Properties
    public String idName = "Villager";
    public int range = 4;
    public int amount = 1;
    public boolean ignoreNBT = true;
    public boolean subtypes = true;

    /**
     * NBT representation of the intended target. Used only for NBT comparison checks
     */
    public NBTTagCompound targetTags = new NBTTagCompound();

    @Override
    public void readFromNBT(NBTTagCompound json) {
        idName = json.hasKey("target", 8) ? json.getString("target") : "Villager";
        range = json.getInteger("range");
        amount = json.getInteger("amount");
        subtypes = json.getBoolean("subtypes");
        ignoreNBT = json.getBoolean("ignoreNBT");
        targetTags = json.getCompoundTag("targetNBT");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound json) {
        json.setString("target", idName);
        json.setInteger("range", range);
        json.setInteger("amount", amount);
        json.setBoolean("subtypes", subtypes);
        json.setBoolean("ignoreNBT", ignoreNBT);
        json.setTag("targetNBT", targetTags);

        return json;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.meeting";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskMeeting.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest) {
        return new GuiEditTaskMeeting(parent, quest, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> quest) {
        return new PanelTaskMeeting(rect, this);
    }
    // endregion Basic

    @Override
    public void detect(@Nonnull ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest) {
        if (!pInfo.PLAYER.isEntityAlive()) return;

        //noinspection unchecked
        List<Entity> list = pInfo.PLAYER.worldObj.getEntitiesWithinAABBExcludingEntity(
                pInfo.PLAYER, pInfo.PLAYER.boundingBox.expand(range, range, range));
        //noinspection unchecked
        Class<? extends Entity> target = (Class<? extends Entity>) EntityList.stringToClassMapping.get(idName);
        if (target == null) return;

        int n = 0;

        for (Entity entity : list) {
            Class<? extends Entity> subject = entity.getClass();
            String subjectID = EntityList.getEntityString(entity);

            if (subjectID == null) {
                continue;
            } else if (subtypes && !target.isAssignableFrom(subject)) {
                continue; // This is not the intended target or sub-type
            } else if (!subtypes && !subjectID.equals(idName)) {
                continue; // This isn't the exact target required
            }

            if (!ignoreNBT) {
                NBTTagCompound subjectTags = new NBTTagCompound();
                entity.writeToNBTOptional(subjectTags);
                if (!ItemComparison.CompareNBTTag(targetTags, subjectTags, true)) continue;
            }

            if (++n >= amount) {
                pInfo.ALL_UUIDS.forEach((uuid) -> {
                    if (!isComplete(uuid)) setComplete(uuid);
                });
                pInfo.markDirtyParty(quest.getKey());
                return;
            }
        }
    }

    @Override
    public void tickTask(@Nonnull ParticipantInfo pInfo, @Nonnull Map.Entry<UUID, IQuest> quest) {
        if (pInfo.PLAYER.ticksExisted % 60 == 0) detect(pInfo, quest);
    }

    @Override
    public List<String> getTextsForSearch() {
        return Collections.singletonList(idName);
    }
}
