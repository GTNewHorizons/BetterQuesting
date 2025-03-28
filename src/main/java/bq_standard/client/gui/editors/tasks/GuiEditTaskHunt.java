package bq_standard.client.gui.editors.tasks;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.network.QuestingPacket;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelTextField;
import betterquesting.api2.client.gui.controls.filters.FieldFilterNumber;
import betterquesting.api2.client.gui.controls.io.ValueFuncIO;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.content.PanelEntityPreview;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.gui_args.GArgsCallback;
import betterquesting.api2.client.gui.themes.gui_args.GArgsNBT;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetGUIs;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.tasks.TaskHunt;

public class GuiEditTaskHunt extends GuiScreenCanvas {

    private final Map.Entry<UUID, IQuest> quest;
    private final TaskHunt task;

    public GuiEditTaskHunt(GuiScreen parent, Map.Entry<UUID, IQuest> quest, TaskHunt task) {
        super(parent);
        this.quest = quest;
        this.task = task;
        this.setVolatile(true);
    }

    @Override
    public void initPanel() {
        super.initPanel();

        Keyboard.enableRepeatEvents(true);

        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);

        cvBackground.addPanel(
            new PanelTextBox(
                new GuiTransform(GuiAlign.TOP_EDGE, new GuiPadding(16, 16, 16, -32), 0),
                QuestTranslation.translate("bq_standard.title.edit_hunt")).setAlignment(1)
                    .setColor(PresetColor.TEXT_HEADER.getColor()));

        final Entity target;

        if (EntityList.stringToClassMapping.containsKey(task.idName)) {
            target = EntityList.createEntityByName(task.idName, Minecraft.getMinecraft().theWorld);
            if (target != null && !task.ignoreNBT) target.readFromNBT(task.targetTags);
        } else target = null;

        this.addPanel(
            new PanelEntityPreview(new GuiTransform(GuiAlign.HALF_TOP, new GuiPadding(16, 32, 16, 0), 0), target)
                .setRotationDriven(
                    new ValueFuncIO<>(() -> 15F),
                    new ValueFuncIO<>(() -> (float) (Minecraft.getSystemTime() % 30000L / 30000D * 360D)))); // Preview
                                                                                                             // works
                                                                                                             // with
                                                                                                             // null.
                                                                                                             // It's
                                                                                                             // fine (or
                                                                                                             // should
                                                                                                             // be)

        cvBackground.addPanel(
            new PanelTextBox(
                new GuiTransform(GuiAlign.MID_CENTER, -100, 4, 96, 12, 0),
                QuestTranslation.translate("bq_standard.gui.amount")).setAlignment(2)
                    .setColor(PresetColor.TEXT_MAIN.getColor()));
        cvBackground.addPanel(
            new PanelTextField<>(
                new GuiTransform(GuiAlign.MID_CENTER, 0, 0, 100, 16, 0),
                "" + task.required,
                FieldFilterNumber.INT).setCallback(value -> task.required = value));

        final GuiScreen screenRef = this;
        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.MID_CENTER, -100, 16, 200, 16, 0),
                -1,
                QuestTranslation.translate("bq_standard.btn.select_mob")) {

                @Override
                public void onButtonClick() {
                    mc.displayGuiScreen(
                        QuestingAPI.getAPI(ApiReference.THEME_REG)
                            .getGui(PresetGUIs.EDIT_ENTITY, new GArgsCallback<>(screenRef, target, value -> {
                                Entity tmp = value != null ? value : new EntityZombie(mc.theWorld);
                                String res = EntityList.getEntityString(tmp);
                                task.idName = res != null ? res : "Zombie";
                                task.targetTags = new NBTTagCompound();
                                tmp.writeToNBTOptional(task.targetTags);

                                sendChanges();
                            })));
                }
            });

        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.MID_CENTER, -100, 32, 200, 16, 0),
                -1,
                QuestTranslation.translate("betterquesting.btn.advanced")) {

                @Override
                public void onButtonClick() {
                    mc.displayGuiScreen(
                        QuestingAPI.getAPI(ApiReference.THEME_REG)
                            .getGui(
                                PresetGUIs.EDIT_NBT,
                                new GArgsNBT<>(
                                    screenRef,
                                    task.writeToNBT(new NBTTagCompound()),
                                    task::readFromNBT,
                                    null)));
                }
            });

        cvBackground.addPanel(
            new PanelButton(
                new GuiTransform(GuiAlign.BOTTOM_CENTER, -100, -16, 200, 16, 0),
                -1,
                QuestTranslation.translate("gui.done")) {

                @Override
                public void onButtonClick() {
                    sendChanges();
                    mc.displayGuiScreen(parent);
                }
            });
    }

    private static final ResourceLocation QUEST_EDIT = new ResourceLocation("betterquesting:quest_edit"); // TODO:
                                                                                                          // Really need
                                                                                                          // to make the
                                                                                                          // native
                                                                                                          // packet
                                                                                                          // types
                                                                                                          // accessible
                                                                                                          // in the API

    private void sendChanges() {
        NBTTagCompound payload = new NBTTagCompound();
        NBTTagList dataList = new NBTTagList();
        NBTTagCompound entry = NBTConverter.UuidValueType.QUEST.writeId(quest.getKey());
        entry.setTag(
            "config",
            quest.getValue()
                .writeToNBT(new NBTTagCompound()));
        dataList.appendTag(entry);
        payload.setTag("data", dataList);
        payload.setInteger("action", 0); // Action: Update data
        QuestingAPI.getAPI(ApiReference.PACKET_SENDER)
            .sendToServer(new QuestingPacket(QUEST_EDIT, payload));
    }
}
