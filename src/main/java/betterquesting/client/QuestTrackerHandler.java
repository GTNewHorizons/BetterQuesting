package betterquesting.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.apache.commons.io.FileUtils;

import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetQuestSync;
import cpw.mods.fml.common.FMLCommonHandler;

/**
 * Stores the single quest shown on the tracker HUD, per player per server.
 */
public class QuestTrackerHandler {

    private static final String TRACKER_DIR = BetterQuesting.MODID + "/tracked/";
    private static File trackerFile;
    private static UUID tracked = null;

    public static UUID getTracked() {
        return tracked;
    }

    public static boolean isTracked(UUID questId) {
        return tracked != null && tracked.equals(questId);
    }

    /** Tracks the quest, or untracks it if it was already the tracked one. Returns the new state. */
    public static boolean toggle(UUID questId) {
        boolean nowTracked = !isTracked(questId);
        tracked = nowTracked ? questId : null;
        save();
        // Progress of an untouched quest can be up to 10s stale on the client, so ask for it now
        if (nowTracked) NetQuestSync.requestSync(Collections.singletonList(questId), false, true);
        return nowTracked;
    }

    public static void clear() {
        if (tracked == null) return;
        tracked = null;
        save();
    }

    private static void save() {
        if (trackerFile == null) return;
        try {
            FileUtils.writeLines(trackerFile, Collections.singletonList(tracked == null ? "" : tracked.toString()));
        } catch (IOException ignored) {
            BetterQuesting.logger.warn("Failed to save tracked quest.");
        }
    }

    public static void load(String address) {
        String identifier = getIdentifier(address);
        trackerFile = new File(TRACKER_DIR, String.format("%s.txt", identifier));
        tracked = null;

        if (!trackerFile.exists()) return;

        try {
            List<String> lines = FileUtils.readLines(trackerFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim()
                    .isEmpty()) continue;
                tracked = UUID.fromString(line.trim());
                break;
            }
        } catch (IOException | IllegalArgumentException ignored) {
            BetterQuesting.logger.warn("Failed to load tracked quest for {}", identifier);
        }
    }

    private static String getIdentifier(String address) {
        if (Minecraft.getMinecraft()
            .isSingleplayer()) {
            return FMLCommonHandler.instance()
                .getMinecraftServerInstance()
                .getFolderName();
        }

        int index = address.indexOf("/") + 1;
        return address.substring(index)
            .replace(":", ".");
    }
}
