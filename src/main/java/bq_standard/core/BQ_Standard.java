package bq_standard.core;

import betterquesting.core.BetterQuesting;
import bq_standard.commands.BQS_Commands;
import bq_standard.core.proxies.CommonProxy;
import bq_standard.handlers.ConfigHandler;
import bq_standard.handlers.GuiHandler;
import bq_standard.handlers.LootSaveLoad;
import bq_standard.items.ItemLootChest;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = BQ_Standard.MODID,
        name = BQ_Standard.NAME,
        version = BQ_Standard.VERSION,
        dependencies = "required-after:betterquesting",
        guiFactory = "bq_standard.handlers.ConfigGuiFactory")
public class BQ_Standard {
    public static final String MODID = "bq_standard";
    public static final String NAME = "Standard Expansion";
    public static final String VERSION = BetterQuesting.VERSION;
    public static final String PROXY = "bq_standard.core.proxies";
    public static final String CHANNEL = "BQ_STANDARD";

    public static boolean hasNEI = false;

    @Instance(MODID)
    public static BQ_Standard instance;

    @SidedProxy(clientSide = PROXY + ".ClientProxy", serverSide = PROXY + ".CommonProxy")
    public static CommonProxy proxy;

    public SimpleNetworkWrapper network;
    public static Logger logger;

    public static Item lootChest = new ItemLootChest();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);

        ConfigHandler.config = new Configuration(event.getSuggestedConfigurationFile(), true);
        ConfigHandler.initConfigs();

        proxy.registerHandlers();

        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        GameRegistry.registerItem(lootChest, "loot_chest");

        proxy.registerRenderers();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("betterquesting")) {
            proxy.registerExpansion();
        }

        hasNEI = Loader.isModLoaded("NotEnoughItems");
    }

    @EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        ICommandManager command = server.getCommandManager();
        ServerCommandManager manager = (ServerCommandManager) command;

        manager.registerCommand(new BQS_Commands());

        LootSaveLoad.INSTANCE.LoadLoot(event.getServer());
    }

    @EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        LootSaveLoad.INSTANCE.UnloadLoot();
    }
}
