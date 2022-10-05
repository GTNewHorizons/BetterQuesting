package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.NbtBlockType;
import bq_standard.client.gui.tasks.PanelTaskInteractItem;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskInteractItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

public class TaskInteractItem extends TaskProgressableBase<Integer> {
    // region Properties
    @Nullable
    public BigItemStack targetItem = null;

    public final NbtBlockType targetBlock = new NbtBlockType(null);
    public boolean partialMatch = true;
    public boolean ignoreNBT = true;
    public boolean onInteract = true;
    public boolean onHit = false;
    public int required = 1;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        targetItem = BigItemStack.loadItemStackFromNBT(nbt.getCompoundTag("item"));
        targetBlock.readFromNBT(nbt.getCompoundTag("block"));
        ignoreNBT = nbt.getBoolean("ignoreNbt");
        partialMatch = nbt.getBoolean("partialMatch");
        required = nbt.getInteger("requiredUses");
        onInteract = nbt.getBoolean("onInteract");
        onHit = nbt.getBoolean("onHit");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setTag("item", targetItem != null ? targetItem.writeToNBT(new NBTTagCompound()) : new NBTTagCompound());
        nbt.setTag("block", targetBlock.writeToNBT(new NBTTagCompound()));
        nbt.setBoolean("ignoreNbt", ignoreNBT);
        nbt.setBoolean("partialMatch", partialMatch);
        nbt.setInteger("requiredUses", required);
        nbt.setBoolean("onInteract", onInteract);
        nbt.setBoolean("onHit", onHit);
        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return BQ_Standard.MODID + ".task.interact_item";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskInteractItem.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskInteractItem(rect, this);
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

    @SuppressWarnings("DuplicatedCode")
    public void onInteract(
            ParticipantInfo pInfo,
            DBEntry<IQuest> quest,
            ItemStack item,
            Block block,
            int meta,
            int x,
            int y,
            int z,
            boolean isHit) {
        if ((!onHit && isHit) || (!onInteract && !isHit)) return;

        if (targetBlock.b != Blocks.air && targetBlock.b != null) {
            if (block == Blocks.air || block == null) return;
            TileEntity tile = block.hasTileEntity(meta) ? pInfo.PLAYER.worldObj.getTileEntity(x, y, z) : null;
            NBTTagCompound tags = null;
            if (tile != null) {
                tags = new NBTTagCompound();
                tile.writeToNBT(tags);
            }

            int tmpMeta = (targetBlock.m < 0 || targetBlock.m == OreDictionary.WILDCARD_VALUE)
                    ? OreDictionary.WILDCARD_VALUE
                    : meta;
            boolean oreMatch = targetBlock.oreDict.length() > 0
                    && OreDictionary.getOres(targetBlock.oreDict).contains(new ItemStack(block, 1, tmpMeta));

            if ((!oreMatch && (block != targetBlock.b || (targetBlock.m >= 0 && meta != targetBlock.m)))
                    || !ItemComparison.CompareNBTTag(targetBlock.tags, tags, true)) {
                return;
            }
        }

        if (targetItem != null) {
            if (targetItem.hasOreDict()
                    && !ItemComparison.OreDictionaryMatch(
                            targetItem.getOreIngredient(),
                            targetItem.GetTagCompound(),
                            item,
                            !ignoreNBT,
                            partialMatch)) {
                return;
            } else if (!ItemComparison.StackMatch(targetItem.getBaseStack(), item, !ignoreNBT, partialMatch)) {
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
        if (targetBlock.getItemStack() != null) {
            texts.add(targetBlock.getItemStack().getBaseStack().getDisplayName());
        }
        if (targetItem != null) {
            texts.add(targetItem.getBaseStack().getDisplayName());
        }
        return texts;
    }
}
