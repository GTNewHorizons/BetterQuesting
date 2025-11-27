package bq_standard.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.item.ItemStack;

import betterquesting.api.api.QuestingAPI;

public class PlayerContainerListener implements ICrafting {

    private static final HashMap<UUID, PlayerContainerListener> LISTEN_MAP = new HashMap<>();
    private EntityPlayer player;

    private PlayerContainerListener(@Nonnull EntityPlayer player) {
        this.player = player;
    }

    static void refreshListener(@Nonnull EntityPlayer player) {
        UUID uuid = QuestingAPI.getQuestingUUID(player);
        PlayerContainerListener listener = LISTEN_MAP.get(uuid);
        if (listener != null) {
            listener.player = player;
        } else {
            listener = new PlayerContainerListener(player);
            LISTEN_MAP.put(uuid, listener);
        }

        try {
            player.inventoryContainer.addCraftingToCrafters(listener);
        } catch (Exception ignored) {}
    }

    @Override
    public void sendContainerAndContentsToPlayer(Container container, List nonNullList) {
        updateTasks();
    }

    @Override
    public void sendSlotContents(Container container, int i, ItemStack itemStack) {
        // Ignore changes outside of main inventory (e.g. crafting grid and armor)
        if (i >= 9 && i <= 44) {
            updateTasks();
        }
    }

    @Override
    public void sendProgressBarUpdate(Container container, int i, int i1) {}

    private void updateTasks() {
        EventHandler.schedulePlayerInventoryCheck(player);
    }

    public static void cleanup() {
        LISTEN_MAP.clear();
    }
}
