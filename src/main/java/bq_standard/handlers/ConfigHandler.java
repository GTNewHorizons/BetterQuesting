package bq_standard.handlers;

import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.Level;

import bq_standard.core.BQS_Settings;
import bq_standard.core.BQ_Standard;

public class ConfigHandler {

    public static final String CATEGORY_GENERAL = "general";

    public static Configuration config;

    public static void initConfigs() {
        if (config == null) {
            BQ_Standard.logger.log(Level.ERROR, "Config attempted to be loaded before it was initialised!");
            return;
        }

        config.load();

        BQS_Settings.hideUpdates = config
            .getBoolean("Hide Updates", CATEGORY_GENERAL, false, "Hide update notifications");

        config.save();

        BQ_Standard.logger.log(Level.INFO, "Loaded configs...");
    }
}
