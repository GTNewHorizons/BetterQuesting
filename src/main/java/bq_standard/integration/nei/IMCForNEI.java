package bq_standard.integration.nei;

import bq_standard.core.BQ_Standard;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.nbt.NBTTagCompound;

public class IMCForNEI {
    public static void IMCSender() {
        if (Loader.isModLoaded("questbook")) {
            sendHandler("bq_quest", "questbook:ItemQuestBook");
            sendCatalyst("bq_quest", "questbook:ItemQuestBook");
        } else {
            sendHandler("bq_quest", "betterquesting:submit_station");
        }
        sendCatalyst("bq_quest", "betterquesting:submit_station");
        sendCatalyst("bq_quest", "betterquesting:observation_station");
    }

    private static void sendHandler(String name, String itemStack) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("handler", name);
        nbt.setString("modName", BQ_Standard.NAME);
        nbt.setString("modId", BQ_Standard.MODID);
        nbt.setBoolean("modRequired", true);
        nbt.setString("itemName", itemStack);
        nbt.setInteger("handlerHeight", 105);
        nbt.setInteger("handlerWidth", 166);
        nbt.setInteger("maxRecipesPerPage", 3);
        nbt.setInteger("yShift", 0);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", nbt);
    }

    private static void sendCatalyst(String name, String itemStack, int priority) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("handlerID", name);
        nbt.setString("itemName", itemStack);
        nbt.setInteger("priority", priority);
        FMLInterModComms.sendMessage("NotEnoughItems", "registerCatalystInfo", nbt);
    }

    private static void sendCatalyst(String name, String itemStack) {
        sendCatalyst(name, itemStack, 0);
    }
}
