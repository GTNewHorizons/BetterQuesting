package betterquesting.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;

import org.apache.commons.io.FileUtils;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.common.FMLCommonHandler;

public class BookmarkHandler {

    private static final List<String> BOOKMARKS = new ArrayList<>();
    private static final List<String> CHAPTER_BOOKMARKS = new ArrayList<>();
    private static final String BOOKMARK_DIR = BetterQuesting.MODID + "/bookmarks/";
    private static File bookmarkFile;
    private static File chapterBookmarkFile;
    private static boolean hasChanged = true;

    public static boolean bookmarkQuest(UUID questId) {
        String uuid = questId.toString();
        boolean added = false;
        if (BOOKMARKS.contains(uuid)) {
            BOOKMARKS.remove(uuid);
        } else {
            BOOKMARKS.add(uuid);
            added = true;
        }

        hasChanged = true;
        saveBookmarks();
        return added;
    }

    public static boolean bookmarkChapter(UUID chapterId) {
        String uuid = chapterId.toString();
        boolean added = false;
        if (CHAPTER_BOOKMARKS.contains(uuid)) {
            CHAPTER_BOOKMARKS.remove(uuid);
        } else {
            CHAPTER_BOOKMARKS.add(uuid);
            added = true;
        }

        saveChapterBookmarks();
        return added;
    }

    public static boolean isChapterBookmarked(UUID chapterId) {
        return CHAPTER_BOOKMARKS.contains(chapterId.toString());
    }

    public static List<UUID> getBookmarkedQuestIds() {
        return parseIds(BOOKMARKS);
    }

    public static List<UUID> getBookmarkedChapterIds() {
        return parseIds(CHAPTER_BOOKMARKS);
    }

    public static boolean hasAnyBookmarks() {
        return !BOOKMARKS.isEmpty() || !CHAPTER_BOOKMARKS.isEmpty();
    }

    public static int getIndexOf(UUID questId) {
        return BOOKMARKS.indexOf(questId.toString());
    }

    public static boolean isBookmarked(UUID questId) {
        return BOOKMARKS.contains(questId.toString());
    }

    public static boolean hasChanged() {
        if (hasChanged) {
            hasChanged = false;
            return true;
        }
        return false;
    }

    private static void saveBookmarks() {
        try {
            FileUtils.writeLines(bookmarkFile, BOOKMARKS);
        } catch (IOException ignored) {
            BetterQuesting.logger.warn("Failed to save bookmarks.");
        }
    }

    private static void saveChapterBookmarks() {
        try {
            FileUtils.writeLines(chapterBookmarkFile, CHAPTER_BOOKMARKS);
        } catch (IOException ignored) {
            BetterQuesting.logger.warn("Failed to save chapter bookmarks.");
        }
    }

    private static List<UUID> parseIds(List<String> ids) {
        List<UUID> parsed = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                parsed.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                BetterQuesting.logger.warn("Ignoring invalid bookmark id {}", id);
            }
        }
        return parsed;
    }

    public static void loadBookmarks(String address) {
        String identifier = getIdentifier(address);
        bookmarkFile = new File(BOOKMARK_DIR, String.format("%s.txt", identifier));
        chapterBookmarkFile = new File(BOOKMARK_DIR, String.format("%s.chapters.txt", identifier));
        BOOKMARKS.clear();
        CHAPTER_BOOKMARKS.clear();
        hasChanged = true;

        readLines(bookmarkFile, BOOKMARKS, identifier);
        readLines(chapterBookmarkFile, CHAPTER_BOOKMARKS, identifier);
    }

    private static void readLines(File file, List<String> target, String identifier) {
        if (!file.exists()) return;

        try {
            target.addAll(FileUtils.readLines(file, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            BetterQuesting.logger.warn("Failed to load bookmarks for {}", identifier);
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
