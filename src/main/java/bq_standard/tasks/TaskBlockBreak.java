package bq_standard.tasks;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.NbtBlockType;
import bq_standard.client.gui.tasks.PanelTaskBlockBreak;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskBlockBreak;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.oredict.OreDictionary;

public class TaskBlockBreak extends TaskProgressableBase<int[]> {
    // region Properties
    public final List<NbtBlockType> blockTypes = new ArrayList<>();

    public TaskBlockBreak() {
        blockTypes.add(new NbtBlockType());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        blockTypes.clear();
        NBTTagList bList = nbt.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < bList.tagCount(); i++) {
            NbtBlockType block = new NbtBlockType();
            block.readFromNBT(bList.getCompoundTagAt(i));
            blockTypes.add(block);
        }

        if (nbt.hasKey("blockID", Constants.NBT.TAG_STRING)) {
            Block targetBlock = (Block) Block.blockRegistry.getObject(nbt.getString("blockID"));
            targetBlock = targetBlock != Blocks.air ? targetBlock : Blocks.log;
            int targetMeta = nbt.getInteger("blockMeta");
            NBTTagCompound targetNbt = nbt.getCompoundTag("blockNBT");
            int targetNum = nbt.getInteger("amount");

            NbtBlockType leg = new NbtBlockType();
            leg.b = targetBlock;
            leg.m = targetMeta;
            leg.tags = targetNbt;
            leg.n = targetNum;

            blockTypes.add(leg);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList bAry = new NBTTagList();
        for (NbtBlockType block : blockTypes) {
            bAry.appendTag(block.writeToNBT(new NBTTagCompound()));
        }
        nbt.setTag("blocks", bAry);

        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.block_break";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskBlockBreak.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, DBEntry<IQuest> quest) {
        return new PanelTaskBlockBreak(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen screen, DBEntry<IQuest> quest) {
        return null;
    }
    // endregion Basic

    // region Progress
    @Override
    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != blockTypes.size() ? new int[blockTypes.size()] : progress;
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public int[] readUserProgressFromNBT(NBTTagCompound nbt) {
        // region Legacy
        if (nbt.hasKey("data", Constants.NBT.TAG_LIST)) {
            int[] data = new int[blockTypes.size()];
            List<NBTBase> dNbt = NBTConverter.getTagList(nbt.getTagList("data", Constants.NBT.TAG_INT));
            for (int i = 0; i < data.length && i < dNbt.size(); i++) {
                data[i] = ((NBTPrimitive) dNbt.get(i)).func_150287_d();
            }
            return data;
        }
        // endregion Legacy
        final int[] data = nbt.getIntArray("data");
        final int[] progress = new int[blockTypes.size()];
        System.arraycopy(data, 0, progress, 0, Math.min(data.length, progress.length));
        return progress;
    }

    @Override
    public void writeUserProgressToNBT(NBTTagCompound nbt, int[] progress) {
        nbt.setIntArray("data", progress);
    }
    // endregion Progress

    @Override
    public void detect(ParticipantInfo pInfo, DBEntry<IQuest> quest) {
        pInfo.ALL_UUIDS.forEach((uuid) -> {
            if (isComplete(uuid)) return;

            int[] tmp = getUsersProgress(uuid);
            for (int i = 0; i < blockTypes.size(); i++) {
                NbtBlockType block = blockTypes.get(i);
                if (block != null && tmp[i] < block.n) return;
            }
            setComplete(uuid);
        });

        pInfo.markDirtyParty(Collections.singletonList(quest.getID()));
    }

    public void onBlockBreak(ParticipantInfo pInfo, DBEntry<IQuest> quest, Block block, int meta, int x, int y, int z) {
        TileEntity tile = block.hasTileEntity(meta) ? pInfo.PLAYER.worldObj.getTileEntity(x, y, z) : null;
        NBTTagCompound tags = null;
        if (tile != null) {
            tags = new NBTTagCompound();
            tile.writeToNBT(tags);
        }

        final List<Tuple2<UUID, int[]>> progress = getBulkProgress(pInfo.ALL_UUIDS);
        boolean changed = false;

        for (int i = 0; i < blockTypes.size(); i++) {
            NbtBlockType targetBlock = blockTypes.get(i);

            int tmpMeta = (targetBlock.m < 0 || targetBlock.m == OreDictionary.WILDCARD_VALUE)
                    ? OreDictionary.WILDCARD_VALUE
                    : meta;
            boolean oreMatch = targetBlock.oreDict.length() > 0
                    && OreDictionary.getOres(targetBlock.oreDict).contains(new ItemStack(block, 1, tmpMeta));
            final int index = i;

            if ((oreMatch || (block == targetBlock.b && (targetBlock.m < 0 || meta == targetBlock.m)))
                    && ItemComparison.CompareNBTTag(targetBlock.tags, tags, true)) {
                progress.forEach((entry) -> {
                    if (entry.getSecond()[index] >= targetBlock.n) return;
                    entry.getSecond()[index]++;
                });
                changed = true;
                break; // NOTE: We're only tracking one break at a time so doing all the progress setting above is fine
            }
        }

        if (changed) {
            setBulkProgress(progress);
            detect(pInfo, quest);
        }
    }

    @Override
    public List<String> getTextsForSearch() {
        List<String> texts = new ArrayList<>();
        for (NbtBlockType block : blockTypes) {
            if (block.getItemStack() != null) {
                texts.add(block.getItemStack().getBaseStack().getDisplayName());
            }
        }
        return texts;
    }
}
