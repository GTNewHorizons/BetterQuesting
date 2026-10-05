package bq_standard.tasks.base;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import betterquesting.api.questing.tasks.TaskProgressLine;
import betterquesting.api.utils.BigItemStack;

/**
 * Formats the "12/64 Sand" lines shown by the quest tracker HUD.
 */
public class HudProgress {

    public static List<TaskProgressLine> line(String name, long progress, long required) {
        List<TaskProgressLine> lines = new ArrayList<>(1);
        lines.add(format(progress, required, name));
        return lines;
    }

    public static List<TaskProgressLine> items(List<BigItemStack> required, int[] progress) {
        List<TaskProgressLine> lines = new ArrayList<>(required.size());
        for (int i = 0; i < required.size(); i++) {
            BigItemStack req = required.get(i);
            int done = i < progress.length ? progress[i] : 0;
            lines.add(format(done, req.stackSize, itemName(req)));
        }
        return lines;
    }

    public static List<TaskProgressLine> fluids(List<FluidStack> required, int[] progress) {
        List<TaskProgressLine> lines = new ArrayList<>(required.size());
        for (int i = 0; i < required.size(); i++) {
            FluidStack req = required.get(i);
            int done = i < progress.length ? progress[i] : 0;
            lines.add(format(done, req.amount, req.getLocalizedName()));
        }
        return lines;
    }

    public static TaskProgressLine format(long progress, long required, String name) {
        return new TaskProgressLine(Math.min(progress, required) + "/" + required + " " + name, progress >= required);
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
