package betterquesting.questing.sync;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class QuestChangeSet {

    private final HashMap<UUID, HashSet<UUID>> dirtyQuestsByPlayer = new HashMap<>();

    public QuestChangeSet markQuestDirty(@Nullable UUID playerID, @Nullable UUID questID) {
        if (playerID == null || questID == null) {
            return this;
        }

        dirtyQuestsByPlayer.computeIfAbsent(playerID, ignored -> new HashSet<>())
                .add(questID);

        return this;
    }

    public QuestChangeSet markQuestDirty(@Nonnull Collection<UUID> playerIDs, @Nullable UUID questID) {
        for (UUID playerID : playerIDs) {
            markQuestDirty(playerID, questID);
        }

        return this;
    }

    public QuestChangeSet merge(@Nullable QuestChangeSet other) {
        if (other == null) {
            return this;
        }

        for (Map.Entry<UUID, Set<UUID>> entry : other.getDirtyQuestsByPlayer().entrySet()) {
            for (UUID questID : entry.getValue()) {
                markQuestDirty(entry.getKey(), questID);
            }
        }

        return this;
    }

    public boolean isEmpty() {
        return dirtyQuestsByPlayer.isEmpty();
    }

    @Nonnull
    public Map<UUID, Set<UUID>> getDirtyQuestsByPlayer() {
        HashMap<UUID, Set<UUID>> copy = new HashMap<>();

        for (Map.Entry<UUID, HashSet<UUID>> entry : dirtyQuestsByPlayer.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }

        return Collections.unmodifiableMap(copy);
    }
}
