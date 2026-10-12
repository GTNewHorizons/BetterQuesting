package betterquesting.api.questing.tasks;

/**
 * One line of task progress shown on the quest tracker HUD, e.g. "15/26 Sand".
 * A task with several requirements reports one line per requirement, so each can be
 * marked done on its own.
 */
public class TaskProgressLine {

    public final String text;
    public final boolean complete;

    public TaskProgressLine(String text, boolean complete) {
        this.text = text;
        this.complete = complete;
    }
}
