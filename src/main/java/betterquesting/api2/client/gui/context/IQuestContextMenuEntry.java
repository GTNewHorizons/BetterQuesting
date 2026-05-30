package betterquesting.api2.client.gui.context;

import java.util.UUID;

import betterquesting.api.questing.IQuest;

/**
 * Provides additional entries for the quest right-click context menu.
 * Register via {@link QuestContextMenuRegistry#register(IQuestContextMenuEntry)}.
 */
public interface IQuestContextMenuEntry {

    /** Display label shown in the context menu. */
    String getLabel(UUID questId, IQuest quest);

    /** Called when the player clicks this entry. The popup is closed automatically after this runs. */
    Runnable getAction(UUID questId, IQuest quest);
}
