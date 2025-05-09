package betterquesting.api2.client.gui.themes.gui_args;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTBase;

import betterquesting.api.misc.ICallback;
import betterquesting.api.nbt_doc.INbtDoc;

public class GArgsNBT<T extends NBTBase> extends GArgsCallback<T> {

    public final INbtDoc doc;

    public GArgsNBT(@Nullable GuiScreen parent, T nbt, ICallback<T> callback, INbtDoc doc) {
        super(parent, nbt, callback);
        this.doc = doc;
    }
}
