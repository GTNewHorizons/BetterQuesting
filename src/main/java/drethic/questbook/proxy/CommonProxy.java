package drethic.questbook.proxy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
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
        FMLCommonHandler.instance().bus().register(FMLEventHandler.INSTANCE);
    }

    public void postInit(FMLPostInitializationEvent e) {

    }
}