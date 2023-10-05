package bq_standard.handlers;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.loot.LootRegistry;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import java.io.File;

public class LootSaveLoad {
    public static LootSaveLoad INSTANCE = new LootSaveLoad();

    public File worldDir;

    public void LoadLoot(MinecraftServer server) {
        if (BQ_Standard.proxy.isClient()) {
            worldDir = server.getFile("saves/" + server.getFolderName());
        } else {
            worldDir = server.getFile(server.getFolderName());
        }

        File f1 = new File(worldDir, "QuestLoot.json");
        JsonObject j1 = new JsonObject();

        if (f1.exists()) {
            j1 = JsonHelper.ReadFromFile(f1);
        } else {
            f1 = server.getFile("config/betterquesting/DefaultLoot.json");

            if (f1.exists()) {
                j1 = JsonHelper.ReadFromFile(f1);
            }
        }

        LootRegistry.INSTANCE.readFromNBT(NBTConverter.JSONtoNBT_Object(j1, new NBTTagCompound(), true), false);
    }

    public void SaveLoot() {
        JsonHelper.WriteToFile(
                new File(worldDir, "QuestLoot.json"),
                NBTConverter.NBTtoJSON_Compound(
                        LootRegistry.INSTANCE.writeToNBT(new NBTTagCompound(), null), new JsonObject(), true));
    }

    public void UnloadLoot() {
        LootRegistry.INSTANCE.reset();
        LootRegistry.INSTANCE.updateUI = false;
        worldDir = null;
    }
}
