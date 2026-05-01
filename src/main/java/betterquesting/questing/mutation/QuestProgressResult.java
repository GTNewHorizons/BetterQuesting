package betterquesting.questing.mutation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import betterquesting.questing.sync.QuestChangeSet;

public final class QuestProgressResult {

    private final QuestChangeSet changes = new QuestChangeSet();
    private final List<UUID> completedQuests = new ArrayList<>();
    private final Set<UUID> changedQuests = new HashSet<>();

    public QuestChangeSet getChanges() {
        return changes;
    }

    public List<UUID> getCompletedQuests() {
        return Collections.unmodifiableList(completedQuests);
    }

    public Set<UUID> getChangedQuests() {
        return Collections.unmodifiableSet(changedQuests);
    }

    public void markChanged(UUID playerID, UUID questID) {
        if (playerID == null || questID == null) return;
        changedQuests.add(questID);
        changes.markQuestDirty(playerID, questID);
    }

    public void markCompleted(UUID playerID, UUID questID) {
        if (playerID == null || questID == null) return;
        completedQuests.add(questID);
        markChanged(playerID, questID);
    }
}
