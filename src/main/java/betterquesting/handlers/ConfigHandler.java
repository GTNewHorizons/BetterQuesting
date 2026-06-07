package betterquesting.handlers;

import net.minecraftforge.common.config.Configuration;

import org.apache.logging.log4j.Level;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.core.BetterQuesting;

public class ConfigHandler {

    public static Configuration config;

    public static void initConfigs() {
        if (config == null) {
            BetterQuesting.logger.log(Level.ERROR, "Config attempted to be loaded before it was initialised!");
            return;
        }

        config.load();

        BQ_Settings.questNotices = config.getBoolean(
            "Quest Notices",
            Configuration.CATEGORY_GENERAL,
            true,
            "Enabled the popup notices when quests are completed or updated");

        String style = config.getString(
            "Notification Style",
            Configuration.CATEGORY_GENERAL,
            "title",
            "How quest notifications display: 'title' (large centered text), 'classic' (small popup with icon), 'off' (disabled)");
        if (!BQ_Settings.questNotices) {
            BQ_Settings.notificationStyle = "off";
        } else {
            BQ_Settings.notificationStyle = style;
        }
        BQ_Settings.showNotificationIcon = config.getBoolean(
            "Show Notification Icon",
            Configuration.CATEGORY_GENERAL,
            true,
            "Show the quest item icon with notifications");
        BQ_Settings.notificationDuration = config.getFloat(
            "Notification Duration",
            Configuration.CATEGORY_GENERAL,
            4.5f,
            1.0f,
            15.0f,
            "How long quest notifications stay on screen (seconds)");
        BQ_Settings.notificationFadeIn = config.getFloat(
            "Notification Fade In",
            Configuration.CATEGORY_GENERAL,
            0.5f,
            0.0f,
            5.0f,
            "How quickly the notification appears (seconds). 0 = instant");
        BQ_Settings.notificationFadeOut = config.getFloat(
            "Notification Fade Out",
            Configuration.CATEGORY_GENERAL,
            1.0f,
            0.0f,
            5.0f,
            "How quickly the notification disappears (seconds). 0 = instant");
        BQ_Settings.notificationIconScale = config.getFloat(
            "Notification Icon Scale",
            Configuration.CATEGORY_GENERAL,
            4.0f,
            0.5f,
            8.0f,
            "Size of the quest icon in title notifications (default 4.0 = 64x64 pixels)");
        BQ_Settings.notificationIconOffsetY = config.getInt(
            "Notification Icon Offset Y",
            Configuration.CATEGORY_GENERAL,
            -25,
            -100,
            100,
            "Vertical offset for the quest icon in pixels. Negative = higher, positive = lower.");
        BQ_Settings.notificationTitleScale = config.getFloat(
            "Notification Title Scale",
            Configuration.CATEGORY_GENERAL,
            0f,
            0f,
            8.0f,
            "Size of the title text in notifications (0 = use global GTNHLib setting)");
        BQ_Settings.notificationSubtitleScale = config.getFloat(
            "Notification Subtitle Scale",
            Configuration.CATEGORY_GENERAL,
            0f,
            0f,
            8.0f,
            "Size of the subtitle text in notifications (0 = use global GTNHLib setting)");
        BQ_Settings.notificationIconAnimation = config.getString(
            "Notification Icon Animation",
            Configuration.CATEGORY_GENERAL,
            "none",
            "Icon animation style during fade-in: none, fly_in, spin");
        BQ_Settings.notificationParticle = config.getString(
            "Notification Particle",
            Configuration.CATEGORY_GENERAL,
            "none",
            "Particle effect on quest completion: none, confetti, sparkle, firework, item_confetti");
        BQ_Settings.curTheme = config
            .getString("Theme", Configuration.CATEGORY_GENERAL, "betterquesting:light", "The current questing theme");
        BQ_Settings.useBookmark = config.getBoolean(
            "Use Quest Bookmark",
            Configuration.CATEGORY_GENERAL,
            true,
            "Jumps the user to the last opened quest");
        BQ_Settings.guiWidth = config.getInt(
            "Max GUI Width",
            Configuration.CATEGORY_GENERAL,
            -1,
            -1,
            Integer.MAX_VALUE,
            "Clamps the max UI width (-1 to disable)");
        BQ_Settings.guiHeight = config.getInt(
            "Max GUI Height",
            Configuration.CATEGORY_GENERAL,
            -1,
            -1,
            Integer.MAX_VALUE,
            "Clamps the max UI height (-1 to disable)");
        BQ_Settings.textWidthCorrection = config.getFloat(
            "Text Width Correction",
            Configuration.CATEGORY_GENERAL,
            1F,
            0.01F,
            10.0F,
            "Correcting the width of split text");

        BQ_Settings.scrollMultiplier = config
            .getFloat("Scroll multiplier", Configuration.CATEGORY_GENERAL, 1F, 0F, 10F, "Scrolling multiplier");

        BQ_Settings.zoomSpeed = config
            .getFloat("Zoom Speed", Configuration.CATEGORY_GENERAL, 1.25F, 1.05F, 3F, "Zoom Speed");

        BQ_Settings.zoomTimeInMs = config.getFloat(
            "Zoom smoothness in ms",
            Configuration.CATEGORY_GENERAL,
            100F,
            0F,
            2000F,
            "Zoom smoothness in ms");

        BQ_Settings.zoomInToCursor = config.getBoolean(
            "Zoom in on cursor",
            Configuration.CATEGORY_GENERAL,
            true,
            "Zoom in on cursor. If false, zooms in on center of screen.");
        BQ_Settings.zoomOutToCursor = config.getBoolean(
            "Zoom out on cursor",
            Configuration.CATEGORY_GENERAL,
            true,
            "Zoom out on cursor. If false, zooms out on center of screen.");

        BQ_Settings.claimAllConfirmation = config.getBoolean(
            "Claim all requires confirmation",
            Configuration.CATEGORY_GENERAL,
            true,
            "If true, then when you click on Claim all, a warning dialog will be displayed");

        BQ_Settings.claimAllRandomChoice = config.getBoolean(
            "Claim all random select choice rewards",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, then when you Claim all quests, choice rewards will be randomly selected");

        BQ_Settings.skipHome = config.getBoolean(
            "Skip home",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true will skip home gui and open quests at startup. This property will be changed by the mod itself.");

        BQ_Settings.lockTray = config.getBoolean(
            "Lock tray",
            Configuration.CATEGORY_GENERAL,
            false,
            "Is quest chapters list locked and opened on start.");
        BQ_Settings.viewMode = config.getBoolean(
            "View mode",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, user can view not-yet-unlocked quests that are not hidden or secret. This property can be changed by the GUI.");
        BQ_Settings.viewModeAllQuestLine = config.getBoolean(
            "View mode all quest line",
            Configuration.CATEGORY_GENERAL,
            true,
            "If true, view mode will display the quest line regardless of whether the quest line is unlocked yet.");
        BQ_Settings.viewModeBtn = config
            .getBoolean("View mode button", Configuration.CATEGORY_GENERAL, false, "If true, show view mode button.");
        BQ_Settings.alwaysDrawImplicit = config.getBoolean(
            "Always draw implicit dependency",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, always draw implicit dependency. This property can be changed by the GUI");
        BQ_Settings.showDependencyArrows = config.getBoolean(
            "Show dependency arrows",
            Configuration.CATEGORY_GENERAL,
            true,
            "If true, quest dependency lines will render directional arrows. This property can be changed by the GUI.");
        BQ_Settings.animateDependencyArrows = config.getBoolean(
            "Animate dependency arrows",
            Configuration.CATEGORY_GENERAL,
            true,
            "If true, directional arrows on quest dependency lines will animate on quest hover.");
        BQ_Settings.forceMonochromeText = config.getBoolean(
            "Force Monochrome Text",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, quest titles and text content will show no color. This property can be changed by the GUI.");
        BQ_Settings.urlDebug = config.getBoolean(
            "Highlight detected clickable url hotzone.",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, render each hotzone using alternating color.");
        BQ_Settings.loadDefaultsOnStartup = config.getBoolean(
            "Load the default quest DB on world startup.",
            Configuration.CATEGORY_GENERAL,
            true,
            "Does an equivalent of '/bq_admin default load' on every world load");
        BQ_Settings.unrestrictAdminCommands = config.getBoolean(
            "Unrestrict Admin Commands",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, all users can use /bq_admin commands regardless of op-status. Useful for single-player without cheats.");
        BQ_Settings.logNullQuests = config.getBoolean(
            "Log null quests",
            Configuration.CATEGORY_GENERAL,
            true,
            "Posts useful information in the log when encountering a null quest during loading.");

        BQ_Settings.noRewards = config.getBoolean(
            "Disable rewards",
            Configuration.CATEGORY_GENERAL,
            false,
            "If true, rewards will be disabled. This might not be supported by reward types.");
        config.save();
    }
}
