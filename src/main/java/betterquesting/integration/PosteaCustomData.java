package betterquesting.integration;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.postea.api.CustomDataReplacementManager;

import cpw.mods.fml.common.Loader;

/// Lets Postea's versioned transformers migrate the item stacks saved in BetterQuesting's world storage files.
///
/// Each method passes through to [CustomDataReplacementManager] and does nothing when Postea is not installed.
public final class PosteaCustomData {

    private static final boolean POSTEA = Loader.isModLoaded("postea");

    private PosteaCustomData() {}

    /// Transforms a root read from disk in place. Call before consuming it. Throws
    /// [net.minecraft.util.ReportedException] when a transformer fails.
    public static void transform(String storageId, NBTTagCompound root) {
        if (POSTEA) Impl.transform(storageId, root);
    }

    /// Stamps a freshly built root about to be written to disk, keeping the stamps in `carried` for transformers not
    /// registered now. `carried` may be null.
    public static void stamp(NBTTagCompound root, NBTTagCompound carried) {
        if (POSTEA) Impl.stamp(root, carried);
    }

    /// A copy of the stamps on a root read from disk, or null. Call after [#transform].
    public static NBTTagCompound savedStamps(NBTTagCompound root) {
        return POSTEA ? Impl.savedStamps(root) : null;
    }

    private static final class Impl {

        static void transform(String storageId, NBTTagCompound root) {
            CustomDataReplacementManager.transform(storageId, root);
        }

        static void stamp(NBTTagCompound root, NBTTagCompound carried) {
            CustomDataReplacementManager.stamp(root, carried);
        }

        static NBTTagCompound savedStamps(NBTTagCompound root) {
            return CustomDataReplacementManager.savedStamps(root);
        }
    }
}
