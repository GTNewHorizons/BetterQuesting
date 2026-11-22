package betterquesting.commands.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.Constants;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.gson.JsonObject;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.api2.utils.TextFormattingUtils;
import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.storage.DefaultQuestArchive;
import betterquesting.storage.QuestSettings;
import bq_standard.integration.vendingmachine.VmAdapter;

public class QuestCommandDefaults extends QuestCommandBase {

    public static final String DEFAULT_FILE = "DefaultQuests";
    public static final String LANG_FILE = "template.lang";
    public static final String SETTINGS_FILE = "QuestSettings.json";
    public static final String QUEST_LINE_DIR = "QuestLines";
    public static final String QUEST_LINE_FILE = "QuestLine.json";
    public static final String QUEST_LINE_ORDER_FILE = "QuestLinesOrder.txt";
    public static final String QUEST_DIR = "Quests";
    public static final String MULTI_QUEST_LINE_DIRECTORY = "MultipleQuestLine";
    public static final String NO_QUEST_LINE_DIRECTORY = "NoQuestLine";
    private static final String JSON_LOAD_ARGUMENT = "json";
    /**
     * This limit applies to the "name" portion of file names, but UUID will be appended, so the
     * actual file name will be longer.
     */
    public static final int FILE_NAME_MAX_LENGTH = 16;

    @Override
    public String getUsageSuffix() {
        return "[save|load|set|exportlang] [file_name|json (load only)]";
    }

    @Override
    public boolean validArgs(String[] args) {
        return args.length == 2 || args.length == 3;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> autoComplete(MinecraftServer server, ICommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 2) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "save", "load", "set", "exportlang");
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("load")) {
                return CommandBase.getListOfStringsMatchingLastWord(args, DEFAULT_FILE, JSON_LOAD_ARGUMENT);
            }
            list.add(DEFAULT_FILE);
        }

        return list;
    }

    @Override
    public String getCommand() {
        return "default";
    }

    @Override
    public void runCommand(MinecraftServer server, CommandBase command, ICommandSender sender, String[] args) {
        boolean loadJson = args.length == 3 && args[1].equalsIgnoreCase("load")
            && args[2].equalsIgnoreCase(JSON_LOAD_ARGUMENT);
        String databaseName;
        File dataDir;
        File legacyFile;
        if (args.length == 3 && !loadJson && !args[2].equalsIgnoreCase(DEFAULT_FILE)) {
            databaseName = args[2];
            dataDir = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2]);
            legacyFile = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2] + ".json");
        } else {
            databaseName = null;
            dataDir = defaultDirectory();
            legacyFile = defaultJsonFile();
        }

        if (args[1].equalsIgnoreCase("save")) {
            save(sender, databaseName, dataDir);

        } else if (args[1].equalsIgnoreCase("load")) {
            if (databaseName == null) {
                boolean loaded = loadJson ? loadDefaultJson(sender, false) : loadDefaults(sender, false);
                if (loaded) {
                    SaveLoadHandler.INSTANCE.resetUpdate();
                }
            } else if (!dataDir.exists() && legacyFile.exists()) {
                loadLegacy(sender, databaseName, legacyFile, false);
            } else {
                load(sender, databaseName, dataDir, false);
            }

        } else if (args[1].equalsIgnoreCase("set") && args.length == 3) {
            set(sender, databaseName, dataDir);

        } else if (args[1].equalsIgnoreCase("exportlang")) {
            exportLang(sender, databaseName, dataDir);

        } else {
            throw getException(command);
        }
    }

    /** Helper method that handles having null sender. */
    private static void sendChatMessage(@Nullable ICommandSender sender, String translationKey, Object... args) {
        if (sender == null) {
            return;
        }
        sender.addChatMessage(new ChatComponentTranslation(translationKey, args));
    }

    public static boolean loadDefaults(@Nullable ICommandSender sender, boolean loadWorldSettings) {
        File archive = defaultArchive();
        if (!archive.isFile()) {
            return loadDefaultJson(sender, loadWorldSettings);
        }
        sendChatMessage(sender, "betterquesting.cmd.default.loadarchive");
        try {
            applyDatabase(sender, null, DefaultQuestArchive.read(archive.toPath()), loadWorldSettings);
            return true;
        } catch (IOException failure) {
            QuestingAPI.getLogger()
                .error("Failed to load default quests from {}", archive, failure);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return false;
        }
    }

    private static boolean loadDefaultJson(@Nullable ICommandSender sender, boolean loadWorldSettings) {
        File directory = defaultDirectory();
        if (directory.isDirectory()) {
            return loadDirectory(sender, null, directory, loadWorldSettings);
        }
        File legacyFile = defaultJsonFile();
        if (!legacyFile.isFile()) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return false;
        }
        loadLegacy(sender, null, legacyFile, loadWorldSettings);
        return true;
    }

    public static NBTTagCompound readDefaultSettings() throws IOException {
        File archive = defaultArchive();
        if (archive.isFile()) {
            return DefaultQuestArchive.readSettings(archive.toPath());
        }
        File settings = new File(defaultDirectory(), SETTINGS_FILE);
        if (settings.isFile()) {
            return readDefinition(settings);
        }
        return readDefinition(defaultJsonFile()).getCompoundTag("questSettings");
    }

    private static File defaultDirectory() {
        return new File(BQ_Settings.defaultDir, DEFAULT_FILE);
    }

    private static File defaultJsonFile() {
        return new File(BQ_Settings.defaultDir, DEFAULT_FILE + ".json");
    }

    private static File defaultArchive() {
        return new File(BQ_Settings.defaultDir, DefaultQuestArchive.FILE_NAME);
    }

    private static void writeDefinition(File target, NBTTagCompound tag, @Nullable List<Future<Void>> writes) {
        Future<Void> write = JsonHelper
            .WriteToFile2(target, NBTConverter.NBTtoJSON_Compound(tag, new JsonObject(), true));
        if (writes != null) writes.add(write);
    }

    private static boolean awaitDefinitionWrites(@Nullable ICommandSender sender, List<Future<Void>> writes) {
        try {
            for (Future<Void> write : writes) {
                write.get();
            }
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread()
                .interrupt();
            QuestingAPI.getLogger()
                .error("Writing quest definitions was interrupted", failure);
        } catch (ExecutionException failure) {
            QuestingAPI.getLogger()
                .error("Writing quest definitions failed", failure);
        }
        sendChatMessage(sender, "betterquesting.cmd.error");
        return false;
    }

    private static boolean refreshDefaultArchive(@Nullable ICommandSender sender, NBTTagCompound database) {
        try {
            DefaultQuestArchive.write(defaultArchive().toPath(), database);
            return true;
        } catch (IOException failure) {
            QuestingAPI.getLogger()
                .error("Failed to write the default quest archive", failure);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return false;
        }
    }

    private static NBTTagCompound readDefinition(File file) {
        return NBTConverter.JSONtoNBT_Object(JsonHelper.ReadFromFile(file), new NBTTagCompound(), true);
    }

    private static boolean rejectReservedBackupName(@Nullable ICommandSender sender, @Nullable String databaseName) {
        if (!JSON_LOAD_ARGUMENT.equalsIgnoreCase(databaseName)) return false;
        sendChatMessage(sender, "betterquesting.cmd.default.reservedname");
        return true;
    }

    public static void save(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir) {
        if (rejectReservedBackupName(sender, databaseName)) return;
        boolean updateArchive = databaseName == null && defaultArchive().isFile();
        List<Future<Void>> writes = updateArchive ? new ArrayList<>() : null;
        // Remove chat formatting, as well as simplifying names for use in file paths.
        BiFunction<String, UUID, String> buildFileName = (name, id) -> {
            String formattedName = removeChatFormatting(name).replaceAll("[^a-zA-Z0-9]", "");

            if (formattedName.length() > FILE_NAME_MAX_LENGTH) {
                formattedName = formattedName.substring(0, FILE_NAME_MAX_LENGTH);
            }

            return String.format("%s-%s", formattedName, UuidConverter.encodeUuid(id));
        };

        File settingsFile = new File(dataDir, SETTINGS_FILE);
        if (dataDir.exists()) {
            if (!settingsFile.exists()) {
                // This might not be a BetterQuesting database; we should be careful.
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Directory exists, but isn't a database\n{}", dataDir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            try {
                FileUtils.deleteDirectory(dataDir);
            } catch (IOException e) {
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Failed to delete directory\n" + dataDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }
        if (!dataDir.mkdirs()) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create directory\n{}", dataDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        // Don't write editmode to json
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        NBTTagCompound settingsTag = QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound());
        settingsTag.setString("format", BetterQuesting.FORMAT);
        writeDefinition(settingsFile, settingsTag, writes);
        // And restore back
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);

        File questDir = new File(dataDir, QUEST_DIR);
        if (!questDir.exists() && !questDir.mkdirs()) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create directories\n{}", questDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        if (!questLineDir.exists() && !questLineDir.mkdirs()) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create directories\n{}", questLineDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        ListMultimap<UUID, IQuestLine> questToQuestLineMultimap = MultimapBuilder.hashKeys()
            .arrayListValues()
            .build();
        List<String> questLineOrderLines = new ArrayList<>(QuestLineDatabase.INSTANCE.size());

        for (Map.Entry<UUID, IQuestLine> entry : QuestLineDatabase.INSTANCE.getOrderedEntries()) {
            UUID questLineId = entry.getKey();
            IQuestLine questLine = entry.getValue();
            questLine.keySet()
                .forEach(key -> questToQuestLineMultimap.put(key, questLine));

            String questLineName = questLine.getProperty(NativeProps.NAME);
            questLineOrderLines.add(
                String.format("%s: %s", UuidConverter.encodeUuid(questLineId), removeChatFormatting(questLineName)));

            File questLineSubdir = new File(questLineDir, buildFileName.apply(questLineName, questLineId));
            if (!questLineSubdir.exists() && !questLineSubdir.mkdirs()) {
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Failed to create directories\n{}", questLineSubdir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            File questLineFile = new File(questLineSubdir, QUEST_LINE_FILE);
            NBTTagCompound questLineTag = questLine.writeToNBT(new NBTTagCompound(), true);
            NBTConverter.UuidValueType.QUEST_LINE.writeId(questLineId, questLineTag);
            writeDefinition(questLineFile, questLineTag, writes);

            for (Map.Entry<UUID, IQuestLineEntry> questLineEntry : questLine.entrySet()) {
                // Unfortunately, we must prepend the quest name to the filename.
                // This is because Windows treats filenames which differ only in casing, as the same
                // file. This causes problems for us as many UUIDs differ only in casing, so we must
                // disambiguate using the quest name as a prefix.
                UUID questId = questLineEntry.getKey();
                String questName = QuestDatabase.INSTANCE.get(questId)
                    .getProperty(NativeProps.NAME);

                File questLineEntryFile = new File(questLineSubdir, buildFileName.apply(questName, questId) + ".json");
                NBTTagCompound questLineEntryTag = questLineEntry.getValue()
                    .writeToNBT(new NBTTagCompound());
                NBTConverter.UuidValueType.QUEST.writeId(questId, questLineEntryTag);
                writeDefinition(questLineEntryFile, questLineEntryTag, writes);
            }
        }

        File questLineOrderFile = new File(dataDir, QUEST_LINE_ORDER_FILE);
        try {
            Files.write(questLineOrderFile.toPath(), questLineOrderLines, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create file\n" + questLineOrderFile, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        for (Map.Entry<UUID, IQuest> entry : QuestDatabase.INSTANCE.entrySet()) {
            UUID questId = entry.getKey();
            IQuest quest = entry.getValue();
            List<IQuestLine> questLines = questToQuestLineMultimap.get(questId);

            File questSubdir;
            switch (questLines.size()) {
                case 0:
                    questSubdir = new File(questDir, NO_QUEST_LINE_DIRECTORY);
                    break;

                case 1:
                    IQuestLine questLine = questLines.get(0);
                    UUID questLineId = QuestLineDatabase.INSTANCE.lookupKey(questLine);
                    String questLineName = questLine.getProperty(NativeProps.NAME);
                    questSubdir = new File(questDir, buildFileName.apply(questLineName, questLineId));
                    break;

                default:
                    questSubdir = new File(questDir, MULTI_QUEST_LINE_DIRECTORY);
                    break;
            }

            if (!questSubdir.exists() && !questSubdir.mkdirs()) {
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Failed to create directories\n{}", questSubdir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
            String questName = quest.getProperty(NativeProps.NAME);
            File questFile = new File(questSubdir, buildFileName.apply(questName, questId) + ".json");

            NBTTagCompound questTag = quest.writeToNBT(new NBTTagCompound());
            NBTConverter.UuidValueType.QUEST.writeId(questId, questTag);
            writeDefinition(questFile, questTag, writes);
        }

        if (writes != null && (!awaitDefinitionWrites(sender, writes) || !refreshDefaultArchive(sender, snapshot()))) {
            return;
        }
        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.save2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.save");
        }
    }

    public static void load(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir,
        boolean loadWorldSettings) {
        loadDirectory(sender, databaseName, dataDir, loadWorldSettings);
    }

    private static NBTTagCompound snapshot() {
        NBTTagCompound database = new NBTTagCompound();
        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        try {
            database.setTag("questSettings", QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        } finally {
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
        }
        database.setTag("questDatabase", QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        database.setTag("questLines", QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        database.setString("format", BetterQuesting.FORMAT);
        return database;
    }

    private static boolean loadDirectory(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir,
        boolean loadWorldSettings) {
        if (!dataDir.isDirectory()) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return false;
        }
        try {
            applyDatabase(sender, databaseName, readDirectory(dataDir, false), loadWorldSettings);
            return true;
        } catch (IOException failure) {
            QuestingAPI.getLogger()
                .error("Failed to load default quests from {}", dataDir, failure);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return false;
        }
    }

    private static NBTTagCompound readDirectory(File dataDir, boolean validate) throws IOException {
        File settingsFile = new File(dataDir, SETTINGS_FILE);
        if (!settingsFile.isFile()) throw new IOException("Missing quest settings: " + settingsFile);

        NBTTagList quests = new NBTTagList();
        Set<UUID> questIds = new HashSet<>();
        try (Stream<Path> paths = Files.walk(new File(dataDir, QUEST_DIR).toPath())) {
            Iterator<Path> files = paths.filter(Files::isRegularFile)
                .filter(
                    path -> path.getFileName()
                        .toString()
                        .endsWith(".json"))
                .iterator();
            while (files.hasNext()) {
                File file = files.next()
                    .toFile();
                NBTTagCompound quest = readDefinition(file, validate);
                if (validate && !questIds.add(requireQuestId(quest, "questID", file))) {
                    throw new IOException("Duplicate quest ID in " + file);
                }
                quests.appendTag(quest);
            }
        }

        File chaptersDir = new File(dataDir, QUEST_LINE_DIR);
        File[] folders = chaptersDir.listFiles(File::isDirectory);
        if (folders == null) throw new IOException("Failed to list quest chapters in " + chaptersDir);
        Map<UUID, NBTTagCompound> chapters = new HashMap<>();
        for (File folder : folders) {
            File header = new File(folder, QUEST_LINE_FILE);
            if (!header.isFile()) throw new IOException("Missing quest chapter header: " + header);
            NBTTagCompound chapter = readDefinition(header, validate);
            File[] files = folder.listFiles(
                file -> file.isFile() && file.getName()
                    .endsWith(".json"));
            if (files == null) throw new IOException("Failed to list quests in " + folder);
            NBTTagList entries = new NBTTagList();
            Set<UUID> chapterQuestIds = new HashSet<>();
            for (File file : files) {
                if (file.equals(header)) continue;
                NBTTagCompound entry = readDefinition(file, validate);
                if (validate) {
                    UUID questId = requireQuestId(entry, "id", file);
                    if (!questIds.contains(questId)) throw new IOException("Missing quest for " + file);
                    if (!chapterQuestIds.add(questId)) throw new IOException("Duplicate chapter entry in " + file);
                }
                entries.appendTag(entry);
            }
            chapter.setTag("quests", entries);
            UUID id = validate ? NBTConverter.UuidValueType.QUEST_LINE.tryReadId(chapter)
                .orElseThrow(() -> new IOException("Missing chapter ID in " + header))
                : NBTConverter.UuidValueType.QUEST_LINE.readId(chapter);
            if (chapters.put(id, chapter) != null) throw new IOException("Duplicate quest chapter: " + folder);
        }

        NBTTagList ordered = new NBTTagList();
        for (String line : Files.readAllLines(new File(dataDir, QUEST_LINE_ORDER_FILE).toPath())) {
            UUID id;
            try {
                id = UuidConverter.decodeUuid(line.split(":", 2)[0]);
            } catch (IllegalArgumentException failure) {
                throw new IOException("Invalid quest chapter order", failure);
            }
            NBTTagCompound chapter = chapters.remove(id);
            if (chapter == null) throw new IOException("Invalid quest chapter order entry: " + line);
            chapter.setInteger("order", ordered.tagCount());
            ordered.appendTag(chapter);
        }
        if (!chapters.isEmpty()) throw new IOException("Missing entries in the quest chapter order");

        NBTTagCompound database = new NBTTagCompound();
        database.setTag("questSettings", readDefinition(settingsFile, validate));
        database.setTag("questDatabase", quests);
        database.setTag("questLines", ordered);
        database.setString("format", BetterQuesting.FORMAT);
        return database;
    }

    private static NBTTagCompound readDefinition(File file, boolean validate) throws IOException {
        if (!validate) return readDefinition(file);
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return NBTConverter.readTypedCompound(JsonHelper.readObject(reader));
        } catch (IOException failure) {
            throw new IOException(file + ": " + failure.getMessage(), failure);
        }
    }

    private static UUID requireQuestId(NBTTagCompound quest, String legacyKey, File file) throws IOException {
        if (quest.hasKey(legacyKey, Constants.NBT.TAG_INT)) {
            return NBTConverter.UuidValueType.QUEST.tryReadId(quest)
                .orElseGet(() -> UuidConverter.convertLegacyId(quest.getInteger(legacyKey)));
        }
        return NBTConverter.UuidValueType.QUEST.tryReadId(quest)
            .orElseThrow(() -> new IOException("Missing quest ID in " + file));
    }

    public static void loadLegacy(@Nullable ICommandSender sender, @Nullable String databaseName, File legacyFile,
        boolean loadWorldSettings) {
        if (legacyFile.isFile()) {
            applyDatabase(
                sender,
                databaseName == null ? null : databaseName + ".json",
                readDefinition(legacyFile),
                loadWorldSettings);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
        }
    }

    private static void applyDatabase(@Nullable ICommandSender sender, @Nullable String databaseName,
        NBTTagCompound database, boolean loadWorldSettings) {
        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        boolean hardMode = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);
        NBTTagList progress = loadWorldSettings ? null
            : QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);

        QuestSettings.INSTANCE.readFromNBT(database.getCompoundTag("questSettings"));
        QuestDatabase.INSTANCE.readFromNBT(database.getTagList("questDatabase", Constants.NBT.TAG_COMPOUND), false);
        QuestLineDatabase.INSTANCE.readFromNBT(database.getTagList("questLines", Constants.NBT.TAG_COMPOUND), false);

        if (!loadWorldSettings) {
            QuestDatabase.INSTANCE.readProgressFromNBT(progress, false);
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
            QuestSettings.INSTANCE.setProperty(NativeProps.HARDCORE, hardMode);
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.load2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.load");
        }

        if (BetterQuesting.isVmLoaded) {
            VmAdapter.resetCompletedQuests(null);
        }
        NetSettingSync.sendSync(null);
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }

    public static void set(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir) {
        if (databaseName == null || DEFAULT_FILE.equalsIgnoreCase(databaseName) || !dataDir.isDirectory()) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return;
        }

        if (defaultArchive().isFile()) {
            try {
                if (refreshDefaultArchive(sender, readDirectory(dataDir, true))) {
                    sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
                }
            } catch (IOException failure) {
                QuestingAPI.getLogger()
                    .error("Failed to set default quests from {}", dataDir, failure);
                sendChatMessage(sender, "betterquesting.cmd.default.invalidsource", failure.getMessage());
            }
            return;
        }

        File defDir = new File(BQ_Settings.defaultDir, DEFAULT_FILE);

        if (defDir.exists()) {
            try {
                FileUtils.deleteDirectory(defDir);
            } catch (IOException e) {
                QuestingAPI.getLogger()
                    .log(Level.ERROR, "Failed to delete directory\n" + defDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }

        try {
            FileUtils.copyDirectory(dataDir, defDir);
        } catch (IOException e) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to copy directory\n" + dataDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
    }

    public static void exportLang(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir) {
        Multiset<UUID> questOccurrenceCount = HashMultiset.create(QuestDatabase.INSTANCE.size());
        QuestLineDatabase.INSTANCE.values()
            .forEach(questLine -> questOccurrenceCount.addAll(questLine.keySet()));

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create directory\n{}", dataDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        File langFile = new File(dataDir, LANG_FILE);

        try (OutputStreamWriter writer = new OutputStreamWriter(
            Files.newOutputStream(langFile.toPath()),
            StandardCharsets.UTF_8)) {

            Function<String, String> escapeName = s -> removeChatFormatting(s.replaceAll("\n", ""));
            Function<String, String> escapeLangString = s -> s.replaceAll("%", "%%")
                .replaceAll("\n", "%n");

            Consumer<IQuest> writeQuest = quest -> {
                UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);

                try {
                    writer
                        .write(String.format("\n# Quest: %s\n", escapeName.apply(quest.getProperty(NativeProps.NAME))));
                    writer.write(
                        String.format(
                            "%s=%s\n",
                            QuestTranslation.buildQuestNameKey(questId),
                            escapeLangString.apply(quest.getProperty(NativeProps.NAME))));
                    writer.write(
                        String.format(
                            "%s=%s\n",
                            QuestTranslation.buildQuestDescriptionKey(questId),
                            escapeLangString.apply(quest.getProperty(NativeProps.DESC))));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };

            writer.write("### Quest Lines ###\n");

            for (Map.Entry<UUID, IQuestLine> entry : QuestLineDatabase.INSTANCE.getOrderedEntries()) {
                UUID questLineId = entry.getKey();
                IQuestLine questLine = entry.getValue();

                writer.write(
                    String
                        .format("\n\n## Quest Line: %s\n", escapeName.apply(questLine.getProperty(NativeProps.NAME))));
                writer.write(
                    String.format(
                        "%s=%s\n",
                        QuestTranslation.buildQuestLineNameKey(questLineId),
                        escapeLangString.apply(questLine.getProperty(NativeProps.NAME))));
                writer.write(
                    String.format(
                        "%s=%s\n",
                        QuestTranslation.buildQuestLineDescriptionKey(questLineId),
                        escapeLangString.apply(questLine.getProperty(NativeProps.DESC))));

                questLine.orderedEntries()
                    .map(Map.Entry::getKey)
                    .filter(questId -> questOccurrenceCount.count(questId) == 1)
                    .map(QuestDatabase.INSTANCE::get)
                    .forEach(writeQuest);
            }

            writer.write("\n\n### Quests in multiple quest lines ###\n");
            QuestDatabase.INSTANCE.orderedEntries()
                .filter(entry -> questOccurrenceCount.count(entry.getKey()) > 1)
                .forEach(entry -> writeQuest.accept(entry.getValue()));

            writer.write("\n\n### Quests in no quest lines ###\n");
            QuestDatabase.INSTANCE.orderedEntries()
                .filter(entry -> questOccurrenceCount.count(entry.getKey()) == 0)
                .forEach(entry -> writeQuest.accept(entry.getValue()));

        } catch (IOException e) {
            QuestingAPI.getLogger()
                .log(Level.ERROR, "Failed to create file\n" + langFile, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.exportlang2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.exportlang");
        }
    }

    private static String removeChatFormatting(String string) {
        return TextFormattingUtils.stripFormatting(string);
    }
}
