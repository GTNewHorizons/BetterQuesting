package betterquesting.api2.client.gui.panels.content;

import static betterquesting.api.storage.BQ_Settings.textWidthCorrection;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;

import com.google.common.collect.ImmutableSet;

import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.RenderUtils;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.client.gui.context.QuestTooltipRegistry;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.misc.URIHandlers;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.colors.IGuiColor;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.QuestDatabase;

public class PanelTextBox implements IGuiPanel {

    /**
     * Tokenizer pattern which is used to tokenize raw text into literal text fragments,
     * (potential) formatting tags, and formatting codes.
     *
     * <p>
     * This is accomplished by matching empty strings which immediately precede or follow a
     * square bracket or formatting code.
     */
    private static final Pattern TOKEN_DELIMITER = Pattern.compile("(?=\\[)|(?=§.)|(?<=])|(?<=§.)");
    private static final Pattern COLOUR_FORMATTING_CODE_PATTERN = Pattern.compile("§[0-9a-fxgq]");
    private static final String FORMATTING_CODE_RESET = "§r";

    private static final String defaultUrlProtocol = "https";
    private static final Set<String> supportedUrlProtocol = ImmutableSet.of("http", "https");
    private static final String INTERACTION_SCHEME = "bqinteraction";
    private static final int QUEST_DESCRIPTION_TOOLTIP_WIDTH = 180;
    private static final int QUEST_DESCRIPTION_TOOLTIP_LINES = 2;
    private static final Map<ResourceLocation, Function<String, String>> textProcessors = new LinkedHashMap<>();
    private static final Map<ResourceLocation, TextInteraction> textInteractions = new LinkedHashMap<>();

    static {
        QuestTooltipRegistry.register((target, tooltip) -> {
            if (!(target instanceof UUID questId)) return;

            IQuest quest = QuestDatabase.INSTANCE.get(questId);
            if (quest == null) return;

            tooltip.add(QuestTranslation.translateQuestName(questId, quest));
            String description = QuestTranslation.translateQuestDescription(questId, quest);
            if (!StringUtils.isBlank(description)) {
                FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
                List<String> descriptionLines = RenderUtils
                    .splitString(description, QUEST_DESCRIPTION_TOOLTIP_WIDTH, fontRenderer);
                int lineCount = Math.min(QUEST_DESCRIPTION_TOOLTIP_LINES, descriptionLines.size());
                for (int i = 0; i < lineCount; i++) {
                    tooltip.add(EnumChatFormatting.GRAY + descriptionLines.get(i));
                }
                if (descriptionLines.size() > QUEST_DESCRIPTION_TOOLTIP_LINES) {
                    tooltip.add(EnumChatFormatting.GRAY + "...");
                }
            }
            tooltip
                .add(EnumChatFormatting.GRAY + QuestTranslation.translate("betterquesting.tooltip.quest_link.click"));
        });
    }

    private final GuiRectText transform;
    private final List<linkRange> linkRanges = new ArrayList<>();
    private final List<HotZone> hotZones = new ArrayList<>();
    private boolean enabled = true;

    private GuiQuest questGUI;
    private String text = "";
    private boolean shadow = false;
    private IGuiColor color = new GuiColorStatic(255, 255, 255, 255);
    private final boolean autoFit;
    private int align = 0;
    private int fontScale = 12;

    private int lines = 1; // Cached number of lines
    private boolean hyperlinkAware;

    public PanelTextBox(IGuiRect rect, String text) {
        this(rect, text, false);
    }

    public PanelTextBox(IGuiRect rect, String text, boolean autoFit) {
        this(rect, text, autoFit, false);
    }

    public PanelTextBox(IGuiRect rect, String text, boolean autoFit, boolean hyperlinkAware) {
        this.transform = new GuiRectText(rect, autoFit);
        this.autoFit = autoFit;
        this.hyperlinkAware = hyperlinkAware;
        this.setText(text);
    }

    public boolean isHyperlinkAware() {
        return hyperlinkAware;
    }

    public PanelTextBox setHyperlinkAware(boolean hyperlinkAware) {
        this.hyperlinkAware = hyperlinkAware;
        bakeHotZones(null);
        return this;
    }

    public PanelTextBox setText(String text) {
        text = processText(text);
        if (hyperlinkAware) {
            StringBuilder textBuilder = new StringBuilder();
            linkRanges.clear();

            // This variable should hold the start text position of the unique [url] tag currently
            // on the stack, or -1 if there is no [url] tag on the stack.
            // Behavior is undefined if there are multiple [url] tags on the stack; consumers of
            // this value should take care not to throw an exception even if this occurs.
            // Perhaps we will want to move this value into the [url] TagInstance itself, some day.
            int currLinkStart = -1;

            Deque<FormattingTag.TagInstance> tags = new ArrayDeque<>();
            Scanner scanner = new Scanner(text).useDelimiter(TOKEN_DELIMITER);
            while (scanner.hasNext()) {
                String token = scanner.next();
                if (token.equals(FORMATTING_CODE_RESET)) {
                    // Reset the formatting, then reapply all active tags
                    // in order of outermost to innermost (reverse of stack order).
                    textBuilder.append(FORMATTING_CODE_RESET);
                    tags.descendingIterator()
                        .forEachRemaining(
                            t -> textBuilder.append(
                                t.getTag()
                                    .getColourFormattingString()));
                    tags.descendingIterator()
                        .forEachRemaining(
                            t -> textBuilder.append(
                                t.getTag()
                                    .getTextFormattingString()));
                    continue;
                } else if (COLOUR_FORMATTING_CODE_PATTERN.matcher(token)
                    .matches()) {
                        textBuilder.append(token);
                        // Re-apply text formatting codes since we just changed the colour.
                        tags.descendingIterator()
                            .forEachRemaining(
                                t -> textBuilder.append(
                                    t.getTag()
                                        .getTextFormattingString()));
                        continue;
                    }

                Optional<FormattingTag.TagInstance> openingTagOptional = FormattingTag.parseOpeningTag(token);
                if (openingTagOptional.isPresent()) {
                    FormattingTag.TagInstance openingTag = openingTagOptional.get();
                    tags.push(openingTag);
                    textBuilder.append(
                        openingTag.getTag()
                            .getColourFormattingString());
                    // Re-apply text formatting codes since we may have just changed the colour.
                    tags.descendingIterator()
                        .forEachRemaining(
                            t -> textBuilder.append(
                                t.getTag()
                                    .getTextFormattingString()));

                    if (openingTag.getTag() == FormattingTag.URL || openingTag.getTag() == FormattingTag.QUESTLINK) {
                        currLinkStart = textBuilder.length();
                    }

                    continue;
                }

                Optional<FormattingTag> closingTagOptional = FormattingTag.parseClosingTag(token);
                if (closingTagOptional.isPresent()) {
                    FormattingTag closingTag = closingTagOptional.get();

                    if (!tags.isEmpty() && closingTag == tags.peek()
                        .getTag()) {
                        FormattingTag.TagInstance openingTag = tags.pop();
                        if (closingTag == FormattingTag.URL && currLinkStart >= 0) {
                            String url = openingTag.getParams()
                                .getOrDefault("link", textBuilder.substring(currLinkStart));
                            linkRanges.add(new linkRange(currLinkStart, textBuilder.length(), url));
                            currLinkStart = -1;
                        } else if (closingTag == FormattingTag.QUESTLINK && currLinkStart >= 0) {
                            String linkText = textBuilder.substring(currLinkStart);
                            String[] questNameID = linkText.split(" ", 2);

                            String displayText;
                            UUID questUUID = null;
                            try {
                                questUUID = UuidConverter.decodeUuid(questNameID[0]);

                                displayText = linkText.contains(" ") ? questNameID[1]
                                    : QuestTranslation
                                        .translateQuestName(questUUID, QuestDatabase.INSTANCE.get(questUUID));

                            } catch (Exception e) {
                                displayText = "§4§lQuest Not Found§4§l";
                            }

                            String formattedDisplayText = applyQuestLinkUnderline(displayText);
                            if (questUUID != null) {
                                linkRanges.add(
                                    new linkRange(
                                        currLinkStart,
                                        currLinkStart + formattedDisplayText.length(),
                                        questUUID));
                            }
                            textBuilder.replace(currLinkStart, textBuilder.length(), formattedDisplayText);

                            currLinkStart = -1;
                        }

                        // Reset the formatting, then reapply all active tags
                        // in order of outermost to innermost (reverse of stack order).
                        // Note that the tag we just closed was already popped off the stack.
                        textBuilder.append(FORMATTING_CODE_RESET);
                        tags.descendingIterator()
                            .forEachRemaining(
                                t -> textBuilder.append(
                                    t.getTag()
                                        .getColourFormattingString()));
                        tags.descendingIterator()
                            .forEachRemaining(
                                t -> textBuilder.append(
                                    t.getTag()
                                        .getTextFormattingString()));
                    } // Else the closing tag doesn't match the current tag, so ignore it.

                    continue;
                }

                textBuilder.append(token);
            }

            this.text = textBuilder.toString();
        } else {
            this.text = text;
        }

        IGuiRect bounds = this.getTransform();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        if (autoFit) {
            float scale = fontScale / 12F;
            List<String> sl = RenderUtils.splitStringWithoutFormat(
                this.text,
                (int) Math.floor(bounds.getWidth() / scale / textWidthCorrection),
                fr);
            lines = sl.size() - 1;

            this.transform.h = fr.FONT_HEIGHT * sl.size();

            bakeHotZones(sl);
        } else {
            lines = (bounds.getHeight() / fr.FONT_HEIGHT) - 1;
        }

        return this;
    }

    public void setGUI(GuiQuest questGUI) {
        this.questGUI = questGUI;
    }

    public static synchronized void registerTextProcessor(ResourceLocation id, Function<String, String> textProcessor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(textProcessor, "textProcessor");
        if (textProcessors.containsKey(id)) throw new IllegalArgumentException("duplicate text processor");
        textProcessors.put(id, textProcessor);
    }

    public static synchronized void registerTextInteraction(ResourceLocation id, TextInteraction textInteraction) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(textInteraction, "textInteraction");
        if (textInteractions.containsKey(id)) throw new IllegalArgumentException("duplicate text interaction");
        textInteractions.put(id, textInteraction);
    }

    public static String createInteractiveText(ResourceLocation interactionId, String target, String text) {
        Objects.requireNonNull(interactionId, "interactionId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(text, "text");
        String payload = interactionId + "\u0000" + target;
        String encodedPayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return "[url link=" + INTERACTION_SCHEME + ":" + encodedPayload + "]" + text + "[/url]";
    }

    private static synchronized String processText(String text) {
        String processedText = text;
        for (Function<String, String> textProcessor : textProcessors.values()) {
            processedText = Objects.requireNonNull(textProcessor.apply(processedText), "text processor result");
        }
        return processedText;
    }

    private static String applyQuestLinkUnderline(String text) {
        String underline = FormattingTag.QUESTLINK.getTextFormattingString();
        StringBuilder formattedText = new StringBuilder(text.length() + underline.length());
        formattedText.append(underline);

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            formattedText.append(character);
            if (character == '\u00a7' && i + 1 < text.length()) {
                char formattingCode = text.charAt(++i);
                formattedText.append(formattingCode);
                if (resetsTextFormatting(formattingCode)) {
                    formattedText.append(underline);
                }
            }
        }

        return formattedText.toString();
    }

    private static boolean resetsTextFormatting(char formattingCode) {
        return formattingCode == 'r' || (formattingCode >= '0' && formattingCode <= '9')
            || (formattingCode >= 'a' && formattingCode <= 'f')
            || formattingCode == 'x'
            || formattingCode == 'g'
            || formattingCode == 'q';
    }

    private void bakeHotZones(List<String> lines) {
        hotZones.clear();
        if (!isHyperlinkAware()) return; // not enabled
        if (StringUtils.isBlank(text)) return; // nothing to do
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        IGuiRect fullbox = getTransform();
        if (lines == null) {
            float scale = fontScale / 12F;
            lines = RenderUtils.splitStringWithoutFormat(
                this.text,
                (int) Math.floor(fullbox.getWidth() / scale / textWidthCorrection),
                fr);
        }

        for (linkRange urlRange : linkRanges) {
            Object url = urlRange.link;
            int start = urlRange.start;
            int end = urlRange.end;

            int currentPos = 0;
            boolean foundUrlStart = false;
            for (int lineIndex = 0, lineCount = lines.size(); lineIndex
                < lineCount; currentPos += lines.get(lineIndex++)
                    .length()) {
                String line = lines.get(lineIndex);
                if (!foundUrlStart) {
                    if (start < currentPos + line.length()) {
                        int left = RenderUtils.getStringWidth(line.substring(0, start - currentPos), fr);
                        if (end <= currentPos + line.length()) {
                            // url on same line, early exit
                            int right = RenderUtils.getStringWidth(line.substring(0, end - currentPos), fr);
                            GuiTransform location = new GuiTransform(
                                GuiAlign.FULL_BOX,
                                left,
                                fr.FONT_HEIGHT * lineIndex,
                                right - left,
                                fr.FONT_HEIGHT,
                                0);
                            location.setParent(fullbox);
                            hotZones.add(new HotZone(location, url));
                            break;
                        }
                        // url span multiple lines
                        foundUrlStart = true;
                        GuiTransform location = new GuiTransform(
                            GuiAlign.FULL_BOX,
                            left,
                            fr.FONT_HEIGHT * lineIndex,
                            fullbox.getWidth(),
                            fr.FONT_HEIGHT,
                            0);
                        location.setParent(fullbox);
                        hotZones.add(new HotZone(location, url));
                    }
                } else {
                    if (end <= currentPos + line.length()) {
                        // url ends at current line
                        GuiTransform location = new GuiTransform(
                            GuiAlign.FULL_BOX,
                            0,
                            fr.FONT_HEIGHT * lineIndex,
                            RenderUtils.getStringWidth(line.substring(0, end - currentPos), fr),
                            fr.FONT_HEIGHT,
                            0);
                        location.setParent(fullbox);
                        hotZones.add(new HotZone(location, url));
                        break;
                    } else {
                        // url still going...
                        GuiTransform location = new GuiTransform(
                            GuiAlign.FULL_BOX,
                            0,
                            fr.FONT_HEIGHT * lineIndex,
                            fullbox.getWidth(),
                            fr.FONT_HEIGHT,
                            0);
                        location.setParent(fullbox);
                        hotZones.add(new HotZone(location, url));
                    }
                }
            }
        }
    }

    public PanelTextBox setColor(IGuiColor color) {
        this.color = color;
        return this;
    }

    public PanelTextBox setAlignment(int align) {
        this.align = MathHelper.clamp_int(align, 0, 2);
        return this;
    }

    public PanelTextBox setFontSize(int size) {
        this.fontScale = size;
        return this;
    }

    public PanelTextBox enableShadow(boolean enable) {
        this.shadow = enable;
        return this;
    }

    @Override
    public IGuiRect getTransform() {
        return transform;
    }

    @Override
    public void initPanel() {
        IGuiRect bounds = this.getTransform();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        float scale = fontScale / 12F;

        if (!autoFit) {
            lines = (int) Math.floor(bounds.getHeight() / (fr.FONT_HEIGHT * scale)) - 1;
            return;
        }

        List<String> sl = RenderUtils
            .splitStringWithoutFormat(text, (int) Math.floor(bounds.getWidth() / scale / textWidthCorrection), fr);
        lines = sl.size() - 1;
        bakeHotZones(sl);

        this.transform.h = (int) Math.floor(fr.FONT_HEIGHT * sl.size() * scale);
    }

    @Override
    public void setEnabled(boolean state) {
        this.enabled = state;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        IGuiRect bounds = this.getTransform();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

        float s = fontScale / 12F;
        int w = (int) Math.ceil(RenderUtils.getStringWidth(text, fr) * s);
        int bw = (int) Math.floor(bounds.getWidth() / s / textWidthCorrection);

        if (bw <= 0) return;

        GL11.glPushMatrix();
        GL11.glTranslatef(bounds.getX(), bounds.getY(), 1);
        GL11.glScalef(s, s, 1F);

        if (align == 2 && bw >= w) {
            RenderUtils.drawSplitString(fr, text, bw - w, 0, bw, color.getRGB(), shadow, 0, lines);
        } else if (align == 1 && bw >= w) {
            RenderUtils.drawSplitString(fr, text, bw / 2 - w / 2, 0, bw, color.getRGB(), shadow, 0, lines);
        } else {
            RenderUtils.drawSplitString(fr, text, 0, 0, bw, color.getRGB(), shadow, 0, lines);
        }

        if (BQ_Settings.urlDebug) {
            for (int i = 0, hotZonesSize = hotZones.size(); i < hotZonesSize; i++) {
                RenderUtils.drawHighlightBox(
                    hotZones.get(i).location,
                    new GuiColorStatic(i % 3 == 0 ? 255 : 0, i % 3 == 1 ? 255 : 0, i % 3 == 2 ? 255 : 0, 255));
            }
        }

        GL11.glPopMatrix();
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        int mxt = mx + getTransform().getX(), myt = my + getTransform().getY();
        for (HotZone hotZone : hotZones) {
            if (hotZone.location.contains(mxt, myt)) {
                if (hotZone.link instanceof String) {
                    URI uri = parseUri((String) hotZone.link);
                    if (uri == null) return false;
                    TextInteractionInvocation interaction = getTextInteraction(uri);
                    if (interaction != null) return interaction.textInteraction.onClick(interaction.target);
                    Predicate<URI> handler = URIHandlers.get(uri.getScheme());
                    if (handler == null) return false;
                    return handler.test(uri);
                } else if (hotZone.link instanceof UUID && questGUI != null) {
                    questGUI.navigateToQuest((UUID) hotZone.link);
                    return true;
                }
            }
        }

        return false;
    }

    public static void openURL(URI p_146407_1_) {
        try {
            Class<?> oclass = Class.forName("java.awt.Desktop");
            Object object = oclass.getMethod("getDesktop")
                .invoke(null);
            oclass.getMethod("browse", URI.class)
                .invoke(object, p_146407_1_);
        } catch (Throwable throwable) {
            BetterQuesting.logger.error("Couldn't open link", throwable);
        }
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        return false;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        return false;
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        return false;
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        int mxt = mx + getTransform().getX(), myt = my + getTransform().getY();
        for (HotZone hotZone : hotZones) {
            if (hotZone.location.contains(mxt, myt)) {
                List<String> descriptionTooltip = new ArrayList<>();
                QuestTooltipRegistry.appendTooltip(hotZone.link, descriptionTooltip);
                if (!descriptionTooltip.isEmpty()) return descriptionTooltip;
                if (!(hotZone.link instanceof String)) return null;
                URI uri = parseUri((String) hotZone.link);
                if (uri == null) return null;
                TextInteractionInvocation interaction = getTextInteraction(uri);
                if (interaction != null) return interaction.textInteraction.getTooltip(interaction.target);
                List<String> tooltip = URIHandlers.getTooltip(uri);
                if (tooltip != null && !tooltip.isEmpty()) return tooltip;
            }
        }
        return null;
    }

    public static URI parseUri(String url) {
        try {
            URI uri = new URI(url);
            return uri.getScheme() != null ? uri : new URI(defaultUrlProtocol + "://" + url);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    public static synchronized TextInteractionInvocation getTextInteraction(URI uri) {
        if (!INTERACTION_SCHEME.equals(uri.getScheme())) return null;
        String payload = uri.getRawSchemeSpecificPart();
        if (payload == null || payload.isEmpty()) return null;
        try {
            String decodedPayload = new String(
                Base64.getUrlDecoder()
                    .decode(payload),
                StandardCharsets.UTF_8);
            int separatorIndex = decodedPayload.indexOf('\u0000');
            if (separatorIndex <= 0) return null;
            ResourceLocation id = new ResourceLocation(decodedPayload.substring(0, separatorIndex));
            TextInteraction textInteraction = textInteractions.get(id);
            return textInteraction != null
                ? new TextInteractionInvocation(textInteraction, decodedPayload.substring(separatorIndex + 1))
                : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public interface TextInteraction {

        boolean onClick(String target);

        List<String> getTooltip(String target);
    }

    public static class TextInteractionInvocation {

        public final TextInteraction textInteraction;
        public final String target;

        public TextInteractionInvocation(TextInteraction textInteraction, String target) {
            this.textInteraction = textInteraction;
            this.target = target;
        }
    }

    public static class GuiRectText implements IGuiRect {

        private final IGuiRect proxy;
        private final boolean useH;
        private int h;

        public GuiRectText(IGuiRect proxy, boolean useH) {
            this.proxy = proxy;
            this.useH = useH;
        }

        @Override
        public int getX() {
            return proxy.getX();
        }

        @Override
        public int getY() {
            return proxy.getY();
        }

        @Override
        public int getWidth() {
            return proxy.getWidth();
        }

        @Override
        public int getHeight() {
            return useH ? h : proxy.getHeight();
        }

        @Override
        public int getDepth() {
            return proxy.getDepth();
        }

        @Override
        public IGuiRect getParent() {
            return proxy.getParent();
        }

        @Override
        public void setParent(IGuiRect rect) {
            proxy.setParent(rect);
        }

        @Override
        public boolean contains(int x, int y) {
            int x1 = this.getX();
            int x2 = x1 + this.getWidth();
            int y1 = this.getY();
            int y2 = y1 + this.getHeight();
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }

        /*
         * @Override
         * public void translate(int x, int y)
         * {
         * proxy.translate(x, y);
         * }
         */

        @Override
        public int compareTo(IGuiRect o) {
            return proxy.compareTo(o);
        }
    }

    public static class linkRange {

        public final int start;
        public final int end;
        public final Object link;

        public linkRange(int start, int end, String link) {
            this.start = start;
            this.end = end;
            this.link = link;
        }

        public linkRange(int start, int end, UUID link) {
            this.start = start;
            this.end = end;
            this.link = link;
        }
    }

    public static class HotZone {

        public final IGuiRect location;
        public final Object link;

        public HotZone(IGuiRect location, Object link) {
            this.location = location;
            this.link = link;
        }
    }
}
