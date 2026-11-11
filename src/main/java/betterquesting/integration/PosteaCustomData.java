package betterquesting.integration;

import com.gtnewhorizons.postea.api.IVersionedTransformer;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.postea.api.CustomDataReplacementManager;

import cpw.mods.fml.common.Loader;

/// Used to provide custom save data (like the world's quest database) to Postea's [IVersionedTransformer]s.
///
/// These transformers allow other mods to change saved data based on their own version stamps. This compat flags,
/// to those transformers, the times they should fire. [#transform] is used before consuming data, so the transformer
/// can run, and [#stamp] is used for fresh data to ensure the transformer can add a stamp of its current version.
public final class PosteaCustomData {

    private static final boolean POSTEA = Loader.isModLoaded("postea");

    private PosteaCustomData() {}

    /// Transforms a root read from disk in place; call before consuming it.
    public static void transform(String storageId, NBTTagCompound root) {
        if (POSTEA) Impl.transform(storageId, root);
    }

    /// Stamps a freshly built root about to be written to disk.
    public static void stamp(NBTTagCompound root) {
        if (POSTEA) Impl.stamp(root);
    }

    private static final class Impl {

        static void transform(String storageId, NBTTagCompound root) {
            CustomDataReplacementManager.transform(storageId, root);
        }

        static void stamp(NBTTagCompound root) {
            CustomDataReplacementManager.stamp(root);
        }
    }
}
