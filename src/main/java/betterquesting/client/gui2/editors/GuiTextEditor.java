package betterquesting.client.gui2.editors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.misc.ICallback;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.popups.PopWaitExternalEvent;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.core.BetterQuesting;
import betterquesting.misc.QuestResourcesFile;
import betterquesting.misc.QuestResourcesFolder;

public class GuiTextEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen {

    private final ICallback<String> callback;
    private final boolean imageSupport;
    private final String textIn;

    private PanelTextField<String> flText;

    public GuiTextEditor(GuiScreen parent, String text, ICallback<String> callback) {
        this(parent, text, false, callback);
    }

    public GuiTextEditor(GuiScreen parent, String text, boolean imageSupport, ICallback<String> callback) {
        super(parent);

        textIn = text;
        this.callback = callback;
        this.imageSupport = imageSupport;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0),
                0,
                QuestTranslation.translate("gui.back")));

        PanelTextBox txTitle = new PanelTextBox(
            new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
            QuestTranslation.translate("betterquesting.title.edit_text")).setAlignment(1);
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);

        flText = new PanelTextField<>(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(124, 32, 24, 32), 0),
            flText != null ? flText.getRawText() : textIn,
            FieldFilterString.INSTANCE);
        cvBackground.addPanel(flText);
        flText.setMaxLength(Integer.MAX_VALUE);
        flText.enableWrapping(true);
        flText.lockFocus(true);

        CanvasScrolling cvFormatList = new CanvasScrolling(
            new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(16, 32, -116, 32), 0));
        cvBackground.addPanel(cvFormatList);

        EnumChatFormatting[] tfValues = EnumChatFormatting.values();
        // Specify how many macro buttons are manually added, before the buttons for the default colors and formatting
        int macroCount = 0;

        if (imageSupport) {
            cvFormatList
                .addPanel(new PanelButton(new GuiRectangle(0, 16 * macroCount++, 100, 16), 3, "§2§nSelect Image§r"));
            cvFormatList.addPanel(
                new PanelButtonStorage<>(
                    new GuiRectangle(0, 16 * macroCount++, 100, 16),
                    2,
                    "§2§n Image§r",
                    "[img height=100] [/img]"));
        }
        cvFormatList.addPanel(
            new PanelButtonStorage<>(
                new GuiRectangle(0, 16 * macroCount++, 100, 16),
                2,
                "§9§nHyperlink§r",
                "[url] [/url]"));
        cvFormatList.addPanel(
            new PanelButtonStorage<>(
                new GuiRectangle(0, 16 * macroCount++, 100, 16),
                2,
                "§4§lWarning§r",
                "[warn] [/warn]"));
        cvFormatList.addPanel(
            new PanelButtonStorage<>(new GuiRectangle(0, 16 * macroCount++, 100, 16), 2, "§3Note§r", "[note] [/note]"));
        cvFormatList.addPanel(
            new PanelButtonStorage<>(
                new GuiRectangle(0, 16 * macroCount++, 100, 16),
                2,
                "§2§nQuest Title§r",
                "[quest] [/quest]"));

        for (int i = 0; i < tfValues.length; i++) {
            cvFormatList.addPanel(
                new PanelButtonStorage<>(
                    new GuiRectangle(0, (i + macroCount) * 16, 100, 16),
                    1,
                    String.format("%s%s§r", tfValues[i].toString(), tfValues[i].getFriendlyName()),
                    tfValues[i].toString()));
        }

        PanelVScrollBar scFormatScroll = new PanelVScrollBar(
            new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(0, 0, -8, 0), 0));
        cvBackground.addPanel(scFormatScroll);
        scFormatScroll.getTransform()
            .setParent(cvFormatList.getTransform());
        cvFormatList.setScrollDriverY(scFormatScroll);
        scFormatScroll.setActive(
            cvFormatList.getScrollBounds()
                .getHeight() > 0);
    }

    @Override
    public void onPanelEvent(PanelEvent event) {
        if (event instanceof PEventButton) {
            onButtonPress((PEventButton) event);
        }
    }

    @SuppressWarnings("unchecked")
    private void onButtonPress(PEventButton event) {
        IPanelButton btn = event.getButton();

        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);
        } else if (btn.getButtonID() == 1 && btn instanceof PanelButtonStorage) {
            String format = ((PanelButtonStorage<String>) btn).getStoredValue();
            flText.writeText(format);
        } else if (btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) {
            String[] tagPair = ((PanelButtonStorage<String>) btn).getStoredValue()
                .split(" ");
            String format = tagPair[0] + flText.getSelectedText() + tagPair[1];
            flText.writeText(format);
        } else if (btn.getButtonID() == 3) {
            PopWaitExternalEvent<String> popup = new PopWaitExternalEvent<String>(
                I18n.format("betterquesting.title.choose_image_swing")) {

                @Override
                protected void handleComplete() {
                    try {
                        super.handleComplete();
                    } finally {
                        setNoEscape(false);
                    }
                }

                @Override
                protected void onComplete(String resourceLoc) {
                    String domain = resourceLoc.substring(0, resourceLoc.lastIndexOf(':'));
                    if (!QuestResourcesFolder.lastResourceDomains.contains(domain)) {
                        mc.refreshResources();
                    }
                    writeImageTag(resourceLoc);
                }
            };
            this.setNoEscape(true);
            this.openPopup(popup);
            SwingUtilities.invokeLater(() -> {
                try {
                    selectImage(popup);
                } finally {
                    popup.ensureDone();
                }
            });
        }
    }

    private void writeImageTag(String resourceLoc) {
        final String prefix = "[img height=";
        String tag = prefix + "]" + resourceLoc + "[/img]";
        flText.writeText(tag);
        flText.moveCursorBy(prefix.length() - tag.length());
    }

    // This method is run on swing loop thread!
    private static void selectImage(PopWaitExternalEvent<String> popup) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser
            .setFileFilter(new FileNameExtensionFilter(I18n.format("betterquesting.tooltip.image_file_type"), "png"));

        File src = null, dst = null;
        File targetRoot = QuestResourcesFile.rootFolder.getAbsoluteFile();

        chooser.setCurrentDirectory(targetRoot);
        while (true) {
            if (src == null) {
                chooser.setDialogTitle(I18n.format("betterquesting.title.choose_image.src"));
                int r = chooser.showOpenDialog(null);
                if (r != JFileChooser.APPROVE_OPTION) {
                    popup.cancel();
                    return;
                }
                src = chooser.getSelectedFile()
                    .getAbsoluteFile();
                if (isParent(targetRoot, src) && !src.getParentFile()
                    .equals(targetRoot)) {
                    popup.complete(getResourceLocation(targetRoot, src));
                    return;
                }
            }
            chooser.setDialogTitle(I18n.format("betterquesting.title.choose_image.dst"));
            chooser.setCurrentDirectory(QuestResourcesFile.rootFolder);
            chooser.setSelectedFile(new File(targetRoot, src.getName()).getAbsoluteFile());
            int r = chooser.showSaveDialog(null);
            if (r != JFileChooser.APPROVE_OPTION) {
                popup.cancel();
                return;
            }
            dst = chooser.getSelectedFile()
                .getAbsoluteFile();
            if (!isParent(targetRoot, dst) || dst.getParentFile()
                .equals(targetRoot)) {
                JOptionPane.showMessageDialog(
                    null,
                    I18n.format("betterquesting.gui.not_correct_dir", QuestResourcesFile.rootFolder.getAbsolutePath()),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
                continue;
            }
            if (dst.exists() && JOptionPane.showConfirmDialog(
                null,
                I18n.format("betterquesting.gui.overwrite_file"),
                "Confirm",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
                continue;
            }
            popup.setMessage("Copying image file...");
            try {
                Path target = dst.toPath();
                Files.createDirectories(target.getParent());
                Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), "IO Error", JOptionPane.ERROR_MESSAGE);
                BetterQuesting.logger.error("Copying image file failed", e);
                return;
            }
            popup.complete(getResourceLocation(targetRoot, dst));
            return;
        }
    }

    private static String getResourceLocation(File targetRoot, File dst) {
        Path relative = targetRoot.toPath()
            .relativize(dst.toPath());
        return relative.toString()
            .replace('\\', '/')
            .replaceFirst("/", ":");
    }

    private static boolean isParent(File parent, File child) {
        while (!parent.equals(child)) {
            File curParent = child.getParentFile();
            if (curParent == null || curParent.equals(child)) return false;
            child = curParent;
        }
        return true;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();

        if (callback != null) {
            callback.setValue(flText.getRawText());
        }
    }
}
