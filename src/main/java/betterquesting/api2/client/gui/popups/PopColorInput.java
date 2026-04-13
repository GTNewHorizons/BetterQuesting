package betterquesting.api2.client.gui.popups;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector4f;

import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterString;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasResizeable;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.ColorTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;

public class PopColorInput extends CanvasEmpty {

    private static final int SV_SIZE = 120;
    private static final int HUE_BAR_W = 12;
    private static final int SV_GRID = 20;
    private static final int PALETTE_COLS = 8;
    private static final int PALETTE_ROWS = 3;
    private static final int CELL_SIZE = 12;
    private static final int CELL_GAP = 1;
    private static final int HISTORY_MAX = 40; // 5 rows of 8

    private static final int[] PRESET_COLORS = { 0xFF0000, 0xFF8000, 0xFFFF00, 0x80FF00, 0x00FF00, 0x00FF80, 0x00FFFF,
        0x0080FF, 0x0000FF, 0x8000FF, 0xFF00FF, 0xFF0080, 0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0x800000,
        0x808000, 0x008000, 0x008080, 0x000080, 0x800080, 0xFF6060 };

    private static final List<Integer> recentColors = new ArrayList<>();

    private final String title;
    private final boolean gradient;
    private final Consumer<String> callback;

    private float hue = 0f;
    private float sat = 1f;
    private float val = 1f;

    /** 0 = first/only color, 1 = second color (gradient mode) */
    private int activeField = 0;
    private int color2Rgb = 0x0000FF;

    private PanelTextField<String> hexField1;
    private PanelTextField<String> hexField2;
    private int previewColor1 = 0xFFFF0000;
    private int previewColor2 = 0xFF0000FF;

    private String lastHexText1 = "";
    private String lastHexText2 = "";

    private boolean suppressFieldSync = false;

    public PopColorInput(@Nonnull String title, boolean gradient, @Nonnull Consumer<String> callback) {
        super(new GuiTransform(GuiAlign.FULL_BOX));
        this.title = title;
        this.gradient = gradient;
        this.callback = callback;
    }

    @Override
    public void initPanel() {
        super.initPanel();

        this.addPanel(
            new PanelGeneric(
                new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 1),
                new ColorTexture(new GuiColorStatic(0x80000000))));

        int boxW = 300;
        int boxH = gradient ? 230 : 200;

        CanvasResizeable cvBox = new CanvasResizeable(
            new GuiTransform(new Vector4f(0.5F, 0.5F, 0.5F, 0.5F)),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBox);
        cvBox.lerpToRect(
            new GuiTransform(new Vector4f(0.5F, 0.5F, 0.5F, 0.5F), -boxW / 2, -boxH / 2, boxW, boxH, 0),
            200L,
            true);

        // Title
        cvBox.addPanel(
            new PanelTextBox(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 8, 4, boxW - 16, 12, 0), title)
                .setAlignment(1)
                .setColor(PresetColor.TEXT_HEADER.getColor()));

        int y = 18;

        // Row 1: SV square + hue bar
        int svX = 8;
        int hueX = svX + SV_SIZE + 6;
        cvBox.addPanel(new SatValPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), svX, y, SV_SIZE, SV_SIZE, 0)));
        cvBox.addPanel(new HueBarPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), hueX, y, HUE_BAR_W, SV_SIZE, 0)));

        // Palette (right of hue bar)
        int palX = hueX + HUE_BAR_W + 10;
        int palY = y;
        cvBox.addPanel(new PalettePanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), palX, palY, 110, 42, 0)));

        // Recent colors (below palette) — 2 rows of 8
        int recentY = palY + PALETTE_ROWS * (CELL_SIZE + CELL_GAP) + 6;
        cvBox.addPanel(
            new PanelTextBox(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), palX, recentY, 60, 10, 0), "Recent")
                .setColor(PresetColor.TEXT_AUX_0.getColor()));
        recentY += 11;
        int recentRows = 5;
        int recentH = recentRows * CELL_SIZE + (recentRows - 1) * CELL_GAP; // 5 rows
        cvBox.addPanel(new RecentPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), palX, recentY, 110, recentH, 0)));

        y += SV_SIZE + 6;

        // Hex input rows
        int fieldX = gradient ? 48 : 22;

        if (gradient) {
            // "Start" label + Edit button
            PanelButton btnStart = new PanelButton(
                new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 8, y, 38, 16, 0),
                -1,
                "Start");
            btnStart.setClickAction(b -> switchActiveField(0));
            cvBox.addPanel(btnStart);
            // Highlight indicator
            cvBox.addPanel(new FieldIndicatorPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 8, y, 38, 16, 0), 0));
        } else {
            cvBox.addPanel(
                new PanelTextBox(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 10, y + 3, 12, 12, 0), "#")
                    .setColor(PresetColor.TEXT_MAIN.getColor()));
        }

        hexField1 = new PanelTextField<>(
            new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), fieldX, y, 60, 16, 0),
            "FF0000",
            FieldFilterString.INSTANCE);
        hexField1.setMaxLength(6);
        cvBox.addPanel(hexField1);

        cvBox.addPanel(
            new ColorPreviewPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), fieldX + 64, y, 16, 16, 0), true));

        lastHexText1 = "FF0000";

        if (gradient) {
            y += 22;

            // "End" label + Edit button
            PanelButton btnEnd = new PanelButton(
                new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 8, y, 38, 16, 0),
                -1,
                "End");
            btnEnd.setClickAction(b -> switchActiveField(1));
            cvBox.addPanel(btnEnd);
            // Highlight indicator
            cvBox.addPanel(new FieldIndicatorPanel(new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 8, y, 38, 16, 0), 1));

            hexField2 = new PanelTextField<>(
                new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), fieldX, y, 60, 16, 0),
                "0000FF",
                FieldFilterString.INSTANCE);
            hexField2.setMaxLength(6);
            cvBox.addPanel(hexField2);

            cvBox.addPanel(
                new ColorPreviewPanel(
                    new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), fieldX + 64, y, 16, 16, 0),
                    false));

            lastHexText2 = "0000FF";
            color2Rgb = 0x0000FF;
        }

        y += 22;

        // OK + Cancel buttons
        PanelButton btnOk = new PanelButton(
            new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), boxW / 2 - 96, y, 88, 16, 0),
            -1,
            "OK");
        btnOk.setClickAction(b -> {
            String hex1 = sanitizeHex(hexField1.getRawText());
            // Add to recent colors
            addRecentColor(parseColorRgb(hex1));
            if (gradient) {
                String hex2 = sanitizeHex(hexField2.getRawText());
                addRecentColor(parseColorRgb(hex2));
                callback.accept("&g&#" + hex1 + "&#" + hex2);
            } else {
                callback.accept("&#" + hex1);
            }
            if (SceneController.getActiveScene() != null) SceneController.getActiveScene()
                .closePopup();
        });
        cvBox.addPanel(btnOk);

        PanelButton btnCancel = new PanelButton(
            new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), boxW / 2 + 8, y, 88, 16, 0),
            -1,
            "Cancel");
        btnCancel.setClickAction(
            b -> {
                if (SceneController.getActiveScene() != null) SceneController.getActiveScene()
                    .closePopup();
            });
        cvBox.addPanel(btnCancel);

        // Initialize HSV from default color
        float[] hsv = rgbToHsv(0xFF0000);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        // Sync hex fields with HSV picker
        if (!suppressFieldSync) {
            // Check if user typed in hex field 1
            String curText1 = hexField1.getRawText();
            if (!curText1.equals(lastHexText1)) {
                lastHexText1 = curText1;
                if (curText1.length() == 6) {
                    int rgb = parseColorRgb(curText1);
                    if (activeField == 0) {
                        float[] hsv = rgbToHsv(rgb);
                        hue = hsv[0];
                        sat = hsv[1];
                        val = hsv[2];
                    }
                }
            }

            if (gradient && hexField2 != null) {
                String curText2 = hexField2.getRawText();
                if (!curText2.equals(lastHexText2)) {
                    lastHexText2 = curText2;
                    if (curText2.length() == 6) {
                        int rgb = parseColorRgb(curText2);
                        if (activeField == 1) {
                            float[] hsv = rgbToHsv(rgb);
                            hue = hsv[0];
                            sat = hsv[1];
                            val = hsv[2];
                        }
                        color2Rgb = rgb;
                    }
                }
            }
        }

        // Update preview colors
        previewColor1 = parseColor(hexField1.getRawText());
        if (gradient && hexField2 != null) {
            previewColor2 = parseColor(hexField2.getRawText());
        }

        super.drawPanel(mx, my, partialTick);
    }

    private void setColorFromPicker() {
        int rgb = hsvToRgb(hue, sat, val);
        String hex = String.format("%06X", rgb);

        suppressFieldSync = true;
        if (activeField == 0) {
            hexField1.setText(hex);
            lastHexText1 = hex;
        } else if (gradient && hexField2 != null) {
            hexField2.setText(hex);
            lastHexText2 = hex;
            color2Rgb = rgb;
        }
        suppressFieldSync = false;
    }

    private void switchActiveField(int fieldIndex) {
        activeField = fieldIndex;
        PanelTextField<String> field = (fieldIndex == 0) ? hexField1 : hexField2;
        if (field != null) {
            int rgb = parseColorRgb(field.getRawText());
            float[] hsv = rgbToHsv(rgb);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
        }
    }

    private void selectColor(int rgb) {
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        setColorFromPicker();
    }

    private static int parseColor(String hex) {
        String sanitized = sanitizeHex(hex);
        try {
            return 0xFF000000 | Integer.parseInt(sanitized, 16);
        } catch (NumberFormatException e) {
            return 0xFFFF0000;
        }
    }

    private static int parseColorRgb(String hex) {
        String sanitized = sanitizeHex(hex);
        try {
            return Integer.parseInt(sanitized, 16);
        } catch (NumberFormatException e) {
            return 0xFF0000;
        }
    }

    static String sanitizeHex(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                sb.append(Character.toUpperCase(c));
            }
        }
        while (sb.length() < 6) {
            sb.append('0');
        }
        return sb.substring(0, 6);
    }

    private static void addRecentColor(int rgb) {
        // Remove if already present
        recentColors.remove(Integer.valueOf(rgb));
        // Add at front
        recentColors.add(0, rgb);
        // Cap at max
        while (recentColors.size() > HISTORY_MAX) {
            recentColors.remove(recentColors.size() - 1);
        }
    }

    // HSV <-> RGB conversion

    static int hsvToRgb(float h, float s, float v) {
        int hi = (int) (h / 60f) % 6;
        float f = h / 60f - hi;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (hi) {
            case 0:
                r = v;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = v;
                b = p;
                break;
            case 2:
                r = p;
                g = v;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = v;
                break;
            case 4:
                r = t;
                g = p;
                b = v;
                break;
            default:
                r = v;
                g = p;
                b = q;
                break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h, s, v = max;
        float d = max - min;
        s = max == 0 ? 0 : d / max;
        if (max == min) {
            h = 0;
        } else if (max == r) {
            h = (g - b) / d + (g < b ? 6 : 0);
            h *= 60;
        } else if (max == g) {
            h = (b - r) / d + 2;
            h *= 60;
        } else {
            h = (r - g) / d + 4;
            h *= 60;
        }
        return new float[] { h, s, v };
    }

    // == TRAP ALL UI USAGE UNTIL CLOSED ===

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        super.onMouseClick(mx, my, click);
        return true;
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        super.onMouseRelease(mx, my, click);
        return true;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        super.onMouseScroll(mx, my, scroll);
        return true;
    }

    @Override
    public boolean onKeyTyped(char c, int keycode) {
        super.onKeyTyped(c, keycode);
        return true;
    }

    // ==================== Inner panel classes ====================

    /** Saturation/Value square: X = saturation, Y = value (inverted) */
    private class SatValPanel implements IGuiPanel {

        private final IGuiRect transform;
        private boolean enabled = true;
        private boolean dragging = false;

        SatValPanel(IGuiRect transform) {
            this.transform = transform;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int x0 = transform.getX();
            int y0 = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();

            // Handle dragging
            if (dragging && Mouse.isButtonDown(0)) {
                updateFromMouse(mx, my);
            } else {
                dragging = false;
            }

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            // Draw grid of colored rectangles
            float cellW = (float) w / SV_GRID;
            float cellH = (float) h / SV_GRID;

            for (int row = 0; row < SV_GRID; row++) {
                for (int col = 0; col < SV_GRID; col++) {
                    float s = col / (float) (SV_GRID - 1);
                    float v = 1f - row / (float) (SV_GRID - 1);
                    int rgb = hsvToRgb(hue, s, v);
                    float r = ((rgb >> 16) & 0xFF) / 255f;
                    float g = ((rgb >> 8) & 0xFF) / 255f;
                    float b = (rgb & 0xFF) / 255f;

                    float cx = x0 + col * cellW;
                    float cy = y0 + row * cellH;

                    GL11.glColor3f(r, g, b);
                    GL11.glBegin(GL11.GL_QUADS);
                    GL11.glVertex2f(cx, cy);
                    GL11.glVertex2f(cx, cy + cellH);
                    GL11.glVertex2f(cx + cellW, cy + cellH);
                    GL11.glVertex2f(cx + cellW, cy);
                    GL11.glEnd();
                }
            }

            // Draw crosshair at current position
            float markerX = x0 + sat * w;
            float markerY = y0 + (1f - val) * h;

            // Outer ring (black)
            GL11.glColor3f(0f, 0f, 0f);
            drawCrosshair(markerX, markerY, 5);
            // Inner ring (white)
            GL11.glColor3f(1f, 1f, 1f);
            drawCrosshair(markerX, markerY, 3);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        private void drawCrosshair(float cx, float cy, int size) {
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(cx - size, cy);
            GL11.glVertex2f(cx + size, cy);
            GL11.glVertex2f(cx, cy - size);
            GL11.glVertex2f(cx, cy + size);
            GL11.glEnd();
        }

        private void updateFromMouse(int mx, int my) {
            int x0 = transform.getX();
            int y0 = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();

            sat = Math.max(0f, Math.min(1f, (float) (mx - x0) / w));
            val = Math.max(0f, Math.min(1f, 1f - (float) (my - y0) / h));
            setColorFromPicker();
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button == 0 && transform.contains(mx, my)) {
                dragging = true;
                updateFromMouse(mx, my);
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
            if (dragging) {
                dragging = false;
                return true;
            }
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
            return null;
        }
    }

    /** Vertical hue bar */
    private class HueBarPanel implements IGuiPanel {

        private final IGuiRect transform;
        private boolean enabled = true;
        private boolean dragging = false;

        HueBarPanel(IGuiRect transform) {
            this.transform = transform;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int x0 = transform.getX();
            int y0 = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();

            if (dragging && Mouse.isButtonDown(0)) {
                updateFromMouse(my);
            } else {
                dragging = false;
            }

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            // Draw vertical hue strips
            for (int row = 0; row < h; row++) {
                float rowHue = (float) row / h * 360f;
                int rgb = hsvToRgb(rowHue, 1f, 1f);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;

                GL11.glColor3f(r, g, b);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(x0, y0 + row);
                GL11.glVertex2f(x0, y0 + row + 1);
                GL11.glVertex2f(x0 + w, y0 + row + 1);
                GL11.glVertex2f(x0 + w, y0 + row);
                GL11.glEnd();
            }

            // Draw marker at current hue
            float markerY = y0 + (hue / 360f) * h;

            // Black outline
            GL11.glColor3f(0f, 0f, 0f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x0 - 1, markerY - 2);
            GL11.glVertex2f(x0 - 1, markerY + 2);
            GL11.glVertex2f(x0 + w + 1, markerY + 2);
            GL11.glVertex2f(x0 + w + 1, markerY - 2);
            GL11.glEnd();

            // White inner
            GL11.glColor3f(1f, 1f, 1f);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x0, markerY - 1);
            GL11.glVertex2f(x0, markerY + 1);
            GL11.glVertex2f(x0 + w, markerY + 1);
            GL11.glVertex2f(x0 + w, markerY - 1);
            GL11.glEnd();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        private void updateFromMouse(int my) {
            int y0 = transform.getY();
            int h = transform.getHeight();
            hue = Math.max(0f, Math.min(359.9f, (float) (my - y0) / h * 360f));
            setColorFromPicker();
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button == 0 && transform.contains(mx, my)) {
                dragging = true;
                updateFromMouse(my);
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
            if (dragging) {
                dragging = false;
                return true;
            }
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
            return null;
        }
    }

    /** Grid of preset palette colors */
    private class PalettePanel implements IGuiPanel {

        private final IGuiRect transform;
        private boolean enabled = true;

        PalettePanel(IGuiRect transform) {
            this.transform = transform;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int x0 = transform.getX();
            int y0 = transform.getY();

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            for (int i = 0; i < PRESET_COLORS.length && i < PALETTE_ROWS * PALETTE_COLS; i++) {
                int col = i % PALETTE_COLS;
                int row = i / PALETTE_COLS;
                int cx = x0 + col * (CELL_SIZE + CELL_GAP);
                int cy = y0 + row * (CELL_SIZE + CELL_GAP);

                int rgb = PRESET_COLORS[i];
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;

                GL11.glColor3f(r, g, b);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(cx, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy);
                GL11.glEnd();
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button != 0 || !transform.contains(mx, my)) return false;

            int x0 = transform.getX();
            int y0 = transform.getY();
            int col = (mx - x0) / (CELL_SIZE + CELL_GAP);
            int row = (my - y0) / (CELL_SIZE + CELL_GAP);

            if (col < 0 || col >= PALETTE_COLS || row < 0 || row >= PALETTE_ROWS) return false;

            int idx = row * PALETTE_COLS + col;
            if (idx >= 0 && idx < PRESET_COLORS.length) {
                selectColor(PRESET_COLORS[idx]);
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
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
            return null;
        }
    }

    /** Row of recently used colors */
    private class RecentPanel implements IGuiPanel {

        private final IGuiRect transform;
        private boolean enabled = true;

        RecentPanel(IGuiRect transform) {
            this.transform = transform;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int x0 = transform.getX();
            int y0 = transform.getY();
            int cols = PALETTE_COLS; // 8 per row

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            for (int i = 0; i < recentColors.size() && i < HISTORY_MAX; i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = x0 + col * (CELL_SIZE + CELL_GAP);
                int cy = y0 + row * (CELL_SIZE + CELL_GAP);

                int rgb = recentColors.get(i);
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;

                GL11.glColor3f(r, g, b);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(cx, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy);
                GL11.glEnd();
            }

            // Draw empty slots as dark gray outlines
            GL11.glColor3f(0.25f, 0.25f, 0.25f);
            for (int i = recentColors.size(); i < HISTORY_MAX; i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = x0 + col * (CELL_SIZE + CELL_GAP);
                int cy = y0 + row * (CELL_SIZE + CELL_GAP);

                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(cx, cy);
                GL11.glVertex2f(cx, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy + CELL_SIZE);
                GL11.glVertex2f(cx + CELL_SIZE, cy);
                GL11.glEnd();
            }

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button != 0 || !transform.contains(mx, my)) return false;

            int x0 = transform.getX();
            int y0 = transform.getY();
            int col = (mx - x0) / (CELL_SIZE + CELL_GAP);
            int row = (my - y0) / (CELL_SIZE + CELL_GAP);
            int idx = row * PALETTE_COLS + col;

            if (idx >= 0 && idx < recentColors.size()) {
                selectColor(recentColors.get(idx));
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
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
            return null;
        }
    }

    /** Color preview rectangle next to hex field — click to switch active field in gradient mode */
    private class ColorPreviewPanel implements IGuiPanel {

        private final IGuiRect transform;
        private final boolean isFirst;
        private boolean enabled = true;

        ColorPreviewPanel(IGuiRect transform, boolean isFirst) {
            this.transform = transform;
            this.isFirst = isFirst;
        }

        private void switchToField() {
            int target = isFirst ? 0 : 1;
            if (gradient && activeField != target) {
                activeField = target;
                PanelTextField<String> field = isFirst ? hexField1 : hexField2;
                if (field != null) {
                    int rgb = parseColorRgb(field.getRawText());
                    float[] hsv = rgbToHsv(rgb);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                }
            }
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int color = isFirst ? previewColor1 : previewColor2;
            float a = ((color >> 24) & 0xFF) / 255F;
            float r = ((color >> 16) & 0xFF) / 255F;
            float g = ((color >> 8) & 0xFF) / 255F;
            float b = (color & 0xFF) / 255F;

            int x = transform.getX();
            int y = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(r, g, b, a);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x + w, y);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button == 0 && transform.contains(mx, my)) {
                switchToField();
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
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
            return null;
        }
    }

    /** Thin colored bar indicating which hex field the picker controls (gradient mode) */
    private class FieldIndicatorPanel implements IGuiPanel {

        private final IGuiRect transform;
        private final int fieldIndex;
        private boolean enabled = true;

        FieldIndicatorPanel(IGuiRect transform, int fieldIndex) {
            this.transform = transform;
            this.fieldIndex = fieldIndex;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            if (activeField != fieldIndex) return;

            int x = transform.getX();
            int y = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            // Draw highlight border around the active row
            GL11.glColor3f(0.3f, 0.8f, 0.3f); // green highlight
            GL11.glLineWidth(2f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x + w, y);
            GL11.glEnd();
            GL11.glLineWidth(1f);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button == 0 && transform.contains(mx, my) && activeField != fieldIndex) {
                activeField = fieldIndex;
                PanelTextField<String> field = (fieldIndex == 0) ? hexField1 : hexField2;
                if (field != null) {
                    int rgb = parseColorRgb(field.getRawText());
                    float[] hsv = rgbToHsv(rgb);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
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
            return null;
        }
    }

    /** Clickable area to switch which hex field the picker controls (gradient mode) */
    private class FieldSelectPanel implements IGuiPanel {

        private final IGuiRect transform;
        private final int fieldIndex;
        private boolean enabled = true;

        FieldSelectPanel(IGuiRect transform, int fieldIndex) {
            this.transform = transform;
            this.fieldIndex = fieldIndex;
        }

        @Override
        public IGuiRect getTransform() {
            return transform;
        }

        @Override
        public void initPanel() {}

        @Override
        public void setEnabled(boolean state) {
            this.enabled = state;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void drawPanel(int mx, int my, float partialTick) {
            int x = transform.getX();
            int y = transform.getY();
            int w = transform.getWidth();
            int h = transform.getHeight();
            boolean active = activeField == fieldIndex;

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            // Draw button background
            if (active) {
                GL11.glColor3f(0.2f, 0.6f, 0.2f); // green = active
            } else if (transform.contains(mx, my)) {
                GL11.glColor3f(0.4f, 0.4f, 0.5f); // hover highlight
            } else {
                GL11.glColor3f(0.3f, 0.3f, 0.35f); // idle
            }
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w, y + h);
            GL11.glVertex2f(x + w, y);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            // Draw label text
            net.minecraft.client.Minecraft.getMinecraft().fontRenderer
                .drawString(active ? "Active" : "Edit", x + 4, y + 4, active ? 0xFFFFFF : 0xAAAAAA);
        }

        @Override
        public boolean onMouseClick(int mx, int my, int button) {
            if (button == 0 && transform.contains(mx, my)) {
                activeField = fieldIndex;
                // Load this field's color into the HSV picker
                PanelTextField<String> field = (fieldIndex == 0) ? hexField1 : hexField2;
                if (field != null) {
                    int rgb = parseColorRgb(field.getRawText());
                    float[] hsv = rgbToHsv(rgb);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onMouseRelease(int mx, int my, int button) {
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
            if (transform.contains(mx, my)) {
                List<String> tt = new ArrayList<>();
                tt.add(activeField == fieldIndex ? "Active" : "Click to edit");
                return tt;
            }
            return null;
        }
    }
}
