package bq_standard.handlers;

import bq_standard.core.BQS_Settings;
import bq_standard.core.BQ_Standard;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.Level;

public class ConfigHandler {
    public static Configuration config;

    public static void initConfigs() {
        if (config == null) {
            BQ_Standard.logger.log(Level.ERROR, "Config attempted to be loaded before it was initialised!");
            return;
        }

        config.load();

        BQS_Settings.hideUpdates =
                config.getBoolean("Hide Updates", Configuration.CATEGORY_GENERAL, false, "Hide update notifications");

        config.save();

        BQ_Standard.logger.log(Level.INFO, "Loaded configs...");
    }
}
