package betterquesting.client.util;

import java.util.regex.Pattern;

import betterquesting.api.storage.BQ_Settings;

public final class GuiTextToggles {

    private GuiTextToggles() {}

    private static final Pattern COLOR_CODE_REMOVER = Pattern.compile("§[0-9a-fA-F]");

    private static final Pattern BQ_TAG_REMOVER = Pattern
        .compile("\\[(?:url|warn|note|quest)]|\\[/(?:url|warn|note|quest)]");

    public static String applyMonochromeIfEnabled(String s) {
        if (s == null) return null;

        s = BQ_TAG_REMOVER.matcher(s)
            .replaceAll("");

        if (!BQ_Settings.forceMonochromeText) return s;

        return COLOR_CODE_REMOVER.matcher(s)
            .replaceAll("");
    }
}
