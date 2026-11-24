package bq_standard.handlers;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.google.gson.JsonObject;

import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.integration.PosteaCustomData;
import bq_standard.core.BQ_Standard;
import bq_standard.rewards.loot.LootRegistry;

public class LootSaveLoad {

    public static LootSaveLoad INSTANCE = new LootSaveLoad();

    /// The directory of the world whose loot has loaded, or null. The loot is saved only while it is set.
    public File worldDir;

    private NBTTagCompound savedStamps;

    public void LoadLoot(MinecraftServer server) {
        worldDir = null;
        File dir;
        if (BQ_Standard.proxy.isClient()) {
            dir = server.getFile("saves/" + server.getFolderName());
        } else {
            dir = server.getFile(server.getFolderName());
        }

        File f1 = new File(dir, "QuestLoot.json");
        JsonObject j1 = new JsonObject();
        boolean worldData = f1.exists();

        if (worldData) {
            j1 = JsonHelper.ReadFromFile(f1);
        } else {
            f1 = server.getFile("config/betterquesting/DefaultLoot.json");

            if (f1.exists()) {
                j1 = JsonHelper.ReadFromFile(f1);
            }
        }

        NBTTagCompound root = NBTConverter.JSONtoNBT_Object(j1, new NBTTagCompound(), true);
        if (worldData) {
            PosteaCustomData.transform("bq_standard:questLoot", root);
        }
        savedStamps = worldData ? PosteaCustomData.savedStamps(root) : null;

        LootRegistry.INSTANCE.readFromNBT(root, false);
        worldDir = dir;
    }

    public void SaveLoot() {
        NBTTagCompound root = LootRegistry.INSTANCE.writeToNBT(new NBTTagCompound(), null);
        PosteaCustomData.stamp(root, savedStamps);
        JsonHelper.WriteToFile(
            new File(worldDir, "QuestLoot.json"),
            NBTConverter.NBTtoJSON_Compound(root, new JsonObject(), true));
    }

    /// Drops the Postea stamps read with the world's loot. Call when the loot is replaced from another source.
    public void forgetSavedStamps() {
        savedStamps = null;
    }

    public void UnloadLoot() {
        LootRegistry.INSTANCE.reset();
        LootRegistry.INSTANCE.updateUI = false;
        worldDir = null;
        savedStamps = null;
    }
}
