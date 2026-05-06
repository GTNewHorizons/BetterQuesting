package betterquesting.client.toolbox.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.client.gui2.editors.designer.PanelToolController;
import betterquesting.client.gui2.editors.nbt.GuiItemSelection;
import betterquesting.network.handlers.NetQuestEdit;

public class ToolboxToolIcon implements IToolboxTool {

    private CanvasQuestLine gui;

    @Override
    public void initTool(CanvasQuestLine gui) {
        this.gui = gui;
    }

    @Override
    public void disableTool() {}

    @Override
    public void refresh(CanvasQuestLine gui) {}

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (click != 0 || !gui.getTransform()
            .contains(mx, my)) return false;

        PanelButtonQuest btn = gui.getButtonAt(mx, my);

        if (btn == null) return false;
        if (PanelToolController.selected.size() > 0 && !PanelToolController.selected.contains(btn)) return false;

        List<PanelButtonQuest> btnList = PanelToolController.selected.size() > 0 ? PanelToolController.selected
            : Collections.singletonList(btn);
        changeIcon(
            btnList,
            btn.getStoredValue()
                .getValue()
                .getProperty(NativeProps.ICON));
        return true;
    }

    private void changeIcon(List<PanelButtonQuest> list, BigItemStack refItem) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiItemSelection(mc.currentScreen, refItem, value -> {
            Map<UUID, IQuest> questsToEdit = new LinkedHashMap<>();
            for (PanelButtonQuest b : list) {
                b.getStoredValue()
                    .getValue()
                    .setProperty(NativeProps.ICON, value);

                questsToEdit.put(
                    b.getStoredValue()
                        .getKey(),
                    b.getStoredValue()
                        .getValue());
            }
            NetQuestEdit.requestEdit(questsToEdit);
        }));
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
    public boolean onKeyPressed(char c, int key) {
        if (PanelToolController.selected.size() <= 0 || key != Keyboard.KEY_RETURN) return false;
        changeIcon(
            PanelToolController.selected,
            PanelToolController.selected.get(0)
                .getStoredValue()
                .getValue()
                .getProperty(NativeProps.ICON));
        return true;
    }

    @Override
    public void drawCanvas(int mx, int my, float partialTick) {}

    @Override
    public void drawOverlay(int mx, int my, float partialTick) {}

    @Override
    public List<String> getTooltip(int mx, int my) {
        return null;
    }

    @Override
    public boolean clampScrolling() {
        return true;
    }

    @Override
    public void onSelection(List<PanelButtonQuest> buttons) {}

    @Override
    public boolean useSelection() {
        return true;
    }
}
