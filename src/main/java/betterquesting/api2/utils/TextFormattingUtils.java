package betterquesting.api2.utils;

public class TextFormattingUtils {

    private TextFormattingUtils() {}

    /**
     * Removes & and § formatting codes, including hex colors and gradients.
     */
    public static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '&') {
                sb.append('&');
                i += 2;
            } else {
                int tokenLength = getFormattingTokenLength(text, i);
                if (tokenLength == 0) tokenLength = getStrippableFormattingTokenLength(text, i);
                if (tokenLength > 0) i += tokenLength;
                else {
                    sb.append(ch);
                    i++;
                }
            }
        }
        return sb.toString();
    }

    /** Returns the length of the formatting token at {@code pos}, or zero for literal text. */
    public static int getFormattingTokenLength(String text, int pos) {
        if (text == null || pos < 0 || pos >= text.length()) return 0;
        char marker = text.charAt(pos);
        if (marker == '\u00a7' && pos + 1 < text.length()) {
            char code = text.charAt(pos + 1);
            char lowerCode = Character.toLowerCase(code);
            if (lowerCode == 'g' && pos + 30 <= text.length()
                && isValidSectionX(text, pos + 2)
                && isValidSectionX(text, pos + 16)) return 30;
            if (lowerCode == 'x' && isValidSectionX(text, pos)) return 14;
            return isSingleCode(code) ? 2 : 0;
        }
        if (marker != '&' || pos + 1 >= text.length()) return 0;
        char code = text.charAt(pos + 1);
        char lowerCode = Character.toLowerCase(code);
        if (lowerCode == 'g' && pos + 18 <= text.length()
            && text.charAt(pos + 2) == '&'
            && text.charAt(pos + 3) == '#'
            && isHex6(text, pos + 4)
            && text.charAt(pos + 10) == '&'
            && text.charAt(pos + 11) == '#'
            && isHex6(text, pos + 12)) return 18;
        if (code == '#' && pos + 8 <= text.length() && isHex6(text, pos + 2)) return 8;
        return isSingleCode(code) ? 2 : 0;
    }

    /** Returns whether the token at {@code pos} resets the active color or text styles. */
    public static boolean resetsTextFormatting(String text, int pos) {
        int tokenLength = getFormattingTokenLength(text, pos);
        if (tokenLength == 0) return false;
        char marker = text.charAt(pos);
        char code = Character.toLowerCase(text.charAt(pos + 1));
        if (marker == '&' && (code == '#' || code == 'g')) return true;
        return code == 'r' || isFormatColor(text.charAt(pos + 1)) || code == 'x' || code == 'g';
    }

    private static boolean isSingleCode(char code) {
        char lowerCode = Character.toLowerCase(code);
        return isFormatColor(code) || lowerCode >= 'k' && lowerCode <= 'o'
            || lowerCode == 'r'
            || lowerCode == 'q'
            || lowerCode == 'z'
            || lowerCode == 'v';
    }

    private static boolean isStrippableSingleCode(char code) {
        char lowerCode = Character.toLowerCase(code);
        return isFormatColor(code) || lowerCode >= 'k' && lowerCode <= 'o' || lowerCode == 'r'
            || lowerCode == 'u' || lowerCode == 'x' || lowerCode == 'q' || lowerCode == 'z'
            || lowerCode == 'v' || lowerCode == 'g';
    }

    private static int getStrippableFormattingTokenLength(String text, int pos) {
        if (text == null || pos < 0 || pos >= text.length()) return 0;
        char marker = text.charAt(pos);
        if ((marker != '\u00a7' && marker != '&') || pos + 1 >= text.length()) return 0;
        return isStrippableSingleCode(text.charAt(pos + 1)) ? 2 : 0;
    }

    public static boolean isFormatColor(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'A' && code <= 'F';
    }

    /**
     * Validate that at position pos we have §x followed by 6 pairs of §+hex_digit (14 chars total).
     */
    public static boolean isValidSectionX(String text, int pos) {
        if (pos + 14 > text.length() || text.charAt(pos) != '\u00a7'
            || Character.toLowerCase(text.charAt(pos + 1)) != 'x') return false;
        for (int i = 0; i < 6; i++) {
            int codePos = pos + 2 + i * 2;
            if (text.charAt(codePos) != '\u00a7' || !isHexChar(text.charAt(codePos + 1))) return false;
        }
        return true;
    }

    private static boolean isHex6(String text, int start) {
        if (start + 6 > text.length()) return false;
        for (int i = 0; i < 6; i++) if (!isHexChar(text.charAt(start + i))) return false;
        return true;
    }

    private static boolean isHexChar(char code) {
        return code >= '0' && code <= '9' || code >= 'a' && code <= 'f' || code >= 'A' && code <= 'F';
    }

    /** Returns whether {@code code} is a supported ampersand formatting code. */
    private static boolean isValidAmpCode(char code) {
        char lowerCode = Character.toLowerCase(code);
        return isFormatColor(code) || lowerCode >= 'k' && lowerCode <= 'o'
            || lowerCode == 'r'
            || lowerCode == 'q'
            || lowerCode == 'z'
            || lowerCode == 'v';
    }

    /** Returns whether {@code format} starts with an ampersand RGB gradient. */
    public static boolean isAmpGradient(String format) {
        return format.length() >= 18 && format.charAt(0) == '&'
            && Character.toLowerCase(format.charAt(1)) == 'g'
            && format.charAt(2) == '&'
            && format.charAt(3) == '#';
    }

    /** Returns whether {@code format} starts with a section-sign RGB gradient. */
    public static boolean isSectionGradient(String format) {
        return format.length() >= 30 && format.charAt(0) == '\u00a7' && Character.toLowerCase(format.charAt(1)) == 'g';
    }

    /** Parses six hexadecimal digits at {@code offset}, or returns {@code -1} on failure. */
    private static int parseHex6(String text, int offset) {
        if (offset + 6 > text.length()) return -1;
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(text.charAt(offset + i), 16);
            if (digit == -1) return -1;
            value = (value << 4) | digit;
        }
        return value;
    }

    /** Interpolates two RGB colors without an alpha channel. */
    private static int lerpRgb(int from, int to, float blend) {
        int red = (int) (((from >> 16) & 0xFF) * (1 - blend) + ((to >> 16) & 0xFF) * blend);
        int green = (int) (((from >> 8) & 0xFF) * (1 - blend) + ((to >> 8) & 0xFF) * blend);
        int blue = (int) ((from & 0xFF) * (1 - blend) + (to & 0xFF) * blend);
        return (red << 16) | (green << 8) | blue;
    }

    /** Formats an RGB value as an ampersand hex color token. */
    private static String buildAmpHexColor(int rgb) {
        return String.format("&#%06X", rgb & 0xFFFFFF);
    }

    /** Counts visible characters until the active gradient is terminated. */
    private static int countVisibleCharsInGradient(String text, int startIndex) {
        int count = 0;
        for (int i = startIndex; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'r' || isFormatColor(text.charAt(i + 1)) || code == 'x' || code == 'q' || code == 'g')
                    break;
                i++;
            } else if (character == '&' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                char lowerCode = Character.toLowerCase(next);
                if (lowerCode == 'g' && getFormattingTokenLength(text, i) == 18) break;
                if (next == '#' && getFormattingTokenLength(text, i) == 8) break;
                if (lowerCode == 'q' || lowerCode == 'r' || isFormatColor(next)) break;
                if (isValidAmpCode(next)) i++;
                else count++;
            } else {
                count++;
            }
        }
        return count;
    }

    /** Expands ampersand gradients into per-character color tokens. */
    public static String expandAmpGradients(String text) {
        int gradientIndex = text.indexOf("&g&#");
        if (gradientIndex == -1) return text;
        StringBuilder result = new StringBuilder(text.length() + 128);
        int lastIndex = 0;
        while (gradientIndex != -1 && gradientIndex + 17 < text.length()) {
            if (getFormattingTokenLength(text, gradientIndex) != 18) {
                gradientIndex = text.indexOf("&g&#", gradientIndex + 2);
                continue;
            }
            int startRgb = parseHex6(text, gradientIndex + 4);
            int endRgb = parseHex6(text, gradientIndex + 12);
            int textStart = gradientIndex + 18;
            int totalVisible = countVisibleCharsInGradient(text, textStart);
            if (startRgb == -1 || endRgb == -1 || totalVisible <= 0) {
                gradientIndex = text.indexOf("&g&#", gradientIndex + 2);
                continue;
            }
            result.append(text, lastIndex, gradientIndex);
            int visibleIndex = 0;
            for (int i = textStart; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character == '&' && i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    char lowerCode = Character.toLowerCase(next);
                    if (lowerCode == 'g' && getFormattingTokenLength(text, i) == 18) {
                        lastIndex = i;
                        break;
                    }
                    if (next == '#' && getFormattingTokenLength(text, i) == 8) {
                        lastIndex = i;
                        break;
                    }
                    if (lowerCode == 'q' || lowerCode == 'r' || isFormatColor(next)) {
                        lastIndex = i;
                        break;
                    }
                    if (isValidAmpCode(next)) {
                        result.append(character)
                            .append(next);
                        i++;
                    } else {
                        float blend = totalVisible > 1 ? (float) visibleIndex / (totalVisible - 1) : 0F;
                        result.append(buildAmpHexColor(lerpRgb(startRgb, endRgb, Math.min(blend, 1F))))
                            .append(character);
                        visibleIndex++;
                    }
                } else if (character == '\u00a7' && i + 1 < text.length()) {
                    char code = Character.toLowerCase(text.charAt(i + 1));
                    if (code == 'r' || isFormatColor(text.charAt(i + 1)) || code == 'x' || code == 'q' || code == 'g') {
                        lastIndex = i;
                        break;
                    }
                    result.append(character)
                        .append(text.charAt(i + 1));
                    i++;
                } else {
                    float blend = totalVisible > 1 ? (float) visibleIndex / (totalVisible - 1) : 0F;
                    result.append(buildAmpHexColor(lerpRgb(startRgb, endRgb, Math.min(blend, 1F))))
                        .append(character);
                    visibleIndex++;
                    if (visibleIndex >= totalVisible) {
                        lastIndex = i + 1;
                        break;
                    }
                }
                lastIndex = i + 1;
            }
            gradientIndex = text.indexOf("&g&#", lastIndex);
        }
        if (lastIndex == 0) return text;
        result.append(text, lastIndex, text.length());
        return result.toString();
    }

    /** Creates the gradient token that continues across a wrapped line. */
    public static String continueGradient(String gradientFormat, String beforeWrap, String afterWrap) {
        int startRgb;
        int endRgb;
        int gradientPrefixLength;
        String styleSuffix;
        if (isAmpGradient(gradientFormat)) {
            startRgb = parseHex6(gradientFormat, 4);
            endRgb = parseHex6(gradientFormat, 12);
            gradientPrefixLength = 18;
            styleSuffix = gradientFormat.length() > 18 ? gradientFormat.substring(18) : "";
        } else if (isSectionGradient(gradientFormat)) {
            startRgb = parseRgbFromSectionX(gradientFormat, 2);
            endRgb = parseRgbFromSectionX(gradientFormat, 16);
            gradientPrefixLength = 30;
            styleSuffix = gradientFormat.length() > 30 ? gradientFormat.substring(30) : "";
        } else {
            return gradientFormat;
        }
        if (startRgb == -1 || endRgb == -1) return gradientFormat;
        int gradientPosition = isAmpGradient(gradientFormat) ? beforeWrap.lastIndexOf("&g&#")
            : beforeWrap.lastIndexOf("\u00a7g\u00a7");
        if (gradientPosition < 0) gradientPosition = 0;
        int visibleOnLine = countVisibleCharsInGradient(beforeWrap, gradientPosition + gradientPrefixLength);
        int visibleRemaining = countVisibleCharsInGradient(afterWrap, 0);
        int total = visibleOnLine + visibleRemaining;
        if (total <= 1) return buildAmpHexColor(endRgb) + styleSuffix;
        float blend = Math.min((float) visibleOnLine / (total - 1), 1F);
        int interpolatedRgb = lerpRgb(startRgb, endRgb, blend);
        return "&g" + buildAmpHexColor(interpolatedRgb) + buildAmpHexColor(endRgb) + styleSuffix;
    }

    /** Parses a section-sign RGB token at {@code offset}, or returns {@code -1} on failure. */
    private static int parseRgbFromSectionX(String text, int offset) {
        if (offset + 14 > text.length()) return -1;
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(text.charAt(offset + 3 + i * 2), 16);
            if (digit == -1) return -1;
            value = (value << 4) | digit;
        }
        return value;
    }

    /** Extracts active formatting tokens from a line for continuation on the next line. */
    public static String getFormatFromString(String text) {
        StringBuilder format = new StringBuilder();
        for (int i = 0; i < text.length();) {
            int tokenLength = getFormattingTokenLength(text, i);
            if (tokenLength <= 0) {
                i++;
                continue;
            }
            String token = text.substring(i, i + tokenLength);
            if (resetsTextFormatting(text, i)) format = new StringBuilder(token);
            else format.append(token);
            i += tokenLength;
        }
        if (format.length() == 0) return "";
        char last = format.charAt(format.length() - 1);
        return last == 'r' || last == 'R' ? "" : format.toString();
    }

}
