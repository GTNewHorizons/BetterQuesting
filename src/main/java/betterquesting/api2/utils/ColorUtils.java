package betterquesting.api2.utils;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("bq_standard");

    public static final ColorResource
    // spotless:off
        neiQuestNameColor           = color.rgb("neiQuestNameColor",        "0x000000"),
        neiQuestNameHoveredColor    = color.rgb("neiQuestNameHoveredColor", "0xA87A5E");
    // spotless:on
}
