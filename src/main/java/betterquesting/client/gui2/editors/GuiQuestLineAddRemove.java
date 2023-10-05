package betterquesting.client.gui2.editors;

import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.utils.NBTConverter;
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
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasEmpty;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestDatabase;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.network.handlers.NetChapterEdit;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;
import com.google.common.collect.Maps;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class GuiQuestLineAddRemove extends GuiScreenCanvas implements IPEventListener, IVolatileScreen, INeedsRefresh
{
    @Nullable
    private IQuestLine questLine;
    private final UUID lineID;
    
    private CanvasQuestDatabase canvasDB;
    private CanvasScrolling canvasQL;
    
    public GuiQuestLineAddRemove(GuiScreen parent, @Nullable IQuestLine questLine)
    {
        super(parent);
        this.questLine = questLine;
        this.lineID = QuestLineDatabase.INSTANCE.lookupKey(questLine);
    }
    
    @Override
    public void refreshGui()
    {
        questLine = lineID == null ? null : QuestLineDatabase.INSTANCE.get(lineID);
        canvasDB.refreshSearch();
        if (questLine != null)
        {
            refreshQuestList();
        }
    }
    
    @Override
    public void initPanel()
    {
        super.initPanel();
    
        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        Keyboard.enableRepeatEvents(true);
    
        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);
    
        PanelTextBox panTxt = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0), QuestTranslation.translate("betterquesting.title.edit_line2", questLine == null ? "" : QuestTranslation.translateQuestLineName(lineID, questLine))).setAlignment(1);
        panTxt.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(panTxt);
    
        cvBackground.addPanel(new PanelButton(new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0), 0, QuestTranslation.translate("gui.back")));
        
        // === LEFT SIDE ===
    
        CanvasEmpty cvLeft = new CanvasEmpty(new GuiTransform(GuiAlign.HALF_LEFT, new GuiPadding(16, 32, 8, 24), 8));
        cvBackground.addPanel(cvLeft);
        
        if(questLine != null)
        {
            PanelTextBox txtQuest = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0), QuestTranslation.translateQuestLineName(lineID, questLine)).setAlignment(1).setColor(PresetColor.TEXT_MAIN.getColor());
            cvLeft.addPanel(txtQuest);
        }
        
        canvasQL = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 16, 8, 0), 0));
        cvLeft.addPanel(canvasQL);
        
        PanelVScrollBar scReq = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 16, 0, 0), 0));
        cvLeft.addPanel(scReq);
        canvasQL.setScrollDriverY(scReq);
        
        // === RIGHT SIDE ==
        
        CanvasEmpty cvRight = new CanvasEmpty(new GuiTransform(GuiAlign.HALF_RIGHT, new GuiPadding(8, 32, 16, 24), 0));
        cvBackground.addPanel(cvRight);
        
        PanelTextBox txtDb = new PanelTextBox(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 0, 0, -16), 0), QuestTranslation.translate("betterquesting.gui.database")).setAlignment(1).setColor(PresetColor.TEXT_MAIN.getColor());
        cvRight.addPanel(txtDb);
        
        PanelTextField<String> searchBox = new PanelTextField<>(new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 8, -32), 0), "", FieldFilterString.INSTANCE);
        searchBox.setWatermark("Search...");
        cvRight.addPanel(searchBox);
        
        canvasDB = new CanvasQuestDatabase(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 32, 8, 24), 0))
        {
            @Override
            protected boolean addResult(Map.Entry<UUID, IQuest> entry, int index, int width)
            {
                
                PanelButtonStorage<Map.Entry<UUID, IQuest>> btnAdd = new PanelButtonStorage<>(new GuiRectangle(0, index * 16, 16, 16, 0), 2, "", entry);
                btnAdd.setIcon(PresetIcon.ICON_POSITIVE.getTexture());
                btnAdd.setActive(questLine != null && questLine.get(entry.getKey()) == null);
                this.addPanel(btnAdd);
                
                PanelButtonStorage<Map.Entry<UUID, IQuest>> btnEdit = new PanelButtonStorage<>(new GuiRectangle(16, index * 16, width - 32, 16, 0), 1, QuestTranslation.translateQuestName(entry), entry);
                this.addPanel(btnEdit);
                
                PanelButtonStorage<Map.Entry<UUID, IQuest>> btnDel = new PanelButtonStorage<>(new GuiRectangle(width - 16, index * 16, 16, 16, 0), 4, "", entry);
                btnDel.setIcon(PresetIcon.ICON_TRASH.getTexture());
                this.addPanel(btnDel);
                
                return true;
            }
        };
        cvRight.addPanel(canvasDB);
        
        searchBox.setCallback(canvasDB::setSearchFilter);
    
        PanelVScrollBar scDb = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-8, 32, 0, 24), 0));
        cvRight.addPanel(scDb);
        canvasDB.setScrollDriverY(scDb);
        
        PanelButton btnNew = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_EDGE, new GuiPadding(0, -16, 0, 0), 0), 5, QuestTranslation.translate("betterquesting.btn.new"));
        cvRight.addPanel(btnNew);
        
        // === DIVIDERS ===
        
		IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_CENTER, 0, 32, 0, 0, 0);
		ls0.setParent(cvBackground.getTransform());
		IGuiRect le0 = new GuiTransform(GuiAlign.BOTTOM_CENTER, 0, -24, 0, 0, 0);
		le0.setParent(cvBackground.getTransform());
		PanelLine paLine0 = new PanelLine(ls0, le0, PresetLine.GUI_DIVIDER.getLine(), 1, PresetColor.GUI_DIVIDER.getColor(), 1);
		cvBackground.addPanel(paLine0);
        
        refreshQuestList();
    }
	
	@Override
	public void onPanelEvent(PanelEvent event)
	{
		if(event instanceof PEventButton)
		{
			onButtonPress((PEventButton)event);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void onButtonPress(PEventButton event)
    {
        IPanelButton btn = event.getButton();
        
        if (btn.getButtonID() == 0) // Exit
        {
            mc.displayGuiScreen(this.parent);
        }
        else if (btn.getButtonID() == 1) // Edit
        {
            Map.Entry<UUID, IQuest> entry = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>) btn).getStoredValue();
            mc.displayGuiScreen(new GuiQuest(this, entry.getKey()));
        }
        else if (btn.getButtonID() == 2) // Add
        {
            Map.Entry<UUID, IQuest> entry = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>)btn).getStoredValue();
            IQuestLineEntry qe = new QuestLineEntry(0, 0);
            int x1 = 0;
            int y1 = 0;
            
            topLoop:
            while (questLine != null)
            {
                for (IQuestLineEntry qle : questLine.values())
                {
                    int x2 = qle.getPosX();
                    int y2 = qle.getPosY();
                    int s2 = qle.getSize();
                    
                    if (x1 >= x2 && x1 < x2 + s2 && y1 >= y2 && y1 < y2 + s2)
                    {
                        x1 += s2;
                        y1 += s2;
                        continue topLoop; // We're in the way, move over and try again
                    }
                }
                
                break;
            }
            
            qe.setPosition(x1, y1);
            questLine.put(entry.getKey(), qe);
            SendChanges();
        }
        else if (btn.getButtonID() == 3 && questLine != null) // Remove
        {
            Map.Entry<UUID, IQuest> entry = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>) btn).getStoredValue();
            questLine.remove(entry.getKey());
            SendChanges();
        }
        else if (btn.getButtonID() == 4) // Delete
        {
            Map.Entry<UUID, IQuest> entry = ((PanelButtonStorage<Map.Entry<UUID, IQuest>>)btn).getStoredValue();
            NBTTagCompound payload = new NBTTagCompound();
            payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(Collections.singletonList(entry.getKey())));
            payload.setInteger("action", 1);
            NetQuestEdit.sendEdit(payload);
        }
        else if (btn.getButtonID() == 5) // New
        {
            NBTTagCompound payload = new NBTTagCompound();
            NBTTagList dataList = new NBTTagList();
            NBTTagCompound entry = new NBTTagCompound();
            dataList.appendTag(entry);
            payload.setTag("data", dataList);
            payload.setInteger("action", 3);
            NetQuestEdit.sendEdit(payload);
        }
        else if (btn.getButtonID() == 6) // Error resolve
        {
            NBTTagCompound payload = new NBTTagCompound();
            payload.setTag("questIDs", NBTConverter.UuidValueType.QUEST.writeIds(Collections.singletonList(((PanelButtonStorage<UUID>) btn).getStoredValue())));
            payload.setInteger("action", 1);
            NetQuestEdit.sendEdit(payload);
        }
    }
    
    private void refreshQuestList()
    {
        canvasQL.resetCanvas();
        
        if(questLine == null)
        {
            return;
        }
        
        int width = canvasQL.getTransform().getWidth();
    
        Iterator<Map.Entry<UUID, IQuestLineEntry>> qleIterator = questLine.entrySet().iterator();
        for (int i = 0; qleIterator.hasNext(); i++)
        {
            Map.Entry<UUID, IQuestLineEntry> entry = qleIterator.next();
            
            IQuest quest = QuestDatabase.INSTANCE.get(entry.getKey());
            
            if (quest == null)
            {
                PanelButtonStorage<UUID> btnErr = new PanelButtonStorage<>(new GuiRectangle(width - 16, i * 16, 16, 16, 0), 6, "[ERROR]", entry.getKey());
                btnErr.setActive(true);
                canvasQL.addPanel(btnErr);
                continue;
            }

            Map.Entry<UUID, IQuest> questEntry = Maps.immutableEntry(entry.getKey(), quest);
            PanelButtonStorage<Map.Entry<UUID, IQuest>> btnEdit = new PanelButtonStorage<>(new GuiRectangle(0, i * 16, width - 16, 16, 0), 1, QuestTranslation.translateQuestName(questEntry), questEntry);
            canvasQL.addPanel(btnEdit);
            
            PanelButtonStorage<Map.Entry<UUID, IQuest>> btnRem = new PanelButtonStorage<>(new GuiRectangle(width - 16, i * 16, 16, 16, 0), 3, "", questEntry);
            btnRem.setIcon(PresetIcon.ICON_NEGATIVE.getTexture());
            canvasQL.addPanel(btnRem);
        }
    }
	
	private void SendChanges()
	{
	    if (questLine == null)
        {
            return;
        }
	    
	    NBTTagCompound payload = new NBTTagCompound();
        NBTTagList dataList = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        NBTConverter.UuidValueType.QUEST_LINE.writeId(lineID, entry);
        entry.setTag("config", questLine.writeToNBT(new NBTTagCompound(), null));
        dataList.appendTag(entry);
        payload.setTag("data", dataList);
        payload.setInteger("action", 0);
        NetChapterEdit.sendEdit(payload);
	}
}
