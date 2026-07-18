package betterquesting.api2.client.gui.editors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.util.ResourceLocation;

public class TextEditorActionRegistry {

    private static final Map<ResourceLocation, TextEditorAction> ACTIONS = new LinkedHashMap<>();

    static {
        register(
            new ResourceLocation("betterquesting:hyperlink"),
            new TextEditorMacro("betterquesting.editor.macro.hyperlink", "[url]", "[/url]"));
        register(
            new ResourceLocation("betterquesting:warning"),
            new TextEditorMacro("betterquesting.editor.macro.warning", "[warn]", "[/warn]"));
        register(
            new ResourceLocation("betterquesting:note"),
            new TextEditorMacro("betterquesting.editor.macro.note", "[note]", "[/note]"));
        register(
            new ResourceLocation("betterquesting:quest_title"),
            new TextEditorMacro("betterquesting.editor.macro.quest_title", "[quest]", "[/quest]"));
        register(
            new ResourceLocation("betterquesting:hex_color"),
            new TextEditorAction(
                "betterquesting.editor.action.hex_color",
                context -> context.openColorPicker("betterquesting.editor.color_picker.hex", false)));
        register(
            new ResourceLocation("betterquesting:rainbow"),
            new TextEditorAction("betterquesting.editor.action.rainbow", context -> context.applyFormatting("&q")));
        register(
            new ResourceLocation("betterquesting:wave"),
            new TextEditorAction("betterquesting.editor.action.wave", context -> context.applyFormatting("&z")));
        register(
            new ResourceLocation("betterquesting:flip"),
            new TextEditorAction("betterquesting.editor.action.flip", context -> context.applyFormatting("&v")));
        register(
            new ResourceLocation("betterquesting:gradient"),
            new TextEditorAction(
                "betterquesting.editor.action.gradient",
                context -> context.openColorPicker("betterquesting.editor.color_picker.gradient", true)));
        register(
            new ResourceLocation("betterquesting:clear_format"),
            new TextEditorAction(
                "betterquesting.editor.action.clear_format",
                TextEditorActionContext::clearFormatting));
    }

    private TextEditorActionRegistry() {}

    public static synchronized void register(ResourceLocation id, TextEditorAction action) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        if (ACTIONS.containsKey(id)) {
            throw new IllegalArgumentException("Text editor action is already registered: " + id);
        }
        ACTIONS.put(id, action);
    }

    public static synchronized List<TextEditorAction> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(ACTIONS.values()));
    }
}
