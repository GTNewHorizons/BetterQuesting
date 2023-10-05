package bq_standard.client.gui;

import net.minecraft.client.gui.inventory.GuiContainer;

import java.util.function.Consumer;

public class GuiContainerFake extends GuiContainer {
    public GuiContainerFake() {
        super(null);
    }

    private Consumer onInitCallback;

    @Override
    public void initGui() {
        if (onInitCallback != null) onInitCallback.accept(null);
    }

    @Override
    public void onGuiClosed() {}

    @Override
    protected void drawGuiContainerBackgroundLayer(float p_146976_1_, int p_146976_2_, int p_146976_3_) {}

    public void setOnInitCallback(Consumer onInitCallback) {
        this.onInitCallback = onInitCallback;
    }
}
