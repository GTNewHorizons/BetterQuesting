package bq_standard.tasks.base;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import betterquesting.api.utils.BigItemStack;

/**
 * Formats the "12/64 Sand" lines shown by the quest tracker HUD.
 */
public class HudProgress {

    public static List<String> line(String name, long progress, long required) {
        List<String> lines = new ArrayList<>(1);
        lines.add(Math.min(progress, required) + "/" + required + " " + name);
        return lines;
    }

    public static List<String> items(List<BigItemStack> required, int[] progress) {
        List<String> lines = new ArrayList<>(required.size());
        for (int i = 0; i < required.size(); i++) {
            BigItemStack req = required.get(i);
            int done = i < progress.length ? progress[i] : 0;
            lines.add(Math.min(done, req.stackSize) + "/" + req.stackSize + " " + itemName(req));
        }
        return lines;
    }

    public static List<String> fluids(List<FluidStack> required, int[] progress) {
        List<String> lines = new ArrayList<>(required.size());
        for (int i = 0; i < required.size(); i++) {
            FluidStack req = required.get(i);
            int done = i < progress.length ? progress[i] : 0;
            lines.add(Math.min(done, req.amount) + "/" + req.amount + " " + req.getLocalizedName());
        }
        return lines;
    }

    private static String itemName(BigItemStack stack) {
        if (stack.hasOreDict()) return stack.getOreDict();
        ItemStack base = stack.getBaseStack();
        if (base == null) return "?";
        try {
            return base.getDisplayName();
        } catch (Exception e) {
            // Some modded items throw while building a display name outside a GUI context
            return base.getUnlocalizedName();
        }
    }
}
