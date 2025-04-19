package betterquesting.api2.client.gui.popups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import betterquesting.api2.client.gui.SceneController;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;

/**
 * More flexible extension of {@link PopChoice} that allows for callbacks per button.
 */
public class PopChoiceExt extends PopChoice {

    private final List<ChoiceData> choices = new ArrayList<>();

    @SuppressWarnings("ConstantConditions") // can be null, since we are overriding the options array
    public PopChoiceExt(@Nonnull String message, @Nullable IGuiTexture icon) {
        super(message, icon, null);
    }

    public PopChoiceExt addOption(String option, Consumer<PanelButton> callback, boolean closesPanel,
        String... tooltips) {
        List<String> tooltipList = null;
        if (tooltips != null && tooltips.length != 0) {
            tooltipList = Arrays.asList(tooltips);
        }

        this.choices.add(new ChoiceData(option, callback, closesPanel, tooltipList));
        return this;
    }

    @Override
    protected int getChoicesCount() {
        return choices.size();
    }

    @Override
    protected String getOptionString(int index) {
        return choices.get(index).option;
    }

    @Override
    protected List<String> getButtonTooltip(int index) {
        return choices.get(index).tooltips;
    }

    @Override
    protected void setButtonAction(PanelButton btn, int index) {
        ChoiceData data = choices.get(index);
        btn.setClickAction(b -> {
            if (data.callback != null) data.callback.accept(b);
            if (data.closesPanel && SceneController.getActiveScene() != null) SceneController.getActiveScene()
                .closePopup();
        });
    }

    private static class ChoiceData {

        private final String option;
        private final Consumer<PanelButton> callback;
        private final boolean closesPanel;
        private final List<String> tooltips;

        public ChoiceData(String option, Consumer<PanelButton> callback, boolean closesPanel, List<String> tooltips) {
            this.option = option;
            this.callback = callback;
            this.closesPanel = closesPanel;
            this.tooltips = tooltips;
        }
    }
}
