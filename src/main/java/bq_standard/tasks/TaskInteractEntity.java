package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.tasks.PanelTaskInteractEntity;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskInteractEntity;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public class TaskInteractEntity extends TaskProgressableBase<Integer> {
    // region Properties
    @Nullable
    public BigItemStack targetItem = null;

    public boolean ignoreItemNBT = false;
    public boolean partialItemMatch = true;

    public String entityID = "Villager";
    public NBTTagCompound entityTags = new NBTTagCompound();
    public boolean entitySubtypes = true;
    public boolean ignoreEntityNBT = true;

    public boolean onInteract = true;
    public boolean onHit = false;
    public int required = 1;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        targetItem = BigItemStack.loadItemStackFromNBT(nbt.getCompoundTag("item"));
        ignoreItemNBT = nbt.getBoolean("ignoreItemNBT");
        partialItemMatch = nbt.getBoolean("partialItemMatch");

        entityID = nbt.getString("targetID");
        entityTags = nbt.getCompoundTag("targetNBT");
        ignoreEntityNBT = nbt.getBoolean("ignoreTargetNBT");
        entitySubtypes = nbt.getBoolean("targetSubtypes");

        required = nbt.getInteger("requiredUses");
        onInteract = nbt.getBoolean("onInteract");
        onHit = nbt.getBoolean("onHit");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("item", targetItem != null ? targetItem.writeToNBT(new NBTTagCompound()) : new NBTTagCompound());
        nbt.setBoolean("ignoreItemNBT", ignoreItemNBT);
        nbt.setBoolean("partialItemMatch", partialItemMatch);

        nbt.setString("targetID", entityID);
        nbt.setTag("targetNBT", entityTags);
        nbt.setBoolean("ignoreTargetNBT", ignoreEntityNBT);
        nbt.setBoolean("targetSubtypes", entitySubtypes);

        nbt.setInteger("requiredUses", required);
        nbt.setBoolean("onInteract", onInteract);
        nbt.setBoolean("onHit", onHit);
        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return BQ_Standard.MODID + ".task.interact_entity";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskInteractEntity.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskInteractEntity(rect, this);
    }

    @Override
    @Nullable
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, DBEntry<IQuest> quest) {
        return null;
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

    public void onInteract(ParticipantInfo pInfo, DBEntry<IQuest> quest, ItemStack item, Entity entity, boolean isHit) {
        if ((!onHit && isHit) || (!onInteract && !isHit)) return;

        //noinspection unchecked
        Class<? extends Entity> targetClass = (Class<? extends Entity>) EntityList.stringToClassMapping.get(entityID);
        if (targetClass == null) return; // No idea what we're looking for

        Class<? extends Entity> subjectClass = entity.getClass();
        String subjectRes = EntityList.getEntityString(entity);
        if (subjectRes == null) return; // This isn't a registered entity!

        if (entitySubtypes ? !targetClass.isAssignableFrom(subjectClass) : !subjectRes.equals(entityID)) return;

        if (!ignoreEntityNBT) {
            NBTTagCompound subjectTags = new NBTTagCompound();
            entity.writeToNBTOptional(subjectTags);
            if (!ItemComparison.CompareNBTTag(entityTags, subjectTags, true)) return;
        }

        if (targetItem != null) {
            if (targetItem.hasOreDict()
                    && !ItemComparison.OreDictionaryMatch(
                            targetItem.getOreIngredient(),
                            targetItem.GetTagCompound(),
                            item,
                            !ignoreItemNBT,
                            partialItemMatch)) {
                return;
            } else if (!ItemComparison.StackMatch(targetItem.getBaseStack(), item, !ignoreItemNBT, partialItemMatch)) {
                return;
            }
        }

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
        List<String> texts = new ArrayList<>();
        texts.add(entityID);
        if (targetItem != null) {
            texts.add(targetItem.getBaseStack().getDisplayName());
        }
        return texts;
    }
}
