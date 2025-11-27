package drethic.questbook.proxy;

import bq_standard.handlers.EventHandler;
import bq_standard.handlers.PlayerContainerListener;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import drethic.questbook.config.QBConfig;
import drethic.questbook.crafting.QBCrafting;
import drethic.questbook.events.FMLEventHandler;
import drethic.questbook.item.QBItems;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent e) {
        QBItems.init();
        QBCrafting.init();
        QBConfig.init(e);
    }

    public void init(FMLInitializationEvent e) {
        FMLCommonHandler.instance()
            .bus()
            .register(FMLEventHandler.INSTANCE);
    }

    public void postInit(FMLPostInitializationEvent e) {

    }

    public void onServerStopped(FMLServerStoppedEvent e) {
        EventHandler.cleanup();
        PlayerContainerListener.cleanup();
    }
}
