package betterquesting.questing.mutation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestMutationResult {

    private final HashMap<UUID, HashSet<UUID>> dirtyQuestsByPlayer = new HashMap<>();
    private final ArrayList<UUID> completedQuests = new ArrayList<>();
    private final ArrayList<UUID> resetQuests = new ArrayList<>();

    public QuestMutationResult markDirty(@Nullable UUID playerID, @Nullable UUID questID) {
        if (playerID == null || questID == null) {
            return this;
        }

        dirtyQuestsByPlayer.computeIfAbsent(playerID, ignored -> new HashSet<>())
            .add(questID);

        return this;
    }

    public QuestMutationResult markDirty(@Nonnull Iterable<UUID> playerIDs, @Nullable UUID questID) {
        for (UUID playerID : playerIDs) {
            markDirty(playerID, questID);
        }

        return this;
    }

    public QuestMutationResult markCompleted(@Nullable UUID playerID, @Nullable UUID questID) {
        if (playerID == null || questID == null) {
            return this;
        }

        completedQuests.add(questID);
        markDirty(playerID, questID);

        return this;
    }

    public QuestMutationResult markReset(@Nullable UUID playerID, @Nullable UUID questID) {
        if (playerID == null || questID == null) {
            return this;
        }

        resetQuests.add(questID);
        markDirty(playerID, questID);

        return this;
    }

    public QuestMutationResult merge(@Nullable QuestMutationResult other) {
        if (other == null) {
            return this;
        }

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

    public boolean affectsPlayer(@Nullable UUID playerID) {
        return playerID != null && dirtyQuestsByPlayer.containsKey(playerID);
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
