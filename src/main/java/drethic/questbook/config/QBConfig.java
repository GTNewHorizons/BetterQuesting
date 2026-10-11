package drethic.questbook.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import drethic.questbook.logger.QBLogger;

public class QBConfig {

    public static final String CATEGORY_GENERAL = "general";

    public static Configuration qbconfig;
    public static QBConfig instance;

    public static boolean spawnWithBook;
    public static boolean syncQuestRewards;

    public static final void init(FMLPreInitializationEvent e) {
        QBLogger.logger.info("Loading config file.");
        qbconfig = new Configuration(
            new File(
                e.getModConfigurationDirectory()
                    .getAbsolutePath() + "/betterquesting/questbook.cfg"));
        qbconfig.load();

        qbconfig.addCustomCategoryComment(CATEGORY_GENERAL, "General options for the Quest Book mod.");

        spawnWithBook = configBoolOption(
            CATEGORY_GENERAL,
            "spawnWithBook",
            false,
            "Enable this option so new players will spawn with a Quest Book.  Default: false");
        syncQuestRewards = configBoolOption(
            CATEGORY_GENERAL,
            "syncQuestRewards",
            false,
            "Quest rewards can only be claimed once per party.  Default: false");

        qbconfig.save();
    }

    public static boolean configBoolOption(String section, String name, boolean defaultValue, String comment) {
        Property spawnWithBookProperty = qbconfig.get(section, name, defaultValue);
        spawnWithBookProperty.comment = comment;
        return spawnWithBookProperty.getBoolean(defaultValue);
    }
}
