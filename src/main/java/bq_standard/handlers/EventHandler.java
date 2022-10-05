package bq_standard.handlers;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.BQLivingUpdateEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.core.BQ_Standard;
import bq_standard.network.handlers.NetLootSync;
import bq_standard.tasks.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemSmeltedEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.IntSupplier;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.Action;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.commons.lang3.Validate;

@SuppressWarnings("unused")
public class EventHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == null
                || event.entityPlayer instanceof FakePlayer
                || event.entityPlayer.worldObj.isRemote
                || event.isCanceled()) return;

        EntityPlayer player = event.entityPlayer;
        ParticipantInfo pInfo = new ParticipantInfo(player);

        Block block = player.worldObj.getBlock(event.x, event.y, event.z);
        int meta = player.worldObj.getBlockMetadata(event.x, event.y, event.z);
        boolean isHit = event.action == Action.LEFT_CLICK_BLOCK;

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskInteractItem)
                    ((TaskInteractItem) task.getValue())
                            .onInteract(
                                    pInfo, entry, player.getHeldItem(), block, meta, event.x, event.y, event.z, isHit);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityAttack(AttackEntityEvent event) {
        if (event.entityPlayer == null
                || event.entityPlayer instanceof FakePlayer
                || event.target == null
                || event.entityPlayer.worldObj.isRemote
                || event.isCanceled()) return;

        EntityPlayer player = event.entityPlayer;
        ParticipantInfo pInfo = new ParticipantInfo(player);

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskInteractEntity)
                    ((TaskInteractEntity) task.getValue())
                            .onInteract(pInfo, entry, player.getHeldItem(), event.target, true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.entityPlayer == null
                || event.entityPlayer instanceof FakePlayer
                || event.target == null
                || event.entityPlayer.worldObj.isRemote
                || event.isCanceled()) return;

        EntityPlayer player = event.entityPlayer;
        ParticipantInfo pInfo = new ParticipantInfo(player);

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskInteractEntity)
                    ((TaskInteractEntity) task.getValue())
                            .onInteract(pInfo, entry, player.getHeldItem(), event.target, false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemCrafted(ItemCraftedEvent event) {
        if (event.player == null || event.player instanceof FakePlayer || event.player.worldObj.isRemote) return;

        ParticipantInfo pInfo = new ParticipantInfo(event.player);

        IntSupplier realStackSizeSupplier = null;
        if (event.craftMatrix instanceof InventoryCrafting
                && event.crafting.stackSize == 0) // Hack for broken-ass shift clicking reporting empty stacks
        {
            realStackSizeSupplier = new IntSupplier() {
                private int stackSize = -1;

                private int get() {
                    ItemStack result = CraftingManager.getInstance()
                            .findMatchingRecipe((InventoryCrafting) event.craftMatrix, event.player.worldObj);
                    return result != null ? result.stackSize : event.crafting.stackSize;
                }

                @Override
                public int getAsInt() {
                    if (stackSize < 0) stackSize = get();
                    return stackSize;
                }
            };
        }

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskCrafting)
                    ((TaskCrafting) task.getValue()).onItemCraft(pInfo, entry, event.crafting, realStackSizeSupplier);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemSmelted(
            ItemSmeltedEvent
                    event) // This event is even more busted than crafting when shift clicking (only ever reports 2
                // empty stacks regardless of actual amount)
            {
        if (event.player == null || event.player instanceof FakePlayer || event.player.worldObj.isRemote) return;

        ParticipantInfo pInfo = new ParticipantInfo(event.player);

        ItemStack refStack = event.smelting.copy();
        if (refStack.stackSize <= 0)
            refStack.stackSize = 1; // Doesn't really fix much but it's better than nothing I suppose

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskCrafting)
                    ((TaskCrafting) task.getValue()).onItemSmelt(pInfo, entry, refStack);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemAnvil(AnvilRepairEvent event) // Somehow actually works as intended unlike other crafting methods
            {
        if (event.entityPlayer == null
                || event.entityPlayer instanceof FakePlayer
                || event.entityPlayer.worldObj.isRemote) return;

        ParticipantInfo pInfo = new ParticipantInfo(event.entityPlayer);

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskCrafting)
                    ((TaskCrafting) task.getValue()).onItemAnvil(pInfo, entry, event.output.copy());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityKilled(LivingDeathEvent event) {
        if (event.source == null
                || !(event.source.getEntity() instanceof EntityPlayer)
                || event.source.getEntity() instanceof FakePlayer
                || event.source.getEntity().worldObj.isRemote
                || event.isCanceled()) return;

        EntityPlayer player = (EntityPlayer) event.source.getEntity();
        ParticipantInfo pInfo = new ParticipantInfo(player);

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskHunt)
                    ((TaskHunt) task.getValue()).onKilledByPlayer(pInfo, entry, event.entityLiving, event.source);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BreakEvent event) {
        if (event.getPlayer() == null
                || event.getPlayer() instanceof FakePlayer
                || event.getPlayer().worldObj.isRemote
                || event.isCanceled()) return;

        ParticipantInfo pInfo = new ParticipantInfo(event.getPlayer());

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof TaskBlockBreak)
                    ((TaskBlockBreak) task.getValue())
                            .onBlockBreak(pInfo, entry, event.block, event.blockMetadata, event.x, event.y, event.z);
            }
        }
    }

    @SubscribeEvent
    public void onEntityLiving(BQLivingUpdateEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)
                || event.entityLiving.worldObj.isRemote
                || event.entityLiving.ticksExisted % 20 != 0
                || QuestingAPI.getAPI(ApiReference.SETTINGS).getProperty(NativeProps.EDIT_MODE)) return;

        EntityPlayer player = (EntityPlayer) event.entityLiving;
        ParticipantInfo pInfo = new ParticipantInfo(player);

        for (DBEntry<IQuest> entry : QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
            for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                if (task.getValue() instanceof ITaskTickable) {
                    ((ITaskTickable) task.getValue()).tickTask(pInfo, entry);
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityCreated(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof EntityPlayer) || event.entity.worldObj.isRemote) return;

        PlayerContainerListener.refreshListener((EntityPlayer) event.entity);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent event) {
        if (event.modID.equalsIgnoreCase(BQ_Standard.MODID)) {
            ConfigHandler.config.save();
            ConfigHandler.initConfigs();
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        if (!event.player.worldObj.isRemote && event.player instanceof EntityPlayerMP) {
            NetLootSync.sendSync((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!event.world.isRemote && LootSaveLoad.INSTANCE.worldDir != null && event.world.provider.dimensionId == 0) {
            LootSaveLoad.INSTANCE.SaveLoot();
        }
    }

    private static final ArrayDeque<FutureTask> serverTasks = new ArrayDeque<>();
    private static Thread serverThread = null;

    private static HashSet<EntityPlayer> playerInventoryUpdates = new HashSet<>();

    /**
     * Schedules checking player's inventory on the next server tick.
     * Deduplicates requests to avoid scanning it multiple times per tick.
     */
    public static void schedulePlayerInventoryCheck(EntityPlayer player) {
        synchronized (playerInventoryUpdates) {
            playerInventoryUpdates.add(player);
        }
    }

    // NOTE: This is slightly different to the version in the base mod. This one will not immediately run tasks even if
    // it's from the same thread.
    public static <T> ListenableFuture<T> scheduleServerTask(Callable<T> task) {
        Validate.notNull(task);

        ListenableFutureTask<T> listenablefuturetask = ListenableFutureTask.create(task);

        synchronized (serverTasks) {
            serverTasks.add(listenablefuturetask);
            return listenablefuturetask;
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase != Phase.START) return;
        if (serverThread == null) serverThread = Thread.currentThread();

        synchronized (serverTasks) {
            while (!serverTasks.isEmpty()) serverTasks.poll().run();
        }

        synchronized (playerInventoryUpdates) {
            for (EntityPlayer player : playerInventoryUpdates) {
                if (player == null || player.inventory == null) {
                    continue;
                }
                ParticipantInfo pInfo = new ParticipantInfo(player);

                for (DBEntry<IQuest> entry :
                        QuestingAPI.getAPI(ApiReference.QUEST_DB).bulkLookup(pInfo.getSharedQuests())) {
                    for (DBEntry<ITask> task : entry.getValue().getTasks().getEntries()) {
                        if (task.getValue() instanceof ITaskInventory)
                            ((ITaskInventory) task.getValue()).onInventoryChange(entry, pInfo);
                    }
                }
            }
            playerInventoryUpdates.clear();
        }
    }
}
