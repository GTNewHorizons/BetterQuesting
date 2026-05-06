package betterquesting.client.toolbox.tools;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.client.toolbox.ToolboxTabMain;
import betterquesting.network.handlers.NetChapterEdit;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;

public class ToolboxToolNew implements IToolboxTool {

    private CanvasQuestLine gui = null;
    private PanelButtonQuest nQuest;

    @Override
    public void initTool(CanvasQuestLine gui) {
        this.gui = gui;

        nQuest = new PanelButtonQuest(new GuiRectangle(0, 0, 24, 24), -1, "", null);
    }

    @Override
    public void refresh(CanvasQuestLine gui) {}

    @Override
    public void drawCanvas(int mx, int my, float partialTick) {
        if (nQuest == null) {
            return;
        }

        int snap = ToolboxTabMain.INSTANCE.getSnapValue();
        int modX = ((mx % snap) + snap) % snap;
        int modY = ((my % snap) + snap) % snap;
        mx -= modX;
        my -= modY;

        nQuest.rect.x = mx;
        nQuest.rect.y = my;
        nQuest.drawPanel(mx, my, partialTick); // TODO: Draw relative
    }

    @Override
    public void drawOverlay(int mx, int my, float partialTick) {
        ToolboxTabMain.INSTANCE.drawGrid(gui);
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        return Collections.emptyList();
    }

    @Override
    public void disableTool() {
        if (nQuest != null) nQuest = null;
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (click != 0 || !gui.getTransform()
            .contains(mx, my)) {
            return false;
        }

        // Pre-sync
        IQuestLine qLine = gui.getQuestLine();
        UUID qID = QuestDatabase.INSTANCE.generateKey();
        UUID lID = QuestLineDatabase.INSTANCE.lookupKey(qLine);
        IQuestLineEntry qe = qLine.get(qID);// new QuestLineEntry(mx, my, 24);

        if (qe == null) {
            qe = new QuestLineEntry(nQuest.rect.x, nQuest.rect.y, 24, 24);
            qLine.put(qID, qe);
        } else {
            qe.setPosition(nQuest.rect.x, nQuest.rect.y);
            qe.setSize(24, 24);
        }

        // Sync Quest
        NetQuestEdit.requestCreate(qID);

        // Sync Line
        NBTTagCompound chPayload = new NBTTagCompound();
        NBTTagList cdList = new NBTTagList();
        NBTTagCompound cTag = new NBTTagCompound();
        NBTConverter.UuidValueType.QUEST_LINE.writeId(lID, cTag);
        cTag.setTag("config", qLine.writeToNBT(new NBTTagCompound(), null));
        cdList.appendTag(cTag);
        chPayload.setTag("data", cdList);
        chPayload.setInteger("action", 0);
        NetChapterEdit.sendEdit(chPayload);

        return true;
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
        return false;
    }

    @Override
    public void onSelection(List<PanelButtonQuest> buttons) {}

    @Override
    public boolean useSelection() {
        return false;
    }
}
