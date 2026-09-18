package betterquesting.api2.client.gui.context;

import javax.annotation.Nullable;

/** Receives changes to the raw target currently hovered in the quest UI. */
public interface IQuestHoverListener {

    /**
     * Called when the hovered target changes, or when no target is currently hovered.
     *
     * @param target the raw hovered target, or {@code null} when no target is hovered
     */
    void onQuestHoverChanged(@Nullable Object target);
}
