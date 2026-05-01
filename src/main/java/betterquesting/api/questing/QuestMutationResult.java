package betterquesting.api.questing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Describes the consequences of quest state mutations.
 * <p>
 * The dirty quest map is the source of truth for client synchronization:
 * each key is a player whose client may need quest progress/cache updates,
 * each value is the set of quests whose progress changed for that player.
 */
public final class QuestMutationResult {

    private final HashMap<UUID, HashSet<UUID>> dirtyQuestsByPlayer = new HashMap<>();
    private final ArrayList<UUID> completedQuests = new ArrayList<>();
    private final ArrayList<UUID> resetQuests = new ArrayList<>();

    public QuestMutationResult markDirty(@Nonnull UUID playerID, @Nonnull UUID questID) {
        dirtyQuestsByPlayer.computeIfAbsent(playerID, ignored -> new HashSet<>())
            .add(questID);

        return this;
    }

    public QuestMutationResult markDirty(@Nonnull Iterable<UUID> playerIDs, @Nonnull UUID questID) {
        for (UUID playerID : playerIDs) {
            markDirty(playerID, questID);
        }

        return this;
    }

    public QuestMutationResult markCompleted(@Nonnull UUID playerID, @Nonnull UUID questID) {
        completedQuests.add(questID);
        markDirty(playerID, questID);

        return this;
    }

    public QuestMutationResult markReset(@Nonnull UUID playerID, @Nonnull UUID questID) {
        resetQuests.add(questID);
        markDirty(playerID, questID);

        return this;
    }

    public QuestMutationResult merge(@Nonnull QuestMutationResult other) {
        for (Map.Entry<UUID, Set<UUID>> entry : other.getDirtyQuestsByPlayer()
            .entrySet()) {
            for (UUID questID : entry.getValue()) {
                markDirty(entry.getKey(), questID);
            }
        }

        completedQuests.addAll(other.getCompletedQuests());
        resetQuests.addAll(other.getResetQuests());

        return this;
    }

    public boolean hasChanges() {
        return !dirtyQuestsByPlayer.isEmpty();
    }

    public boolean affectsPlayer(@Nonnull UUID playerID) {
        return dirtyQuestsByPlayer.containsKey(playerID);
    }

    @Nonnull
    public Map<UUID, Set<UUID>> getDirtyQuestsByPlayer() {
        HashMap<UUID, Set<UUID>> copy = new HashMap<>();

        for (Map.Entry<UUID, HashSet<UUID>> entry : dirtyQuestsByPlayer.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }

        return Collections.unmodifiableMap(copy);
    }

    @Nonnull
    public Set<UUID> getAffectedPlayers() {
        return Collections.unmodifiableSet(new HashSet<>(dirtyQuestsByPlayer.keySet()));
    }

    @Nonnull
    public Set<UUID> getChangedQuests() {
        HashSet<UUID> changedQuests = new HashSet<>();

        for (HashSet<UUID> quests : dirtyQuestsByPlayer.values()) {
            changedQuests.addAll(quests);
        }

        return Collections.unmodifiableSet(changedQuests);
    }

    @Nonnull
    public List<UUID> getCompletedQuests() {
        return Collections.unmodifiableList(completedQuests);
    }

    @Nonnull
    public List<UUID> getResetQuests() {
        return Collections.unmodifiableList(resetQuests);
    }
}
