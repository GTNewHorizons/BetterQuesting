package betterquesting.client.toolbox.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.client.gui2.editors.designer.PanelToolController;
import betterquesting.network.handlers.NetQuestEdit;

public class ToolboxToolLink implements IToolboxTool {

    private CanvasQuestLine gui;
    private final List<PanelButtonQuest> linking = new ArrayList<>();
    private final GuiRectangle mouseRect = new GuiRectangle(0, 0, 0, 0);

    @Override
    public void initTool(CanvasQuestLine gui) {
        this.gui = gui;
        linking.clear();
    }

    @Override
    public void disableTool() {
        linking.clear();
    }

    @Override
    public void refresh(CanvasQuestLine gui) {
        if (linking.isEmpty()) {
            return;
        }

        List<PanelButtonQuest> tmp = new ArrayList<>();

        for (PanelButtonQuest b1 : linking) {
            for (PanelButtonQuest b2 : gui.getQuestButtons()) {
                if (b1.getStoredValue()
                    .getKey()
                    .equals(
                        b2.getStoredValue()
                            .getKey())) {
                    tmp.add(b2);
                }
            }
        }

        linking.clear();
        linking.addAll(tmp);
    }

    @Override
    public void drawCanvas(int mx, int my, float partialTick) {
        if (linking.size() <= 0) return;

        mouseRect.x = mx;
        mouseRect.y = my;

        for (PanelButtonQuest btn : linking) {
            PresetLine.QUEST_COMPLETE.getLine()
                .drawLine(btn.rect, mouseRect, 2, PresetColor.QUEST_LINE_COMPLETE.getColor(), partialTick);
        }
    }

    @Override
    public void drawOverlay(int mx, int my, float partialTick) {}

    @Override
    public List<String> getTooltip(int mx, int my) {
        return null;
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (click == 1 && linking.size() > 0) {
            linking.clear();
            return true;
        } else if (click != 0 || !gui.getTransform()
            .contains(mx, my)) {
                return false;
            }

        if (linking.size() <= 0) {
            PanelButtonQuest btn = gui.getButtonAt(mx, my);
            if (btn == null) return false;

            if (PanelToolController.selected.size() > 0) {
                if (!PanelToolController.selected.contains(btn)) return false;
                linking.addAll(PanelToolController.selected);
                return true;
            }

            linking.add(btn);
            return true;
        } else {
            PanelButtonQuest b2 = gui.getButtonAt(mx, my);

            if (b2 == null) return false;
            linking.remove(b2);

            if (linking.size() > 0) {
                IQuest q2 = b2.getStoredValue()
                    .getValue();
                boolean mod2 = false;

                NBTTagList dataList = new NBTTagList();

                for (PanelButtonQuest b1 : linking) {
                    IQuest q1 = b1.getStoredValue()
                        .getValue();
                    boolean mod1 = false;

                    // Don't have to worry about the lines anymore. The panel is getting refereshed anyway
                    if (!containsReq(
                        q2,
                        b1.getStoredValue()
                            .getKey())
                        && !containsReq(
                            q1,
                            b2.getStoredValue()
                                .getKey())) {
                        mod2 = addReq(
                            q2,
                            b1.getStoredValue()
                                .getKey())
                            || mod2;
                    } else {
                        mod2 = removeReq(
                            q2,
                            b1.getStoredValue()
                                .getKey())
                            || mod2;
                        mod1 = removeReq(
                            q1,
                            b2.getStoredValue()
                                .getKey());
                    }

                    if (mod1) {
                        NBTTagCompound entry = NBTConverter.UuidValueType.QUEST.writeId(
                            b1.getStoredValue()
                                .getKey());
                        entry.setTag(
                            "config",
                            b1.getStoredValue()
                                .getValue()
                                .writeToNBT(new NBTTagCompound()));
                        dataList.appendTag(entry);
                    }
                }

                if (mod2) {
                    NBTTagCompound entry = NBTConverter.UuidValueType.QUEST.writeId(
                        b2.getStoredValue()
                            .getKey());
                    entry.setTag("config", q2.writeToNBT(new NBTTagCompound()));
                    dataList.appendTag(entry);
                }

                NBTTagCompound payload = new NBTTagCompound();
                payload.setTag("data", dataList);
                payload.setInteger("action", 0);
                NetQuestEdit.sendEdit(payload);

                linking.clear();
                return true;
            }

            return false;
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
    public boolean onKeyPressed(char c, int keyCode) {
        return false;
    }

    @Override
    public boolean clampScrolling() {
        return true;
    }

    @Override
    public void onSelection(List<PanelButtonQuest> buttons) {}

    @Override
    public boolean useSelection() {
        return linking.isEmpty();
    }

    private boolean containsReq(IQuest quest, UUID id) {
        return quest.getRequirements()
            .contains(id);
    }

    private boolean removeReq(IQuest quest, UUID id) {
        return quest.getRequirements()
            .remove(id);
    }

    private boolean addReq(IQuest quest, UUID id) {
        return quest.getRequirements()
            .add(id);
    }
}
