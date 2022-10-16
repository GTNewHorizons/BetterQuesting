package drethic.questbook;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import drethic.questbook.proxy.CommonProxy;

@Mod(modid = QuestBook.MODID, name = QuestBook.MODNAME, version = QuestBook.VERSION, dependencies = "required-after:betterquesting")
public class QuestBook {

    public static final String MODID = "questbook";
    public static final String MODNAME = "Better Questing Quest Book";
    public static final String VERSION = BetterQuesting.VERSION;

    @Instance("MODID")
    public static QuestBook instance = new QuestBook();

    @SidedProxy(clientSide = "drethic.questbook.proxy.ClientProxy", serverSide = "drethic.questbook.proxy.ServerProxy")
    public static CommonProxy proxy;

    @EventHandler
    public void init(FMLInitializationEvent e) {
        proxy.init(e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        proxy.postInit(e);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        proxy.preInit(e);
    }
}
