package betterquesting.api2.client.gui.context;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Dispatches interactive quest UI tooltip extension points to registered providers. */
public class QuestTooltipRegistry {

    private static final CopyOnWriteArrayList<IQuestTooltipProvider> providers = new CopyOnWriteArrayList<>();

    private QuestTooltipRegistry() {}

    /** Registers a provider that can handle an interactive quest UI target. */
    public static void register(IQuestTooltipProvider provider) {
        providers.addIfAbsent(Objects.requireNonNull(provider, "provider"));
    }

    /** Appends registered tooltip content in provider registration order. */
    public static void appendTooltip(Object target, List<String> tooltip) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(tooltip, "tooltip");

        for (IQuestTooltipProvider provider : providers) {
            provider.appendTooltip(target, tooltip);
        }
    }
}
