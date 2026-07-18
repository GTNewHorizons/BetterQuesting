package betterquesting.client.gui2.editors;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import betterquesting.api.utils.RenderUtils;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.panels.content.FormattingTag;

public class TextEditorSyntaxHighlighter implements Function<String, PanelTextField.TextDisplayText> {

    private static final String RESET = "\u00a7r";
    private static final String TAG_COLOR = "\u00a7c";
    private static final Pattern TAG_CANDIDATE = Pattern.compile("\\[[^\\]\\r\\n]*]");
    private static final Pattern IMAGE_OPENING_TAG = Pattern.compile("\\[img height=[1-9]\\d*]");
    private static final String IMAGE_CLOSING_TAG = "[/img]";

    @Override
    public PanelTextField.TextDisplayText apply(String text) {
        int[] sourceToDisplay = new int[text.length() + 1];
        StringBuilder renderedText = new StringBuilder(text.length() + 32);
        Matcher matcher = TAG_CANDIDATE.matcher(text);
        String activeFormatting = "";
        int sourceIndex = 0;

        while (matcher.find()) {
            if (!isSupportedTag(matcher.group())) {
                continue;
            }

            activeFormatting = appendSource(
                text,
                sourceIndex,
                matcher.start(),
                renderedText,
                sourceToDisplay,
                activeFormatting);

            renderedText.append(TAG_COLOR);
            appendRaw(text, matcher.start(), matcher.end(), renderedText, sourceToDisplay);
            renderedText.append(RESET)
                .append(activeFormatting);
            sourceToDisplay[matcher.end()] = renderedText.length();
            sourceIndex = matcher.end();
        }

        appendSource(text, sourceIndex, text.length(), renderedText, sourceToDisplay, activeFormatting);
        return new PanelTextField.TextDisplayText(renderedText.toString(), sourceToDisplay);
    }

    private static boolean isSupportedTag(String tag) {
        return FormattingTag.parseOpeningTag(tag)
            .isPresent()
            || FormattingTag.parseClosingTag(tag)
                .isPresent()
            || IMAGE_OPENING_TAG.matcher(tag)
                .matches()
            || IMAGE_CLOSING_TAG.equals(tag);
    }

    private static String appendSource(String text, int start, int end, StringBuilder renderedText,
        int[] sourceToDisplay, String activeFormatting) {
        int index = start;
        while (index < end) {
            String formatting = getAmpersandFormatting(text, index, end);
            if (formatting != null) {
                renderedText.append(formatting);
                int formattingLength = formatting.startsWith("\u00a7x") ? 8 : 2;
                appendRaw(text, index, index + formattingLength, renderedText, sourceToDisplay);
                activeFormatting = RenderUtils.getFormatFromString(activeFormatting + formatting);
                index += formattingLength;
                continue;
            }

            int sectionFormattingLength = getSectionFormattingLength(text, index, end);
            if (sectionFormattingLength > 0) {
                appendRaw(text, index, index + sectionFormattingLength, renderedText, sourceToDisplay);
                activeFormatting = RenderUtils
                    .getFormatFromString(activeFormatting + text.substring(index, index + sectionFormattingLength));
                index += sectionFormattingLength;
                continue;
            }

            sourceToDisplay[index] = renderedText.length();
            renderedText.append(text.charAt(index));
            index++;
        }
        sourceToDisplay[end] = renderedText.length();
        return activeFormatting;
    }

    private static void appendRaw(String text, int start, int end, StringBuilder renderedText, int[] sourceToDisplay) {
        for (int index = start; index < end; index++) {
            sourceToDisplay[index] = renderedText.length();
            renderedText.append(text.charAt(index));
        }
        sourceToDisplay[end] = renderedText.length();
    }

    private static String getAmpersandFormatting(String text, int index, int end) {
        if (text.charAt(index) != '&' || index + 1 >= end) {
            return null;
        }

        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == '#' && index + 8 <= end && isHex(text, index + 2)) {
            return toSectionHex(text.substring(index + 2, index + 8));
        }
        if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
            || (code >= 'k' && code <= 'o')
            || code == 'r') {
            return "\u00a7" + code;
        }
        return null;
    }

    private static int getSectionFormattingLength(String text, int index, int end) {
        if (text.charAt(index) != '\u00a7' || index + 1 >= end) {
            return 0;
        }
        if (Character.toLowerCase(text.charAt(index + 1)) == 'x' && index + 14 <= end
            && RenderUtils.isValidSectionX(text, index)) {
            return 14;
        }
        return 2;
    }

    private static boolean isHex(String text, int start) {
        for (int index = start; index < start + 6; index++) {
            if (Character.digit(text.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String toSectionHex(String hex) {
        StringBuilder result = new StringBuilder("\u00a7x");
        for (int index = 0; index < hex.length(); index++) {
            result.append('\u00a7')
                .append(hex.charAt(index));
        }
        return result.toString();
    }
}
