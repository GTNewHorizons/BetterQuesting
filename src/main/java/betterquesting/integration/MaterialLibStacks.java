package betterquesting.integration;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.ruling_0.materiallib.api.StackResolver;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.utils.BigItemStack;

/// Resolves the `ml:<Material>:<shape>` item ids MaterialLib-aware quest files carry. Such an entry names its item by
/// material and shape instead of by registry name and metadata, so it survives sessions that renumber MaterialLib's
/// metadata.
///
/// This is the only class holding MaterialLib types, so the rest of BetterQuesting loads without MaterialLib
/// installed. Reference it only when `Loader.isModLoaded("materiallib")` passes.
public final class MaterialLibStacks {

    private MaterialLibStacks() {}

    /// Returns the stack the id names, keeping `nbt`'s Count, OreDict and tag and replacing its id and Damage.
    /// Returns null when the id is malformed or names nothing MaterialLib registers.
    @Nullable
    public static BigItemStack resolve(String idName, NBTTagCompound nbt) {
        String[] parts = idName.split(":");
        if (parts.length != 3 || parts[1].isEmpty() || parts[2].isEmpty()) {
            QuestingAPI.getLogger()
                .warn("Malformed MaterialLib item id \"{}\", expected ml:<Material>:<shape>", idName);
            return null;
        }

        ItemStack stack = StackResolver.getStack(parts[1], parts[2], 1);
        if (stack == null) return null;

        NBTTagCompound resolved = (NBTTagCompound) nbt.copy();
        resolved.setInteger("id", Item.itemRegistry.getIDForObject(stack.getItem()));
        resolved.setShort("Damage", (short) stack.getItemDamage());
        return BigItemStack.loadItemStackFromNBT(resolved);
    }
}
