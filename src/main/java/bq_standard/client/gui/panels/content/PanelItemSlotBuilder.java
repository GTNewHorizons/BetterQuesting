package bq_standard.client.gui.panels.content;

import betterquesting.api.utils.BigItemStack;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.content.PanelItemSlot;
import bq_standard.core.BQ_Standard;

import java.util.Objects;

public final class PanelItemSlotBuilder {

    private IGuiRect rectangle;
    private BigItemStack value;
    private int id = -1;
    private boolean showCount, oreDict;

    @SuppressWarnings("unused")
    PanelItemSlotBuilder() {}

    private PanelItemSlotBuilder(BigItemStack value, IGuiRect rectangle) {
        this.value = value;
        this.rectangle = rectangle;
    }

    public static PanelItemSlotBuilder forValue(BigItemStack value, IGuiRect rectangle) {
        Objects.requireNonNull(rectangle, "PanelItemSlot rectangle");

        return new PanelItemSlotBuilder(value, rectangle);
    }

    public PanelItemSlotBuilder withId(int id) {
        this.id = id;
        return this;
    }

    public PanelItemSlotBuilder showCount(boolean enabled) {
        this.showCount = enabled;
        return this;
    }

    public PanelItemSlotBuilder oreDict(boolean enabled) {
        this.oreDict = enabled;
        return this;
    }

    public PanelItemSlot build() {
        PanelItemSlot slot;
        if (BQ_Standard.hasNEI && value != null)
            slot = new PanelInteractiveItemSlot(rectangle, id, value, showCount, oreDict);
        else slot = new PanelItemSlot(rectangle, id, value, showCount, oreDict);

        return slot;
    }
}
