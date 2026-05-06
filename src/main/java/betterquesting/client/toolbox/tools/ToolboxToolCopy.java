package betterquesting.client.toolbox.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;

import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.client.gui2.editors.designer.PanelToolController;
import betterquesting.client.toolbox.ToolboxTabMain;
import betterquesting.network.handlers.NetChapterEdit;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;

public class ToolboxToolCopy implements IToolboxTool {

    private CanvasQuestLine gui = null;

    private final List<GrabEntry> grabList = new ArrayList<>();

    @Override
    public void initTool(CanvasQuestLine gui) {
        this.gui = gui;
        grabList.clear();
    }

    @Override
    public void disableTool() {
        grabList.clear();
    }

    @Override
    public void refresh(CanvasQuestLine gui) {
        if (grabList.size() <= 0) {
            return;
        }

        List<GrabEntry> tmp = new ArrayList<>();

        for (GrabEntry grab : grabList) {
            for (PanelButtonQuest btn : PanelToolController.selected) {
                if (btn.getStoredValue()
                    .getKey()
                    .equals(
                        grab.btn.getStoredValue()
                            .getKey())) {
                    tmp.add(new GrabEntry(btn, grab.offX, grab.offY));
                    break;
                }
            }
        }

        grabList.clear();
        grabList.addAll(tmp);
    }

    @Override
    public void drawCanvas(int mx, int my, float partialTick) {
        if (grabList.size() <= 0) return;

        int snap = Math.max(1, ToolboxTabMain.INSTANCE.getSnapValue());
        int dx = mx;
        int dy = my;
        dx = ((dx % snap) + snap) % snap;
        dy = ((dy % snap) + snap) % snap;
        dx = mx - dx;
        dy = my - dy;

        for (GrabEntry grab : grabList) {
            grab.btn.rect.x = dx + grab.offX;
            grab.btn.rect.y = dy + grab.offY;
            grab.btn.drawPanel(dx, dy, partialTick);
        }
    }

    @Override
    public void drawOverlay(int mx, int my, float partialTick) {
        if (grabList.size() > 0) ToolboxTabMain.INSTANCE.drawGrid(gui);
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        return grabList.size() <= 0 ? null : Collections.emptyList();
    }

    @Override
    public boolean onMouseClick(int mx, int my, int click) {
        if (click == 1 && grabList.size() > 0) {
            grabList.clear();
            return true;
        } else if (click != 0 || !gui.getTransform()
            .contains(mx, my)) {
                return false;
            }

        if (grabList.size() == 0) {
            PanelButtonQuest btnClicked = gui.getButtonAt(mx, my);
            if (btnClicked == null) return false;

            // Pickup the group or the single one if none are selected
            if (PanelToolController.selected.size() > 0) {
                if (!PanelToolController.selected.contains(btnClicked)) return false;

                for (PanelButtonQuest btn : PanelToolController.selected) {
                    GuiRectangle rect = new GuiRectangle(btn.rect);
                    grabList.add(
                        new GrabEntry(
                            new PanelButtonQuest(rect, -1, "", btn.getStoredValue()),
                            rect.x - btnClicked.rect.x,
                            rect.y - btnClicked.rect.y));
                }
            } else {
                grabList.add(
                    new GrabEntry(
                        new PanelButtonQuest(new GuiRectangle(btnClicked.rect), -1, "", btnClicked.getStoredValue()),
                        0,
                        0));
            }

            return true;
        }

        // Pre-sync
        IQuestLine questLine = gui.getQuestLine();
        UUID questLineId = QuestLineDatabase.INSTANCE.lookupKey(questLine);

        ArrayList<UUID> newIDs = new ArrayList<>(generateNewIDs(grabList.size()));
        HashMap<UUID, UUID> remappedIDs = new HashMap<>(grabList.size());

        for (int i = 0; i < grabList.size(); i++) {
            remappedIDs.put(
                grabList.get(i).btn.getStoredValue()
                    .getKey(),
                newIDs.get(i));
        }

        HashMap<UUID, IQuest> questsToCreate = new HashMap<>();

        for (int i = 0; i < grabList.size(); i++) {
            GrabEntry grab = grabList.get(i);
            IQuest quest = grab.btn.getStoredValue()
                .getValue();
            UUID newQuestId = newIDs.get(i);

            questLine.put(
                newQuestId,
                new QuestLineEntry(grab.btn.rect.x, grab.btn.rect.y, grab.btn.rect.w, grab.btn.rect.h));

            HashSet<UUID> reqsCopy = new HashSet<>(quest.getRequirements());
            boolean hasReqsChanged = false;

            for (Map.Entry<UUID, UUID> entry : remappedIDs.entrySet()) {
                if (reqsCopy.remove(entry.getKey())) {
                    reqsCopy.add(entry.getValue());
                    hasReqsChanged = true;
                }
            }

            if (!hasReqsChanged) {
                questsToCreate.put(newQuestId, quest);
                continue;
            }

            IQuest questCopy = new QuestInstance();
            questCopy.readFromNBT(quest.writeToNBT(new NBTTagCompound()));

            // setRequirements removes requirement types not present in the reqsCopy,
            // but not adds any new types, hence the loop for setting requirement types
            questCopy.setRequirements(reqsCopy);
            for (UUID oldId : quest.getRequirements()) {
                UUID newId = remappedIDs.get(oldId);
                IQuest.RequirementType requirementType = quest.getRequirementType(oldId);
                questCopy.setRequirementType(newId, requirementType);
            }

            questsToCreate.put(newQuestId, questCopy);
        }

        grabList.clear();

        // Send new quests
        NetQuestEdit.requestCreate(questsToCreate);

        // Send quest line edits
        NetChapterEdit.requestEdit(questLineId, questLine);

        return true;
    }

    private static Set<UUID> generateNewIDs(int count) {
        Set<UUID> newIds = new HashSet<>();
        while (newIds.size() < count) {
            // In the extremely unlikely event of a collision,
            // we'll handle it automatically due to newIds being a Set
            newIds.add(QuestDatabase.INSTANCE.generateKey());
        }
        return newIds;
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
        return grabList.size() > 0;
    }

    @Override
    public boolean clampScrolling() {
        return grabList.size() <= 0;
    }

    @Override
    public void onSelection(List<PanelButtonQuest> buttons) {}

    @Override
    public boolean useSelection() {
        return grabList.size() <= 0;
    }

    private class GrabEntry {

        private final PanelButtonQuest btn;
        private final int offX;
        private final int offY;

        private GrabEntry(PanelButtonQuest btn, int offX, int offY) {
            this.btn = btn;
            this.offX = offX;
            this.offY = offY;
        }
    }
}
