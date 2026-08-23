package betterquesting.api2.client.gui.context;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

/** Tracks and publishes the quest currently hovered in the quest UI. */
public class QuestHoverRegistry {

    private static final CopyOnWriteArrayList<IQuestHoverListener> listeners = new CopyOnWriteArrayList<>();
    private static volatile Object currentTarget;
    private static boolean frameActive;
    private static boolean frameHasTarget;

    private QuestHoverRegistry() {}

    /** Registers a listener for changes to the currently hovered target. */
    public static void register(IQuestHoverListener listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    public static void beginFrame() {
        frameActive = true;
        frameHasTarget = false;
    }

    public static void endFrame() {
        if (frameActive && !frameHasTarget) clearCurrentTarget();
        frameActive = false;
    }

    /** Publishes a raw target as currently hovered by the quest UI. */
    public static void setCurrentTarget(Object target) {
        Objects.requireNonNull(target, "target");
        frameHasTarget |= frameActive;

        Object previousTarget = currentTarget;
        if (target == previousTarget) return;
        if (target.equals(previousTarget)) {
            currentTarget = target;
            return;
        }

        currentTarget = target;
        notifyListeners(target);
    }

    /** Clears the hover state only when it belongs to the specified target. */
    public static void clearCurrentTarget(Object target) {
        Objects.requireNonNull(target, "target");

        Object previousTarget = currentTarget;
        if (target != previousTarget) return;
        currentTarget = null;

        notifyListeners(null);
    }

    public static void clearCurrentTarget() {
        if (currentTarget == null) return;
        currentTarget = null;
        notifyListeners(null);
    }

    private static void notifyListeners(@Nullable Object target) {
        for (IQuestHoverListener listener : listeners) {
            listener.onQuestHoverChanged(target);
        }
    }
}
