package bq_standard;

import betterquesting.api.utils.NBTConverter;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

public class NBTReplaceUtil {
    @SuppressWarnings("unchecked")
    public static <T extends NBTBase> T replaceStrings(T baseTag, String key, String replace) {
        if (baseTag == null) {
            return null;
        }

        if (baseTag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) baseTag;

            for (String k : (Set<String>) compound.func_150296_c()) {
                compound.setTag(k, replaceStrings(compound.getTag(k), key, replace));
            }
        } else if (baseTag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) baseTag;
            List<NBTBase> tList = NBTConverter.getTagList(list);

            for (int i = 0; i < tList.size(); i++) {
                tList.set(i, replaceStrings(tList.get(i), key, replace));
            }
        } else if (baseTag instanceof NBTTagString) {
            NBTTagString tString = (NBTTagString) baseTag;
            return (T) new NBTTagString(tString.func_150285_a_().replaceAll(key, replace));
        }

        return baseTag; // Either isn't a string or doesn't contain one
    }
}
