package betterquesting.client.gui2.editors;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.util.vector.Vector4f;

import com.google.common.collect.Maps;

import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.client.gui.misc.IVolatileScreen;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.rewards.IReward;
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
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelLine;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.panels.lists.CanvasSearch;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetLine;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.registry.IFactoryData;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.editors.nbt.GuiNbtEditor;
import betterquesting.network.handlers.NetQuestEdit;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.rewards.RewardRegistry;

public class GuiRewardEditor extends GuiScreenCanvas implements IPEventListener, IVolatileScreen, INeedsRefresh {

    private CanvasScrolling qrList;

    private IQuest quest;
    private final UUID qID;

    public GuiRewardEditor(GuiScreen parent, IQuest quest) {
        super(parent);

        this.quest = quest;
        this.qID = QuestDatabase.INSTANCE.lookupKey(quest);
    }

    @Override
    public void refreshGui() {
        quest = QuestDatabase.INSTANCE.get(qID);

        if (quest == null) {
            mc.displayGuiScreen(this.parent);
            return;
        }

        refreshRewards();
    }

    @Override
    public void initPanel() {
        super.initPanel();

        if (qID == null) {
            mc.displayGuiScreen(this.parent);
            return;
        }

        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);

        // Background panel
        CanvasTextured cvBackground = new CanvasTextured(
            new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0),
            PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        PanelTextBox panTxt = new PanelTextBox(
            new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(0, 16, 0, -32), 0),
            QuestTranslation.translate("betterquesting.title.edit_rewards")).setAlignment(1);
        panTxt.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(panTxt);

        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0),
                0,
                QuestTranslation.translate("gui.back")));

        CanvasSearch<IFactoryData<IReward, NBTTagCompound>, IFactoryData<IReward, NBTTagCompound>> cvRegSearch = new CanvasSearch<IFactoryData<IReward, NBTTagCompound>, IFactoryData<IReward, NBTTagCompound>>(
            (new GuiTransform(GuiAlign.HALF_RIGHT, new GuiPadding(8, 48, 24, 32), 0))) {

            @Override
            protected Iterator<IFactoryData<IReward, NBTTagCompound>> getIterator() {
                List<IFactoryData<IReward, NBTTagCompound>> list = RewardRegistry.INSTANCE.getAll();
                list.sort(
                    Comparator.comparing(
                        o -> o.getRegistryName()
                            .toString()
                            .toLowerCase()));
                return list.iterator();
            }

            @Override
            protected void queryMatches(IFactoryData<IReward, NBTTagCompound> value, String query,
                ArrayDeque<IFactoryData<IReward, NBTTagCompound>> results) {
                if (value.getRegistryName()
                    .toString()
                    .toLowerCase()
                    .contains(query.toLowerCase())) results.add(value);
            }

            @Override
            protected boolean addResult(IFactoryData<IReward, NBTTagCompound> entry, int index, int cachedWidth) {
                this.addPanel(
                    new PanelButtonStorage<>(
                        new GuiRectangle(0, index * 16, cachedWidth, 16, 0),
                        1,
                        entry.getRegistryName()
                            .toString(),
                        entry));
                return true;
            }
        };
        cvBackground.addPanel(cvRegSearch);

        PanelVScrollBar scReg = new PanelVScrollBar(
            new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-24, 48, 16, 32), 0));
        cvBackground.addPanel(scReg);
        cvRegSearch.setScrollDriverY(scReg);

        PanelTextField<String> tfSearch = new PanelTextField<>(
            new GuiTransform(new Vector4f(0.5F, 0F, 1F, 0F), new GuiPadding(8, 32, 16, -48), 0),
            "",
            FieldFilterString.INSTANCE);
        tfSearch.setCallback(cvRegSearch::setSearchFilter);
        tfSearch.setWatermark("Search...");
        cvBackground.addPanel(tfSearch);

        qrList = new CanvasScrolling(new GuiTransform(GuiAlign.HALF_LEFT, new GuiPadding(16, 32, 16, 32), 0));
        cvBackground.addPanel(qrList);

        PanelVScrollBar scRew = new PanelVScrollBar(
            new GuiTransform(new Vector4f(0.5F, 0F, 0.5F, 1F), new GuiPadding(-16, 32, 8, 32), 0));
        cvBackground.addPanel(scRew);
        qrList.setScrollDriverY(scRew);

        // === DIVIDERS ===

        IGuiRect ls0 = new GuiTransform(GuiAlign.TOP_CENTER, 0, 32, 0, 0, 0);
        ls0.setParent(cvBackground.getTransform());
        IGuiRect le0 = new GuiTransform(GuiAlign.BOTTOM_CENTER, 0, -32, 0, 0, 0);
        le0.setParent(cvBackground.getTransform());
        PanelLine paLine0 = new PanelLine(
            ls0,
            le0,
            PresetLine.GUI_DIVIDER.getLine(),
            1,
            PresetColor.GUI_DIVIDER.getColor(),
            1);
        cvBackground.addPanel(paLine0);

        refreshRewards();
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
        } else if (btn.getButtonID() == 1 && btn instanceof PanelButtonStorage) // Add
        {
            IFactoryData<IReward, NBTTagCompound> fact = ((PanelButtonStorage<IFactoryData<IReward, NBTTagCompound>>) btn)
                .getStoredValue();
            quest.getRewards()
                .add(
                    quest.getRewards()
                        .nextID(),
                    fact.createNew());

            SendChanges();
        } else if (btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) // Remove
        {
            IReward reward = ((PanelButtonStorage<IReward>) btn).getStoredValue();

            if (quest.getRewards()
                .removeValue(reward)) {
                SendChanges();
            }
        } else if (btn.getButtonID() == 3 && btn instanceof PanelButtonStorage) // Edit
        {
            IReward reward = ((PanelButtonStorage<IReward>) btn).getStoredValue();
            GuiScreen editor = reward.getRewardEditor(this, Maps.immutableEntry(qID, quest));

            if (editor != null) {
                mc.displayGuiScreen(editor);
            } else {
                mc.displayGuiScreen(new GuiNbtEditor(this, reward.writeToNBT(new NBTTagCompound()), value -> {
                    reward.readFromNBT(value);
                    SendChanges();
                }));
            }
        }
    }

    private void refreshRewards() {
        List<DBEntry<IReward>> dbRew = quest.getRewards()
            .getEntries();

        qrList.resetCanvas();
        int w = qrList.getTransform()
            .getWidth();

        for (int i = 0; i < dbRew.size(); i++) {
            IReward reward = dbRew.get(i)
                .getValue();
            qrList.addPanel(
                new PanelButtonStorage<>(
                    new GuiRectangle(0, i * 16, w - 16, 16, 0),
                    3,
                    QuestTranslation.translate(reward.getUnlocalisedName()),
                    reward));
            qrList.addPanel(
                new PanelButtonStorage<>(
                    new GuiRectangle(w - 16, i * 16, 16, 16, 0),
                    2,
                    "" + EnumChatFormatting.RED + EnumChatFormatting.BOLD + "x",
                    reward));
        }
    }

    private void SendChanges() {
        NBTTagCompound payload = new NBTTagCompound();
        NBTTagList dataList = new NBTTagList();
        NBTTagCompound entry = NBTConverter.UuidValueType.QUEST.writeId(qID);
        entry.setTag("config", quest.writeToNBT(new NBTTagCompound()));
        dataList.appendTag(entry);
        payload.setTag("data", dataList);
        payload.setInteger("action", 0);
        NetQuestEdit.sendEdit(payload);
    }
}
