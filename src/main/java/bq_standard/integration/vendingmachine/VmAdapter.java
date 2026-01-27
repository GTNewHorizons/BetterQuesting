package bq_standard.integration.vendingmachine;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cubefury.vendingmachine.integration.betterquesting.BqAdapter;
import com.cubefury.vendingmachine.integration.betterquesting.gui.BqTradeGroup;
import com.cubefury.vendingmachine.network.handlers.NetSatisfiedQuestSync;

import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;

public class VmAdapter {

    public static void addBqConditions(UUID player, List<UUID> completedQuests) {
        for (UUID quest : completedQuests) {
            BqAdapter.INSTANCE.setQuestFinished(player, quest);
        }
    }

    public static void resetCompletedQuests(UUID player) {
        BqAdapter.INSTANCE.resetQuests(player);
    }

    public static void sendPlayerSatisfiedCache() {
        NetSatisfiedQuestSync.sendSync();
    }

    public static Set<UUID> getTrades(UUID quest) {
        return BqAdapter.INSTANCE.getTrades(quest);
    }

    public static boolean questHasTrades(UUID quest) {
        return BqAdapter.INSTANCE.questHasTrades(quest);
    }

    public static int addTradePanel(CanvasScrolling csReward, IGuiRect rectReward, UUID tradeGroup, int startYOffset) {
        return BqTradeGroup.addTradePanel(csReward, rectReward, tradeGroup, startYOffset);
    }

}
