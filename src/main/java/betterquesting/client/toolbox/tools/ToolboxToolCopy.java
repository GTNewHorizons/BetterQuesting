package betterquesting.client.toolbox.tools;

import betterquesting.api.client.toolbox.IToolboxTool;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.client.gui2.editors.designer.PanelToolController;
import betterquesting.client.toolbox.ToolboxTabMain;
import betterquesting.network.handlers.NetChapterEdit;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ToolboxToolCopy implements IToolboxTool
{
	private CanvasQuestLine gui = null;
	
	private final List<GrabEntry> grabList = new ArrayList<>();
	
	@Override
	public void initTool(CanvasQuestLine gui)
	{
		this.gui = gui;
        grabList.clear();
	}
	
	@Override
	public void disableTool()
	{
        grabList.clear();
	}
	
	@Override
    public void refresh(CanvasQuestLine gui)
    {
        if (grabList.size() <= 0)
        {
            return;
        }
        
        List<GrabEntry> tmp = new ArrayList<>();
        
        for (GrabEntry grab : grabList)
        {
            for (PanelButtonQuest btn : PanelToolController.selected)
            {
                if (btn.getStoredValue().getKey().equals(grab.btn.getStoredValue().getKey()))
                {
                    tmp.add(new GrabEntry(btn, grab.offX, grab.offY));
                    break;
                }
            }
        }
        
        grabList.clear();
        grabList.addAll(tmp);
    }
	
	@Override
	public void drawCanvas(int mx, int my, float partialTick)
	{
        if(grabList.size() <= 0) return;
	    
	    int snap = Math.max(1, ToolboxTabMain.INSTANCE.getSnapValue());
	    int dx = mx;
	    int dy = my;
	    dx = ((dx%snap) + snap)%snap;
	    dy = ((dy%snap) + snap)%snap;
	    dx = mx - dx;
	    dy = my - dy;
	    
	    for(GrabEntry grab : grabList)
        {
            grab.btn.rect.x = dx + grab.offX;
            grab.btn.rect.y = dy + grab.offY;
            grab.btn.drawPanel(dx, dy, partialTick);
        }
	}
	
	@Override
    public void drawOverlay(int mx, int my, float partialTick)
    {
        if(grabList.size() > 0) ToolboxTabMain.INSTANCE.drawGrid(gui);
    }
    
    @Override
    public List<String> getTooltip(int mx, int my)
    {
        return grabList.size() <= 0 ? null : Collections.emptyList();
    }
	
	@Override
	public boolean onMouseClick(int mx, int my, int click)
	{
		if(click == 1 && grabList.size() > 0)
		{
			grabList.clear();
			return true;
		} else if(click != 0 || !gui.getTransform().contains(mx, my))
		{
			return false;
		}
		
		if(grabList.size() <= 0)
		{
			PanelButtonQuest btnClicked = gui.getButtonAt(mx, my);
			
			if(btnClicked != null) // Pickup the group or the single one if none are selected
            {
                if(PanelToolController.selected.size() > 0)
                {
                    if(!PanelToolController.selected.contains(btnClicked)) return false;
                    
                    for(PanelButtonQuest btn : PanelToolController.selected)
                    {
                        GuiRectangle rect = new GuiRectangle(btn.rect);
                        grabList.add(new GrabEntry(new PanelButtonQuest(rect, -1, "", btn.getStoredValue()), rect.x - btnClicked.rect.x, rect.y - btnClicked.rect.y));
                    }
                } else
                {
                    grabList.add(new GrabEntry(new PanelButtonQuest(new GuiRectangle(btnClicked.rect), -1, "", btnClicked.getStoredValue()), 0, 0));
                }
                
                return true;
            }
            
            return false;
		}
		
        // Pre-sync
        IQuestLine qLine = gui.getQuestLine();
        UUID lID = QuestLineDatabase.INSTANCE.lookupKey(qLine);

        // Turn Set into List so that we can access by index.
        List<UUID> nextIDs = new ArrayList<>(getNextIDs(grabList.size()));
        BiMap<UUID, UUID> remappedIDs = HashBiMap.create(grabList.size());
        
        for (int i = 0; i < grabList.size(); i++)
        {
            remappedIDs.put(grabList.get(i).btn.getStoredValue().getKey(), nextIDs.get(i));
        }
        
        NBTTagList qdList = new NBTTagList();
        
        for (int i = 0; i < grabList.size(); i++)
        {
            GrabEntry grab = grabList.get(i);
            IQuest quest = grab.btn.getStoredValue().getValue();
            UUID qID = nextIDs.get(i);
            
            if (qLine.get(qID) == null)
            {
                qLine.put(qID, new QuestLineEntry(grab.btn.rect.x, grab.btn.rect.y, grab.btn.rect.w, grab.btn.rect.h));
            }
            
            NBTTagCompound questTags = quest.writeToNBT(new NBTTagCompound());
            Set<UUID> reqs = new HashSet<>(quest.getRequirements());

            for (Map.Entry<UUID, UUID> entry : remappedIDs.entrySet())
            {
                if (reqs.contains(entry.getKey()))
                {
                    reqs.remove(entry.getKey());
                    reqs.add(entry.getValue());
                }
            }

            // We can't tamper with the original so we change it in NBT post-write
            NBTTagList tagList = new NBTTagList();
            for (UUID questID : reqs)
            {
                NBTTagCompound tag = NBTConverter.UuidValueType.QUEST.writeId(questID);

                // We need the pre-remapped ID so that we can look up the prerequisite type.
                UUID oldID = remappedIDs.inverse().getOrDefault(questID, questID);
                IQuest.RequirementType requirementType = quest.getRequirementType(oldID);
                if (requirementType != IQuest.RequirementType.NORMAL)
                {
                    tag.setByte("type", requirementType.id());
                }

                tagList.appendTag(tag);
            }
            questTags.setTag("preRequisites", tagList);

            NBTTagCompound tagEntry = new NBTTagCompound();
            NBTConverter.UuidValueType.QUEST.writeId(qID, tagEntry);
            tagEntry.setTag("config", questTags);
            qdList.appendTag(tagEntry);
        }
        
        grabList.clear();
        
        // Send new quests
        NBTTagCompound quPayload = new NBTTagCompound();
        quPayload.setTag("data", qdList);
        quPayload.setInteger("action", 3);
        NetQuestEdit.sendEdit(quPayload);
        
        // Send quest line edits
        NBTTagCompound chPayload = new NBTTagCompound();
        NBTTagList cdList = new NBTTagList();
        NBTTagCompound tagEntry = new NBTTagCompound();
        NBTConverter.UuidValueType.QUEST_LINE.writeId(lID, tagEntry);
        tagEntry.setTag("config", qLine.writeToNBT(new NBTTagCompound(), null));
        cdList.appendTag(tagEntry);
        chPayload.setTag("data", cdList);
        chPayload.setInteger("action", 0);
        NetChapterEdit.sendEdit(chPayload);
		
		return true;
	}

	private static Set<UUID> getNextIDs(int num)
    {
        Set<UUID> nextIds = new HashSet<>();
        while (nextIds.size() < num)
        {
            // In the extremely unlikely event of a collision,
            // we'll handle it automatically due to nextIds being a Set
            nextIds.add(QuestDatabase.INSTANCE.generateKey());
        }
        return nextIds;
    }
	
	@Override
    public boolean onMouseRelease(int mx, int my, int click)
    {
        return false;
    }
	
	@Override
	public boolean onMouseScroll(int mx, int my, int scroll)
	{
	    return false;
	}
	
	@Override
	public boolean onKeyPressed(char c, int keyCode)
	{
	    return grabList.size() > 0;
	}
	
	@Override
	public boolean clampScrolling()
	{
		return grabList.size() <= 0;
	}
	
	@Override
    public void onSelection(List<PanelButtonQuest> buttons)
    {
    }
	
	@Override
    public boolean useSelection()
    {
        return grabList.size() <= 0;
    }
	
	private class GrabEntry
    {
        private final PanelButtonQuest btn;
        private final int offX;
        private final int offY;
        
        private GrabEntry(PanelButtonQuest btn, int offX, int offY)
        {
            this.btn = btn;
            this.offX = offX;
            this.offY = offY;
        }
    }
}
