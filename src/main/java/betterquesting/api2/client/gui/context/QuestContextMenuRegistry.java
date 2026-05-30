package betterquesting.api2.client.gui.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Static registry for external context menu entries added to BQ quest popups. */
public final class QuestContextMenuRegistry {

	private static final List<IQuestContextMenuEntry> entries = Collections.synchronizedList(new ArrayList<>());

	private QuestContextMenuRegistry() {}

	/** Call during mod init to add a custom entry to every quest's right-click menu. */
	public static void register(IQuestContextMenuEntry entry) {
		if (entry == null) throw new IllegalArgumentException("entry cannot be null");
		entries.add(entry);
	}

	/** Returns a read-only snapshot of all registered entries. */
	public static List<IQuestContextMenuEntry> getEntries() {
		return new ArrayList<>(entries);
	}
}
