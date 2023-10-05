package bq_standard.tasks;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.utils.ParticipantInfo;
import betterquesting.api2.utils.Tuple2;
import bq_standard.client.gui.tasks.PanelTaskCrafting;
import bq_standard.core.BQ_Standard;
import bq_standard.tasks.base.TaskProgressableBase;
import bq_standard.tasks.factory.FactoryTaskCrafting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.stats.StatList;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

public class TaskCrafting extends TaskProgressableBase<int[]> implements ITaskItemInput {
    // region Properties
    public final List<BigItemStack> requiredItems = new ArrayList<>();
    public boolean partialMatch = true;
    public boolean ignoreNBT = true;
    public boolean allowAnvil = false;
    public boolean allowSmelt = true;
    public boolean allowCraft = true;
    public boolean allowCraftedFromStatistics = false;

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        partialMatch = nbt.getBoolean("partialMatch");
        ignoreNBT = nbt.getBoolean("ignoreNBT");
        if (nbt.hasKey("allowCraft")) allowCraft = nbt.getBoolean("allowCraft");
        if (nbt.hasKey("allowCraftedFromStatistics"))
            allowCraftedFromStatistics = nbt.getBoolean("allowCraftedFromStatistics");
        if (nbt.hasKey("allowSmelt")) allowSmelt = nbt.getBoolean("allowSmelt");
        if (nbt.hasKey("allowAnvil")) allowAnvil = nbt.getBoolean("allowAnvil");

        requiredItems.clear();
        NBTTagList iList = nbt.getTagList("requiredItems", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < iList.tagCount(); i++) {
            requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("partialMatch", partialMatch);
        nbt.setBoolean("ignoreNBT", ignoreNBT);
        nbt.setBoolean("allowCraft", allowCraft);
        nbt.setBoolean("allowCraftedFromStatistics", allowCraftedFromStatistics);
        nbt.setBoolean("allowSmelt", allowSmelt);
        nbt.setBoolean("allowAnvil", allowAnvil);

        NBTTagList itemArray = new NBTTagList();
        for (BigItemStack stack : this.requiredItems) {
            itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound()));
        }
        nbt.setTag("requiredItems", itemArray);

        return nbt;
    }
    // endregion Properties

    // region Basic
    @Override
    public String getUnlocalisedName() {
        return "bq_standard.task.crafting";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskCrafting.INSTANCE.getRegistryName();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IGuiPanel getTaskGui(IGuiRect rect, Map.Entry<UUID, IQuest> context) {
        return new PanelTaskCrafting(rect, this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, Map.Entry<UUID, IQuest> quest) {
        return null;
    }
    // endregion Basic

    // region Progress
    @Override
    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != requiredItems.size() ? new int[requiredItems.size()] : progress;
    }

    @Override
    public int[] readUserProgressFromNBT(NBTTagCompound nbt) {
        // region Legacy
        if (nbt.hasKey("data", Constants.NBT.TAG_LIST)) {
            int[] data = new int[requiredItems.size()];
            List<NBTBase> dJson = NBTConverter.getTagList(nbt.getTagList("data", Constants.NBT.TAG_INT));
            for (int i = 0; i < data.length && i < dJson.size(); i++) {
                try {
                    data[i] = ((NBTPrimitive) dJson.get(i)).func_150287_d();
                } catch (Exception e) {
                    BQ_Standard.logger.log(Level.ERROR, "Incorrect task progress format", e);
                }
            }
            return data;
        }
        // endregion Legacy
        final int[] data = nbt.getIntArray("data");
        final int[] progress = new int[requiredItems.size()];
        System.arraycopy(data, 0, progress, 0, Math.min(data.length, progress.length));
        return progress;
    }

    @Override
    public void writeUserProgressToNBT(NBTTagCompound nbt, int[] progress) {
        nbt.setIntArray("data", progress);
    }
    // endregion Progress

    @Override
    public void detect(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest) {
        pInfo.ALL_UUIDS.forEach((uuid) -> {
            if (isComplete(uuid)) return;

            int[] tmp = getUsersProgress(uuid);
            boolean progressChanged = false;
            boolean completed = true;
            for (int i = 0; i < requiredItems.size(); i++) {
                BigItemStack rStack = requiredItems.get(i);
                EntityPlayerMP player;
                if ((tmp[i] < rStack.stackSize)
                        && allowCraftedFromStatistics
                        && (player = QuestingAPI.getPlayer(uuid)) != null) {
                    StatisticsFile stats = player.func_147099_x();
                    int itemId = Item.getIdFromItem(rStack.getBaseStack().getItem());
                    if (itemId < StatList.objectCraftStats.length && StatList.objectCraftStats[itemId] != null) {
                        // This has a very misleading deobf name, writeStat looks up the given stat in the hashmap and
                        // returns it as an int.
                        int alreadyCrafted = stats.writeStat(StatList.objectCraftStats[itemId]);
                        if (alreadyCrafted > tmp[i]) {
                            tmp[i] = alreadyCrafted;
                            progressChanged = true;
                        }
                    }
                }
                if (tmp[i] < rStack.stackSize) {
                    completed = false;
                }
            }
            if (progressChanged) {
                setUserProgress(uuid, tmp);
            }
            if (completed) {
                setComplete(uuid);
            }
        });

        pInfo.markDirtyParty(quest.getKey());
    }

    public void onItemCraft(
            ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, ItemStack stack, IntSupplier realStackSizeSupplier) {
        if (!allowCraft) return;
        onItemInternal(pInfo, quest, stack, realStackSizeSupplier);
    }

    public void onItemSmelt(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, ItemStack stack) {
        if (!allowSmelt) return;
        onItemInternal(pInfo, quest, stack);
    }

    public void onItemAnvil(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, ItemStack stack) {
        if (!allowAnvil) return;
        onItemInternal(pInfo, quest, stack);
    }

    private void onItemInternal(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, ItemStack stack) {
        onItemInternal(pInfo, quest, stack, null);
    }

    private void onItemInternal(
            ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, ItemStack stack, IntSupplier realStackSizeSupplier) {
        // ignore null stack
        // ignore negatively sized stack only if it's indeed the real stack size
        if (stack == null || (stack.stackSize <= 0 && realStackSizeSupplier == null)) return;

        final List<Tuple2<UUID, int[]>> progress = getBulkProgress(pInfo.ALL_UUIDS);
        boolean changed = false;
        int realStackSizeCache = realStackSizeSupplier == null ? Math.max(0, stack.stackSize) : -1;

        for (int i = 0; i < requiredItems.size(); i++) {
            final BigItemStack rStack = requiredItems.get(i);
            final int index = i;

            if (ItemComparison.StackMatch(rStack.getBaseStack(), stack, !ignoreNBT, partialMatch)
                    || ItemComparison.OreDictionaryMatch(
                            rStack.getOreIngredient(), rStack.GetTagCompound(), stack, !ignoreNBT, partialMatch)) {
                int realStackSize;
                if (realStackSizeCache < 0) {
                    realStackSize = realStackSizeSupplier.getAsInt();
                    if (realStackSize <= 0)
                        // bruh
                        return;
                    realStackSizeCache = realStackSize;
                } else {
                    realStackSize = realStackSizeCache;
                }
                progress.stream()
                        .filter(e -> e.getSecond()[index] < rStack.stackSize)
                        .forEach(e -> e.getSecond()[index] =
                                Math.min(e.getSecond()[index] + realStackSize, rStack.stackSize));
                changed = true;
            }
        }

        if (changed) {
            setBulkProgress(progress);
            detect(pInfo, quest);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    @Override
    public List<String> getTextsForSearch() {
        List<String> texts = new ArrayList<>();
        for (BigItemStack bigStack : requiredItems) {
            ItemStack stack = bigStack.getBaseStack();
            texts.add(stack.getDisplayName());
            if (bigStack.hasOreDict()) {
                texts.add(bigStack.getOreDict());
            }
        }
        return texts;
    }

    @Override
    public List<BigItemStack> getItemInputs() {
        return requiredItems;
    }
}
