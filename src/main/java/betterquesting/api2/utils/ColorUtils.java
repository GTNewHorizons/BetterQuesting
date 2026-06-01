package betterquesting.api2.utils;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("vendingmachine");

    public static final ColorResource
    // spotless:off
        neiQuestNameColor           = color.rgb("neiQuestNameColor",        "#000000"),
        neiQuestNameHoveredColor    = color.rgb("neiQuestNameHoveredColor", "#A87A5E");
    // spotless:on
}
