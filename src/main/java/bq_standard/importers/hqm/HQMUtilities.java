package bq_standard.importers.hqm;

import java.util.HashMap;

import net.minecraft.item.Item;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.logging.log4j.Level;

import com.google.gson.JsonObject;

import betterquesting.api.placeholders.PlaceholderConverter;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.core.BetterQuesting;
import bq_standard.importers.hqm.converters.items.HQMItem;
import bq_standard.importers.hqm.converters.items.HQMItemBag;
import bq_standard.importers.hqm.converters.items.HQMItemHeart;

public class HQMUtilities {

    /**
     * Get HQM formatted item, Type 1
     * Can return multiple stacks in the event the stack size exceeds 127
     */
    public static BigItemStack HQMStackT1(JsonObject json) {
        String id = JsonHelper.GetString(json, "id", "minecraft:stone");
        Item item = (Item) Item.itemRegistry.getObject(id);
        int amount = JsonHelper.GetNumber(json, "amount", 1)
            .intValue();
        int damage = JsonHelper.GetNumber(json, "damage", 0)
            .intValue();
        NBTTagCompound tags = getNbtTagCompound(json);

        HQMItem hqm = itemConverters.get(id.toLowerCase());
        if (hqm != null) return hqm.convertItem(damage, amount, tags);

        return PlaceholderConverter.convertItem(item, id, amount, damage, "", tags);
    }

    /**
     * Get HQM formatted item, Type 2.
     * Can return multiple stacks in the event the stack size exceeds 127
     */
    public static BigItemStack HQMStackT2(JsonObject rJson) {
        JsonObject json = JsonHelper.GetObject(rJson, "item");
        String id = JsonHelper.GetString(json, "id", "minecraft:stone");
        Item item = (Item) Item.itemRegistry.getObject(id);
        int amount = JsonHelper.GetNumber(rJson, "required", 1)
            .intValue();
        int damage = JsonHelper.GetNumber(json, "damage", 0)
            .intValue();
        boolean oreDict = JsonHelper.GetString(rJson, "precision", "")
            .equalsIgnoreCase("ORE_DICTIONARY");

        NBTTagCompound tags = getNbtTagCompound(json);

        HQMItem hqm = itemConverters.get(id.toLowerCase());
        if (hqm != null) return hqm.convertItem(damage, amount, tags);

        BigItemStack stack = PlaceholderConverter.convertItem(item, id, amount, damage, "", tags);

        if (oreDict && item != null) {
            int[] oreId = OreDictionary.getOreIDs(stack.getBaseStack());
            if (oreId.length > 0) stack.setOreDict(OreDictionary.getOreName(oreId[0]));
        }

        return stack;
    }

    public static FluidStack HQMStackT3(JsonObject json) {
        String name = JsonHelper.GetString(json, "fluid", "water");
        Fluid fluid = FluidRegistry.getFluid(name);
        int amount = JsonHelper.GetNumber(json, "required", 1000)
            .intValue();

        return PlaceholderConverter.convertFluid(fluid, name, amount, null);
    }

    private static NBTTagCompound getNbtTagCompound(JsonObject json) {
        if (!json.has("nbt")) return null;
        try {
            String nbt = json.get("nbt")
                .toString();
            nbt = nbt.replaceFirst("\"", ""); // Delete first quote
            nbt = nbt.substring(0, nbt.length() - 1); // Delete last quote
            nbt = nbt.replace(":\\\"", ":\""); // Fix start of strings
            nbt = nbt.replace("\\\",", "\","); // Fix middle of lists
            nbt = nbt.replace("\\\"}", "\"}"); // Fix end of strings
            nbt = nbt.replace("\\\"]", "\"]"); // Fix end of lists
            nbt = nbt.replace("[\\\"", "[\""); // Fix start of lists
            nbt = nbt.replace("\\n", "\n");
            NBTBase nbtBase = JsonToNBT.func_150315_a(nbt);
            if (nbtBase instanceof NBTTagCompound compound) {
                return compound;
            }
        } catch (Exception e) {
            BetterQuesting.logger.log(
                Level.ERROR,
                "Unable to convert HQM NBT data {}. This is likely a HQM Gson/Json formatting issue",
                json.get("nbt")
                    .toString(),
                e);
        }
        return null;
    }

    private static final HashMap<String, HQMItem> itemConverters = new HashMap<>();

    static {
        itemConverters.put("hardcorequesting:hearts", new HQMItemHeart());
        itemConverters.put("hardcorequesting:bags", new HQMItemBag());
    }
}
