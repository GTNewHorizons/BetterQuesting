package betterquesting.api2.client.gui.resources.factories.lines;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonObject;

import betterquesting.api2.client.gui.resources.lines.DirectionalLine;
import betterquesting.api2.client.gui.resources.lines.IGuiLine;
import betterquesting.api2.registry.IFactoryData;

public class FactoryDirectionalLine implements IFactoryData<IGuiLine, JsonObject> {

    public static final FactoryDirectionalLine INSTANCE = new FactoryDirectionalLine();

    private static final ResourceLocation RES_ID = new ResourceLocation("betterquesting", "line_directional");

    @Override
    public DirectionalLine loadFromData(JsonObject data) {
        return createNew();
    }

    @Override
    public ResourceLocation getRegistryName() {
        return RES_ID;
    }

    @Override
    public DirectionalLine createNew() {
        return new DirectionalLine();
    }
}
