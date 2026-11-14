package betterquesting.api2.client.gui.panels.content;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Replaces {@code [item]modid:name[/item]}, {@code [item meta=3]modid:name[/item]} and
 * {@code [fluid]internalname[/fluid]} tags in quest text with the display name of the item or
 * fluid in the client's language.
 *
 * <p>
 * Quest text only stores the registry identifier, so the name shown in a quest always matches the
 * name shown in game and never has to be translated by hand.
 *
 * <p>
 * If the item or fluid cannot be found (for example because the mod providing it is disabled), the
 * identifier itself is displayed instead.
 */
public class ResourceNameTextProcessor {

    public static final ResourceLocation ID = new ResourceLocation(BetterQuesting.MODID, "resource_names");

    private static final Pattern RESOURCE_TAG = Pattern
        .compile("\\[(item|fluid)(?: meta=(\\d+))?] *([^\\[\\] ]+) *\\[/(item|fluid)]");
    private static final String ITEM = "item";

    private ResourceNameTextProcessor() {}

    public static void register() {
        PanelTextBox.registerTextProcessor(ID, ResourceNameTextProcessor::process);
    }

    public static String process(String text) {
        if (text == null || text.indexOf('[') < 0) return text;

        Matcher matcher = RESOURCE_TAG.matcher(text);
        StringBuffer out = new StringBuffer(text.length());
        while (matcher.find()) {
            String tag = matcher.group(1);
            // A tag closed by the other kind is left untouched, so the text keeps whatever the author wrote.
            if (!tag.equals(matcher.group(4))) continue;

            String id = matcher.group(3);
            String meta = matcher.group(2);
            String name = ITEM.equals(tag) ? resolveItem(id, meta) : resolveFluid(id, meta);
            matcher.appendReplacement(out, Matcher.quoteReplacement(name));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String resolveItem(String id, String meta) {
        int separator = id.indexOf(':');
        if (separator < 0) {
            BetterQuesting.logger.warn("Malformed item name tag in quest text: {}", id);
            return id;
        }

        Item item = GameRegistry.findItem(id.substring(0, separator), id.substring(separator + 1));
        if (item == null) {
            BetterQuesting.logger.warn("Unknown item in quest text: {}", id);
            return id;
        }

        try {
            return new ItemStack(item, 1, meta == null ? 0 : Integer.parseInt(meta)).getDisplayName();
        } catch (Exception e) {
            BetterQuesting.logger.warn("Unable to get the display name of {} in quest text", id, e);
            return id;
        }
    }

    private static String resolveFluid(String id, String meta) {
        if (meta != null) {
            BetterQuesting.logger.warn("Fluid name tag in quest text has a meta value, ignoring it: {}", id);
        }

        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) {
            BetterQuesting.logger.warn("Unknown fluid in quest text: {}", id);
            return id;
        }
        return new FluidStack(fluid, 1).getLocalizedName();
    }
}
