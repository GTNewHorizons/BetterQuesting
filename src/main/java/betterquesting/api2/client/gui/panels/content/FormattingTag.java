package betterquesting.api2.client.gui.panels.content;

import betterquesting.api.storage.BQ_Settings;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public enum FormattingTag {
    NOTE("note"),
    WARNING("warn"),
    QUEST("quest"),

    BOLD("bold"),
    ITALIC("italic"),
    UNDERLINE("underline"),
    STRIKETHROUGH("strikethrough"),
    OBFUSCATED("obfuscated"),

    /**
     * Defines a clickable hyperlink.
     *
     * <p>Params:
     * <ul>
     *     <li>{@code link}: If provided, sets the hyperlink target. If not provided, then the
     *     <em>exact</em> text contents within the {@code [url]} tags will be the hyperlink target.</li>
     * </ul>
     *
     * <p>Example usage:
     * <ul>
     *     <li>{@code [url]https://www.example.com[/url]}</li>
     *     <li>{@code [url link=https://www.example.com]Click me![/url]}</li>
     * </ul>
     *
     * <p>URL tags cannot be nested, and will break if you try.
     */
    URL("url"),
    ;

    public static final ImmutableMap<String, FormattingTag> NAME_TO_VALUE_MAP =
            ImmutableMap.copyOf(
                    Arrays.stream(values())
                            .collect(
                                    Collectors.toMap(FormattingTag::getName, Function.identity())));

    private static final Pattern OPENING_TAG_PATTERN =
            Pattern.compile("\\[([0-9a-zA-Z]+)((?: [0-9a-zA-Z]+=[^ ]+)*)]");
    private static final Pattern OPENING_TAG_PARAMS_PATTERN =
            Pattern.compile(" ([0-9a-zA-Z]+)=([^ ]+)");
    private static final Pattern CLOSING_TAG_PATTERN = Pattern.compile("\\[/([0-9a-zA-Z]+)]");

    private static final String BQ_DARK_THEME = "betterquesting:dark";
    private static final String BQ_ENDER_THEME = "betterquesting:ender";
    private static final String BQ_OVERWORLD_THEME = "betterquesting:overworld";
    private static final String BQ_STRONGHOLD_THEME = "betterquesting:stronghold";

    private static final ImmutableMap<FormattingTag, String> DEFAULT_FORMATTING_STRING_MAP;
    private static final ImmutableTable<FormattingTag, String, String> THEME_FORMATTING_STRING_TABLE;
    private static final ImmutableMap<FormattingTag, String> TEXT_FORMATTING_STRING_MAP;

    static {
        ImmutableMap.Builder<FormattingTag, String> defaultFormattingStringMapBuilder =
                ImmutableMap.builder();
        ImmutableTable.Builder<FormattingTag, String, String> themeFormattingStringTableBuilder =
                ImmutableTable.builder();
        ImmutableMap.Builder<FormattingTag, String> textFormattingStringMapBuilder =
                ImmutableMap.builder();

        defaultFormattingStringMapBuilder.put(NOTE, "§3");
        themeFormattingStringTableBuilder.put(NOTE, BQ_ENDER_THEME, "§b");
        themeFormattingStringTableBuilder.put(NOTE, BQ_OVERWORLD_THEME, "§b");
        themeFormattingStringTableBuilder.put(NOTE, BQ_STRONGHOLD_THEME, "§b");

        defaultFormattingStringMapBuilder.put(WARNING, "§4");
        themeFormattingStringTableBuilder.put(WARNING, BQ_DARK_THEME, "§c");
        themeFormattingStringTableBuilder.put(WARNING, BQ_OVERWORLD_THEME, "§c");
        themeFormattingStringTableBuilder.put(WARNING, BQ_STRONGHOLD_THEME, "§c");

        defaultFormattingStringMapBuilder.put(QUEST, "§2");
        themeFormattingStringTableBuilder.put(QUEST, BQ_DARK_THEME, "§a");
        themeFormattingStringTableBuilder.put(QUEST, BQ_OVERWORLD_THEME, "§a");
        themeFormattingStringTableBuilder.put(QUEST, BQ_STRONGHOLD_THEME, "§a");

        defaultFormattingStringMapBuilder.put(URL, "§1");
        themeFormattingStringTableBuilder.put(URL, BQ_DARK_THEME, "§9");
        themeFormattingStringTableBuilder.put(URL, BQ_OVERWORLD_THEME, "§9");
        themeFormattingStringTableBuilder.put(URL, BQ_STRONGHOLD_THEME, "§9");

        textFormattingStringMapBuilder.put(BOLD, "§l");
        textFormattingStringMapBuilder.put(ITALIC, "§o");
        textFormattingStringMapBuilder.put(UNDERLINE, "§n");
        textFormattingStringMapBuilder.put(STRIKETHROUGH, "§m");
        textFormattingStringMapBuilder.put(OBFUSCATED, "§k");

        textFormattingStringMapBuilder.put(QUEST, "§n");
        textFormattingStringMapBuilder.put(URL, "§n");

        DEFAULT_FORMATTING_STRING_MAP = defaultFormattingStringMapBuilder.build();
        THEME_FORMATTING_STRING_TABLE = themeFormattingStringTableBuilder.build();
        TEXT_FORMATTING_STRING_MAP = textFormattingStringMapBuilder.build();
    }

    private final String name;

    FormattingTag(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getColourFormattingString() {
        return Objects.firstNonNull(
                THEME_FORMATTING_STRING_TABLE.get(this, BQ_Settings.curTheme),
                DEFAULT_FORMATTING_STRING_MAP.getOrDefault(this, ""));
    }

    /**
     * Unfortunately, in Minecraft 1.7, text formatting codes (bold, italic, etc.) must be
     * re-applied after colour formatting codes.
     *
     * <p>Having this separate method allows us to handle re-applying these text formatting codes
     * where needed.
     */
    public String getTextFormattingString() {
        return TEXT_FORMATTING_STRING_MAP.getOrDefault(this, "");
    }

    public static Optional<TagInstance> parseOpeningTag(String text) {
        Matcher matcher = OPENING_TAG_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        FormattingTag tag = NAME_TO_VALUE_MAP.get(matcher.group(1));
        if (tag == null) {
            return Optional.empty();
        }

        ImmutableMap.Builder<String, String> paramsBuilder = ImmutableMap.builder();
        String params = matcher.group(2);
        if (!params.isEmpty()) {
            matcher = OPENING_TAG_PARAMS_PATTERN.matcher(params);
            while (matcher.find()) {
                paramsBuilder.put(matcher.group(1), matcher.group(2));
            }
        }

        return Optional.of(new TagInstance(tag, paramsBuilder.build()));
    }

    public static Optional<FormattingTag> parseClosingTag(String text) {
        Matcher matcher = CLOSING_TAG_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        return Optional.ofNullable(NAME_TO_VALUE_MAP.get(matcher.group(1)));
    }

    /**
     * Helper object which represents an instance of an opening formatting tag, complete with
     * optional parameters.
     *
     * <p>Parameters are specified using the form {@code paramName=value}. The parameter value is
     * not allowed to contain spaces or square brackets.
     *
     * <p>The reason why parameter values cannot contain square brackets, is because they would
     * interfere with the string tokenization logic in {@link PanelTextBox}.
     */
    public static class TagInstance {
        private final FormattingTag tag;
        private final ImmutableMap<String, String> params;

        private TagInstance(FormattingTag tag, ImmutableMap<String, String> params) {
            this.tag = tag;
            this.params = params;
        }

        public FormattingTag getTag() {
            return tag;
        }

        public ImmutableMap<String, String> getParams() {
            return params;
        }
    }
}
