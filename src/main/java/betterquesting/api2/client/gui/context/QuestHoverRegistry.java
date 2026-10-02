package betterquesting.api2.client.gui.context;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import betterquesting.api.api.QuestingAPI;

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

    /** Unregisters a listener and returns whether it was present. */
    public static boolean unregister(IQuestHoverListener listener) {
        return listeners.remove(Objects.requireNonNull(listener, "listener"));
    }

    public static void beginFrame() {
        frameActive = true;
        frameHasTarget = false;
    }

    public static void endFrame() {
        if (frameActive && !frameHasTarget) clearCurrentTarget();
        frameActive = false;
    }

    /** Offers the topmost raw target discovered during the current GUI frame. */
    public static void offerCurrentTarget(Object target) {
        Objects.requireNonNull(target, "target");
        if (frameActive) {
            if (frameHasTarget) return;
            frameHasTarget = true;
        }

        Object previousTarget = currentTarget;
        if (target == previousTarget) return;
        if (target.equals(previousTarget)) {
            currentTarget = target;
            return;
        }

        currentTarget = target;
        notifyListeners(target);
    }

    /** Publishes a raw target for integrations outside the standard frame traversal. */
    public static void setCurrentTarget(Object target) {
        offerCurrentTarget(target);
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
            try {
                listener.onQuestHoverChanged(target);
            } catch (Throwable throwable) {
                QuestingAPI.getLogger()
                    .error("Quest hover listener failed", throwable);
            }
        }
    }
}
