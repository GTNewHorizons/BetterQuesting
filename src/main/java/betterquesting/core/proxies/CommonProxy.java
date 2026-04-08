package betterquesting.core.proxies;

import net.minecraft.command.ICommandManager;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.commands.BQ_CommandAdmin;
import betterquesting.commands.BQ_CommandDebug;
import betterquesting.commands.BQ_CommandUser;
import betterquesting.commands.BQ_CopyProgress;
import betterquesting.core.BetterQuesting;
import betterquesting.core.ExpansionLoader;
import betterquesting.handlers.EventHandler;
import betterquesting.handlers.GuiHandler;
import betterquesting.handlers.SaveLoadHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy {

    public boolean isClient() {
        return false;
    }

    public void registerHandlers() {
        ExpansionLoader.INSTANCE.initCommonAPIs();

        MinecraftForge.EVENT_BUS.register(EventHandler.INSTANCE);
        MinecraftForge.TERRAIN_GEN_BUS.register(EventHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(EventHandler.INSTANCE);

        NetworkRegistry.INSTANCE.registerGuiHandler(BetterQuesting.instance, new GuiHandler());
    }

    public void registerRenderers() {}

    public void serverStarting(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        ICommandManager command = server.getCommandManager();
        ServerCommandManager manager = (ServerCommandManager) command;

        manager.registerCommand(new BQ_CopyProgress());
        manager.registerCommand(new BQ_CommandAdmin());
        manager.registerCommand(new BQ_CommandUser());

        if ((Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) {
            manager.registerCommand(new BQ_CommandDebug());
        }

        SaveLoadHandler.INSTANCE.loadDatabases(server);
        if (BQ_Settings.loadDefaultsOnStartup) {
            try {
                manager.executeCommand(server, "/bq_admin default load");
            } catch (Exception e) {
                BetterQuesting.logger.error("Could not load the default quest database", e);
            }
        }
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        SaveLoadHandler.INSTANCE.unloadDatabases();
    }
}
