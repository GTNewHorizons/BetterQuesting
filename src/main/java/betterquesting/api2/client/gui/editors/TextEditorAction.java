package betterquesting.api2.client.gui.editors;

import java.util.Objects;
import java.util.function.Consumer;

public class TextEditorAction {

    private final String translationKey;
    private final Consumer<TextEditorActionContext> executor;

    public TextEditorAction(String translationKey, Consumer<TextEditorActionContext> executor) {
        this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public void execute(TextEditorActionContext context) {
        executor.accept(Objects.requireNonNull(context, "context"));
    }
}
