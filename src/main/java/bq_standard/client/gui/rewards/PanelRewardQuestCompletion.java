package bq_standard.client.gui.rewards;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.CanvasMinimum;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import bq_standard.rewards.RewardQuestCompletion;
import org.lwjgl.util.vector.Vector4f;

public class PanelRewardQuestCompletion extends CanvasMinimum {
    private final RewardQuestCompletion reward;
    private final IGuiRect initialRect;

    public PanelRewardQuestCompletion(IGuiRect rect, RewardQuestCompletion reward) {
        super(rect);
        this.reward = reward;
        initialRect = rect;
    }

    @Override
    public void initPanel() {
        super.initPanel();
        int width = initialRect.getWidth();

        IQuest quest = QuestingAPI.getAPI(ApiReference.QUEST_DB).getValue(reward.questNum);
        this.addPanel(new PanelButtonQuest(
                new GuiRectangle(0, 0, 18, 18, 0),
                -1,
                "",
                quest == null ? null : new DBEntry<>(reward.questNum, quest)));
        this.addPanel(new PanelTextBox(
                        new GuiTransform(new Vector4f(0F, 0F, 0F, 0F), 36, 2, width - 36, 16, 0),
                        QuestTranslation.translate("bq_standard.gui.questcompletion"))
                .setColor(PresetColor.TEXT_MAIN.getColor()));
        recalcSizes();
    }
}
