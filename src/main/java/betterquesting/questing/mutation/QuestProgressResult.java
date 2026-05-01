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
    private final List<UUID> resetQuests = new ArrayList<>();
    private final Set<UUID> affectedPlayers = new HashSet<>();

    public QuestChangeSet getChanges() {
        return changes;
    }

    public List<UUID> getCompletedQuests() {
        return Collections.unmodifiableList(completedQuests);
    }

    public Set<UUID> getChangedQuests() {
        return Collections.unmodifiableSet(changedQuests);
    }

    public List<UUID> getResetQuests() {
        return Collections.unmodifiableList(resetQuests);
    }

    public Set<UUID> getAffectedPlayers() {
        return Collections.unmodifiableSet(affectedPlayers);
    }

    public void markChanged(UUID playerID, UUID questID) {
        if (playerID == null || questID == null) return;

        affectedPlayers.add(playerID);
        changedQuests.add(questID);
        changes.markQuestDirty(playerID, questID);
    }

    public void markChanged(UUID questID, QuestChangeSet changeSet) {
        if (questID == null || changeSet == null || changeSet.isEmpty()) return;

        changedQuests.add(questID);
        changes.merge(changeSet);
        affectedPlayers.addAll(
            changeSet.getDirtyQuestsByPlayer()
                .keySet());
    }

    public void markCompleted(UUID playerID, UUID questID) {
        if (playerID == null || questID == null) return;
        completedQuests.add(questID);
        markChanged(playerID, questID);
    }

    public void markReset(UUID playerID, UUID questID) {
        if (playerID == null || questID == null) return;
        resetQuests.add(questID);
        markChanged(playerID, questID);
    }

    public QuestProgressResult merge(QuestProgressResult other) {
        if (other == null) return this;

        this.changes.merge(other.getChanges());
        this.completedQuests.addAll(other.getCompletedQuests());
        this.resetQuests.addAll(other.getResetQuests());
        this.changedQuests.addAll(other.getChangedQuests());
        this.affectedPlayers.addAll(other.getAffectedPlayers());

        return this;
    }
}
