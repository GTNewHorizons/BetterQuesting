package betterquesting.api.properties;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.Contract;

import betterquesting.api2.storage.INBTSaveLoad;

public interface IPropertyContainer extends INBTSaveLoad<NBTTagCompound> {

    <T> T getProperty(IPropertyType<T> prop);

    <T> T getProperty(IPropertyType<T> prop, T def);

    /// Similar to {@link #getProperty(IPropertyType, Object)}, except it returns the default if the property was set to
    /// null.
    /// @param prop The property queried.
    /// @param def Returned instead of null
    /// @return The value stored for the given property
    @Contract("_, !null -> !null")
    default <T> T getOrDefault(IPropertyType<T> prop, T def) {
        final T ret = getProperty(prop);
        if (ret != null) return ret;
        else return def;
    }

    boolean hasProperty(IPropertyType<?> prop);

    void removeProperty(IPropertyType<?> prop);

    <T> void setProperty(IPropertyType<T> prop, T value);

    void removeAllProps();
}
