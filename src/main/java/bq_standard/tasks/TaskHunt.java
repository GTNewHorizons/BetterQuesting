package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.editors.tasks.GuiEditTaskHunt;
import bq_standard.client.gui.tasks.PanelTaskHunt;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskHunt;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;

public class TaskHunt extends TaskProgressableBase<Integer> {
    // region Properties
    public String idName = "Zombie";
    public String damageType = "";
    public int required = 1;
    public boolean ignoreNBT = true;
    public boolean subtypes = true;

    /**
     * NBT representation of the intended target. Used only for NBT comparison checks
     */
    public NBTTagCompound targetTags = new NBTTagCompound();

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        idName = nbt.getString("target");
        required = nbt.getInteger("required");
        subtypes = nbt.getBoolean("subtypes");
        ignoreNBT = nbt.getBoolean("ignoreNBT");
        targetTags = nbt.getCompoundTag("targetNBT");
        damageType = nbt.getString("damageType");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setString("target", idName);
        nbt.setInteger("required", required);
        nbt.setBoolean("subtypes", subtypes);
        nbt.setBoolean("ignoreNBT", ignoreNBT);
        nbt.setTag("targetNBT", targetTags);
        nbt.setString("damageType", damageType);

        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return BQ_Standard.MODID + ".task.hunt";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskHunt.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskHunt(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return new GuiEditTaskHunt(parent, quest, this);
    }
    // endregion Basic

    // region Progress
    @Override
    public Integer getUsersProgress(UUID uuid) {
        Integer n = userProgress.get(uuid);
        return n == null ? 0 : n;
    }

    @Override
    public Integer readUserProgressFromNBT(NBTTagCompound nbt) {
        return nbt.getInteger("value");
    }

    @Override
    public void writeUserProgressToNBT(NBTTagCompound nbt, Integer progress) {
        nbt.setInteger("value", progress);
    }
    // endregion Progress

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        final List<Tuple2<UUID, Integer>> progress = getBulkProgress(pInfo.ALL_UUIDS);

        progress.forEach((value) -> {
            if (value.getSecond() >= required) setComplete(value.getFirst());
        });

        pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
    }

    @SuppressWarnings({"unchecked", "DuplicatedCode"})
    public void onKilledByPlayer(
            ParticipantInfo pInfo, DBEntry<IQuest> quest, EntityLivingBase entity, DamageSource source) {
        if (damageType.length() > 0 && (source == null || !damageType.equalsIgnoreCase(source.damageType))) return;

        Class<? extends Entity> subject = entity.getClass();
        Class<? extends Entity> target = (Class<? extends Entity>) EntityList.stringToClassMapping.get(idName);
        String subjectID = EntityList.getEntityString(entity);

        if (subjectID == null || target == null) {
            return; // Missing necessary data
        } else if (subtypes && !target.isAssignableFrom(subject)) {
            return; // This is not the intended target or sub-type
        } else if (!subtypes && !subjectID.equals(idName)) {
            return; // This isn't the exact target required
        }

        NBTTagCompound subjectTags = new NBTTagCompound();
        entity.writeToNBTOptional(subjectTags);
        if (!ignoreNBT && !ItemComparison.CompareNBTTag(targetTags, subjectTags, true)) return;

        final List<Tuple2<UUID, Integer>> progress = getBulkProgress(pInfo.ALL_UUIDS);

        progress.forEach((value) -> {
            if (isComplete(value.getFirst())) return;
            int np = Math.min(required, value.getSecond() + 1);
            setUserProgress(value.getFirst(), np);
            if (np >= required) setComplete(value.getFirst());
        });

        pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
    }

    @Override
    public List<String> getTextsForSearch() {
        return Collections.singletonList(idName);
    }
}
