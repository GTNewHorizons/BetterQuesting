package betterquesting.api2.utils;

public final class TextFormattingUtils {

    private TextFormattingUtils() {}

    /**
     * Removes & and § formatting codes, including hex colors and gradients.
     */
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '&' && i + 1 < len) {
                char next = text.charAt(i + 1);
                char nextL = Character.toLowerCase(next);
                // &g&#RRGGBB&#RRGGBB (18 chars)
                if (nextL == 'g' && i + 18 <= len
                    && text.charAt(i + 2) == '&'
                    && text.charAt(i + 3) == '#'
                    && isHex6(text, i + 4)
                    && text.charAt(i + 10) == '&'
                    && text.charAt(i + 11) == '#'
                    && isHex6(text, i + 12)) {
                    i += 17;
                    continue;
                }
                // &#RRGGBB (8 chars)
                if (next == '#' && isHex6(text, i + 2)) {
                    i += 7;
                    continue;
                }
                // &X single codes
                if ((nextL >= '0' && nextL <= '9') || (nextL >= 'a' && nextL <= 'f')
                    || (nextL >= 'k' && nextL <= 'o')
                    || nextL == 'r'
                    || nextL == 'x'
                    || nextL == 'q'
                    || nextL == 'z'
                    || nextL == 'v'
                    || nextL == 'g') {
                    i += 1;
                    continue;
                }
                // Literal &
                sb.append(ch);
            } else if (ch == '§' && i + 1 < len) {
                char next = text.charAt(i + 1);
                char nextL = Character.toLowerCase(next);
                // §g + two §x sequences (30 chars)
                if (nextL == 'g' && i + 30 <= len
                    && isSectionHexColor(text, i + 2)
                    && isSectionHexColor(text, i + 16)) {
                    i += 29;
                    continue;
                }
                // §x§R§R§G§G§B§B (14 chars)
                if (nextL == 'x' && isSectionHexColor(text, i)) {
                    i += 13;
                    continue;
                }
                // §X (2 chars) — any known format code
                if ((nextL >= '0' && nextL <= '9') || (nextL >= 'a' && nextL <= 'f')
                    || (nextL >= 'k' && nextL <= 'o')
                    || nextL == 'r'
                    || nextL == 'x'
                    || nextL == 'q'
                    || nextL == 'z'
                    || nextL == 'v'
                    || nextL == 'g') {
                    i += 1;
                    continue;
                }
                // Unknown §, pass through
                sb.append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean isSectionHexColor(String text, int start) {
        if (start + 14 > text.length()) return false;
        if (text.charAt(start) != '§' || Character.toLowerCase(text.charAt(start + 1)) != 'x') return false;
        for (int k = 0; k < 6; k++) {
            int pos = start + 2 + k * 2;
            char c = text.charAt(pos + 1);
            if (text.charAt(pos) != '§'
                || !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static boolean isHex6(String text, int start) {
        if (start + 6 > text.length()) return false;
        for (int k = 0; k < 6; k++) {
            char c = text.charAt(start + k);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}
