package betterquesting.api2.client.gui.editors;

import java.util.Objects;

import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.popups.PopColorInput;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.GuiTextEditor;

public class TextEditorActionContext {

    private final GuiTextEditor editor;

    public TextEditorActionContext(GuiTextEditor editor) {
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    public String getSelectedText() {
        return editor.getSelectedText();
    }

    public void replaceSelectedText(String text) {
        editor.replaceSelectedText(Objects.requireNonNull(text, "text"));
    }

    public void applyFormatting(String formatting) {
        applyFormatting(Objects.requireNonNull(formatting, "formatting"), getSelectedText());
    }

    public void clearFormatting() {
        String selectedText = getSelectedText();
        replaceSelectedText(selectedText.isEmpty() ? "\u00a7r" : GuiTextEditor.stripFormatting(selectedText));
    }

    public void openPopup(IGuiPanel panel) {
        editor.openPopup(Objects.requireNonNull(panel, "panel"));
    }

    public void openColorPicker(String titleKey, boolean gradient) {
        String selectedText = getSelectedText();
        openPopup(
            new PopColorInput(
                QuestTranslation.translate(titleKey),
                gradient,
                formatting -> applyFormatting(formatting, selectedText)));
    }

    private void applyFormatting(String formatting, String selectedText) {
        replaceSelectedText(selectedText.isEmpty() ? formatting : formatting + selectedText + "\u00a7r");
    }
}
