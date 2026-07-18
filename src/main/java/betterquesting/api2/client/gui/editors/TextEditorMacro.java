package betterquesting.api2.client.gui.editors;

import java.util.Objects;

public class TextEditorMacro extends TextEditorAction {

    private final String openingText;
    private final String closingText;

    public TextEditorMacro(String translationKey, String openingText, String closingText) {
        super(
            translationKey,
            context -> context.replaceSelectedText(openingText + context.getSelectedText() + closingText));
        this.openingText = Objects.requireNonNull(openingText, "openingText");
        this.closingText = Objects.requireNonNull(closingText, "closingText");
    }

    public String wrap(String text) {
        return openingText + text + closingText;
    }
}
