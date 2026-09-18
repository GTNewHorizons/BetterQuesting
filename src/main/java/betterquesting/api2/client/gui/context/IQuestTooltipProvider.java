package betterquesting.api2.client.gui.context;

import java.util.List;

/** Appends client-side tooltip lines for an interactive quest UI target. */
public interface IQuestTooltipProvider {

    /**
     * Appends lines when the provider recognizes the raw interactive target.
     *
     * @param target  the raw target stored by the interactive UI element
     * @param tooltip the mutable tooltip line list
     */
    void appendTooltip(Object target, List<String> tooltip);
}
