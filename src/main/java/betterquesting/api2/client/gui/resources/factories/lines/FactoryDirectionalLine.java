package betterquesting.api2.client.gui.resources.factories.lines;

import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonObject;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.client.gui.resources.lines.DirectionalLine;
import betterquesting.api2.client.gui.resources.lines.IGuiLine;
import betterquesting.api2.registry.IFactoryData;

public class FactoryDirectionalLine implements IFactoryData<IGuiLine, JsonObject> {

    public static final FactoryDirectionalLine INSTANCE = new FactoryDirectionalLine();

    private static final ResourceLocation RES_ID = new ResourceLocation("betterquesting", "line_directional");

    @Override
    public DirectionalLine loadFromData(JsonObject data) {
        float arrowWidth = JsonHelper.GetNumber(data, "arrowWidth", DirectionalLine.DefArrowWidth)
            .floatValue();
        float arrowSize = JsonHelper.GetNumber(data, "arrowSize", DirectionalLine.DefArrowSize)
            .floatValue();
        float arrowOpacity = JsonHelper.GetNumber(data, "arrowOpacity", DirectionalLine.DefArrowOpacity)
            .floatValue();
        return new DirectionalLine(arrowWidth, arrowSize, arrowOpacity);
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
