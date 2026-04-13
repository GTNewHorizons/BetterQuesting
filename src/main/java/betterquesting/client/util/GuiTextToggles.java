package betterquesting.client.util;

import java.util.regex.Pattern;

import betterquesting.api.storage.BQ_Settings;

public final class GuiTextToggles {

    private GuiTextToggles() {}

    // Strips all color and effect format codes: §x RGB, §g gradient, §y rainbow,
    // §w wave, §j dinnerbone, legacy §0-f, and their & equivalents.
    // Preserves styles (§k-o) and reset (§r).
    private static final Pattern COLOR_CODE_REMOVER = Pattern.compile(
        "§g§x(?:§[0-9a-fA-F]){6}§x(?:§[0-9a-fA-F]){6}" // §g gradient (30 chars)
            + "|§x(?:§[0-9a-fA-F]){6}" // §x RGB (14 chars)
            + "|§[0-9a-fA-Fxygwj]" // legacy colors, §x/§y/§g/§w/§j
            + "|&g&#[0-9a-fA-F]{6}&#[0-9a-fA-F]{6}" // &g gradient (18 chars)
            + "|&#[0-9a-fA-F]{6}" // &#RRGGBB (8 chars)
            + "|&[0-9a-fA-Fygwj]"); // legacy & colors, &y/&g/&w/&j

    // Strips BQ formatting tags that add color. URL is excluded so links stay clickable;
    // the §1/§9 color that [url] inserts is already handled by COLOR_CODE_REMOVER.
    private static final Pattern BQ_TAG_REMOVER = Pattern.compile("\\[(?:warn|note|quest)]|\\[/(?:warn|note|quest)]");

    public static String applyMonochromeIfEnabled(String s) {
        if (s == null) return null;

        if (!BQ_Settings.forceMonochromeText) return s;

        s = BQ_TAG_REMOVER.matcher(s)
            .replaceAll("");

        return COLOR_CODE_REMOVER.matcher(s)
            .replaceAll("");
    }
}
