package betterquesting.commands.admin;

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
import betterquesting.commands.QuestCommandBase;
import betterquesting.core.BetterQuesting;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;
import betterquesting.storage.QuestSettings;
import com.google.common.base.Splitter;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;
import com.google.gson.JsonObject;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class QuestCommandDefaults extends QuestCommandBase {
    public static final String DEFAULT_FILE = "DefaultQuests";
    public static final String LANG_FILE = "template.lang";
    public static final String SETTINGS_FILE = "QuestSettings.json";
    public static final String QUEST_LINE_DIR = "QuestLines";
    public static final String QUEST_LINE_ORDER_FILE = "QuestLinesOrder.txt";
    public static final String QUEST_DIR = "Quests";
    public static final String MULTI_QUEST_LINE_DIRECTORY = "MultipleQuestLine";
    public static final String NO_QUEST_LINE_DIRECTORY = "NoQuestLine";
    /**
     * This limit applies to the "name" portion of file names, but UUID will be appended, so the
     * actual file name will be longer.
     */
    public static final int FILE_NAME_MAX_LENGTH = 16;

    @Override
    public String getUsageSuffix() {
        return "[save|savelegacy|load|set|exportlang] [file_name]";
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
            return CommandBase.getListOfStringsMatchingLastWord(args, "save", "savelegacy", "load", "set", "exportlang");
        } else if (args.length == 3) {
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
        String databaseName;
        File dataDir;
        // The location of the legacy single huge file.
        File legacyFile;
        if (args.length == 3 && !args[2].equalsIgnoreCase(DEFAULT_FILE)) {
            databaseName = args[2];
            dataDir = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2]);
            legacyFile = new File(BQ_Settings.defaultDir, "saved_quests/" + args[2] + ".json");
        } else {
            databaseName = null;
            dataDir = new File(BQ_Settings.defaultDir, DEFAULT_FILE);
            legacyFile = new File(BQ_Settings.defaultDir, DEFAULT_FILE + ".json");
        }

        if (args[1].equalsIgnoreCase("save")) {
            save(sender, databaseName, dataDir);

        } else if (args[1].equalsIgnoreCase("savelegacy")) {
            saveLegacy(sender, databaseName, legacyFile);

        } else if (args[1].equalsIgnoreCase("load")) {
            if (!dataDir.exists() && legacyFile.exists()) {
                loadLegacy(sender, databaseName, legacyFile, false);
            } else {
                load(sender, databaseName, dataDir, false);
            }

        } else if (args[1].equalsIgnoreCase("set") && args.length == 3) {
            if (!dataDir.exists() && legacyFile.exists()) {
                setLegacy(sender, databaseName, legacyFile);
            } else {
                set(sender, databaseName, dataDir);
            }

        } else if (args[1].equalsIgnoreCase("exportlang")) {
            exportLang(sender, databaseName, dataDir);

        } else {
            throw getException(command);
        }
    }

    /** Helper method that handles having null sender. */
    private static void sendChatMessage(
            @Nullable ICommandSender sender, String translationKey, Object... args) {
        if (sender == null) {
            return;
        }
        sender.addChatMessage(new ChatComponentTranslation(translationKey, args));
    }

    public static void save(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir) {
        // Remove chat formatting, as well as simplifying names for use in file paths.
        BiFunction<String, UUID, String> buildFileName =
                (name, id) -> {
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
                QuestingAPI.getLogger().log(Level.ERROR, "Directory exists, but isn't a database\n{}", dataDir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            try {
                FileUtils.deleteDirectory(dataDir);
            } catch (IOException e) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to delete directory\n" + dataDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }
        if (!dataDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directory\n{}", dataDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        // Don't write editmode to json
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        NBTTagCompound settingsTag = QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound());
        settingsTag.setString("format", BetterQuesting.FORMAT);
        JsonHelper.WriteToFile(settingsFile, NBTConverter.NBTtoJSON_Compound(settingsTag, new JsonObject(), true));
        // And restore back
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);

        File questDir = new File(dataDir, QUEST_DIR);
        if (!questDir.exists() && !questDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        if (!questLineDir.exists() && !questLineDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questLineDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        ListMultimap<UUID, IQuestLine> questToQuestLineMultimap =
                MultimapBuilder.hashKeys().arrayListValues().build();
        List<String> questLineOrderLines = new ArrayList<>(QuestLineDatabase.INSTANCE.size());

        for (Map.Entry<UUID, IQuestLine> entry : QuestLineDatabase.INSTANCE.getOrderedEntries()) {
            UUID questLineId = entry.getKey();
            IQuestLine questLine = entry.getValue();
            questLine.keySet().forEach(key -> questToQuestLineMultimap.put(key, questLine));

            String questLineName = questLine.getProperty(NativeProps.NAME);
            questLineOrderLines.add(
                    String.format(
                            "%s: %s",
                            UuidConverter.encodeUuid(questLineId),
                            removeChatFormatting(questLineName)));

            File questLineSubdir = new File(questLineDir, buildFileName.apply(questLineName, questLineId));
            if (!questLineSubdir.exists() && !questLineSubdir.mkdirs()) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questLineSubdir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            File questLineFile = new File(questLineSubdir, "QuestLine.json");
            NBTTagCompound questLineTag = questLine.writeToNBT(new NBTTagCompound(), true);
            NBTConverter.UuidValueType.QUEST_LINE.writeId(questLineId, questLineTag);
            JsonHelper.WriteToFile(questLineFile, NBTConverter.NBTtoJSON_Compound(questLineTag, new JsonObject(), true));

            for (Map.Entry<UUID, IQuestLineEntry> questLineEntry : questLine.entrySet()) {
                // Unfortunately, we must prepend the quest name to the filename.
                // This is because Windows treats filenames which differ only in casing, as the same
                // file. This causes problems for us as many UUIDs differ only in casing, so we must
                // disambiguate using the quest name as a prefix.
                UUID questId = questLineEntry.getKey();
                String questName = QuestDatabase.INSTANCE.get(questId).getProperty(NativeProps.NAME);

                File questLineEntryFile = new File(questLineSubdir, buildFileName.apply(questName, questId) + ".json");
                NBTTagCompound questLineEntryTag = questLineEntry.getValue().writeToNBT(new NBTTagCompound());
                NBTConverter.UuidValueType.QUEST.writeId(questId, questLineEntryTag);
                JsonHelper.WriteToFile(questLineEntryFile, NBTConverter.NBTtoJSON_Compound(questLineEntryTag, new JsonObject(), true));
            }
        }

        File questLineOrderFile = new File(dataDir, QUEST_LINE_ORDER_FILE);
        try {
            Files.write(questLineOrderFile.toPath(), questLineOrderLines, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create file\n" + questLineOrderFile, e);
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
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questSubdir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
            String questName = quest.getProperty(NativeProps.NAME);
            File questFile = new File(questSubdir, buildFileName.apply(questName, questId) + ".json");

            NBTTagCompound questTag = quest.writeToNBT(new NBTTagCompound());
            NBTConverter.UuidValueType.QUEST.writeId(questId, questTag);
            JsonHelper.WriteToFile(questFile, NBTConverter.NBTtoJSON_Compound(questTag, new JsonObject(), true));
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.save2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.save");
        }
    }

    /** This is unused by default, but available if needed. The single file is easier to search. */
    public static void saveLegacy(@Nullable ICommandSender sender, @Nullable String databaseName, File legacyFile) {
        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);

        NBTTagCompound base = new NBTTagCompound();
        // Don't write editmode to json
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false);
        base.setTag("questSettings", QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        // And restore back
        QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
        base.setTag("questDatabase", QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        base.setTag("questLines", QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        base.setString("format", BetterQuesting.FORMAT);
        JsonHelper.WriteToFile(legacyFile, NBTConverter.NBTtoJSON_Compound(base, new JsonObject(), true));

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.save2", databaseName + ".json");
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.save");
        }
    }

    public static void load(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir, boolean loadWorldSettings) {
        if (!dataDir.exists()) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return;
        }

        Function<File, NBTTagCompound> readNbt =
                file ->
                        NBTConverter.JSONtoNBT_Object(
                                JsonHelper.ReadFromFile(file), new NBTTagCompound(), true);

        boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        boolean hardMode = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);
        NBTTagList jsonP = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);

        File settingsFile = new File(dataDir, SETTINGS_FILE);
        if (!settingsFile.exists()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to find file\n{}", settingsFile);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        QuestSettings.INSTANCE.readFromNBT(readNbt.apply(settingsFile));

        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        Map<UUID, IQuestLine> questLines = new HashMap<>();
        for (File questLineSubdir : questLineDir.listFiles()) {
            File questLineFile = new File(questLineSubdir, "QuestLine.json");
            if (!questLineFile.exists()) {
                QuestingAPI.getLogger().log(Level.ERROR, "Missing quest line file\n" + questLineSubdir);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            NBTTagCompound questLineTag = readNbt.apply(questLineFile);
            UUID questLineId = NBTConverter.UuidValueType.QUEST_LINE.readId(questLineTag);

            IQuestLine questLine = new QuestLine();
            questLine.readFromNBT(questLineTag);
            questLines.put(questLineId, questLine);

            for (File questLineEntryFile : questLineSubdir.listFiles()) {
                if (questLineEntryFile.getName().equals("QuestLine.json")) {
                    continue;
                }

                NBTTagCompound questLineEntryTag = readNbt.apply(questLineEntryFile);
                UUID questId = NBTConverter.UuidValueType.QUEST.readId(questLineEntryTag);
                questLine.put(questId, new QuestLineEntry(questLineEntryTag));
            }
        }

        File questLineOrderFile = new File(dataDir, QUEST_LINE_ORDER_FILE);
        List<String> questLineOrderLines;
        try {
            questLineOrderLines = Files.readAllLines(questLineOrderFile.toPath());
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to read file\n" + questLineOrderFile, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        List<Map.Entry<UUID, IQuestLine>> orderedQuestLines = new ArrayList<>(questLineOrderLines.size());
        Splitter splitter = Splitter.on(':');
        for (String line : questLineOrderLines) {
            Iterator<String> iter = splitter.split(line).iterator();
            if (!iter.hasNext()) {
                continue;
            }

            UUID questLineId = UuidConverter.decodeUuid(iter.next());
            orderedQuestLines.add(Maps.immutableEntry(questLineId, questLines.get(questLineId)));
        }
        QuestLineDatabase.INSTANCE.setOrderedEntries(orderedQuestLines);

        File questDir = new File(dataDir, QUEST_DIR);
        QuestDatabase.INSTANCE.clear();
        try (Stream<Path> paths = Files.walk(questDir.toPath())) {
            paths.filter(Files::isRegularFile).forEach(
                    path -> {
                        File questFile = path.toFile();
                        NBTTagCompound questTag = readNbt.apply(questFile);
                        UUID questId = NBTConverter.UuidValueType.QUEST.readId(questTag);

                        IQuest quest = new QuestInstance();
                        quest.readFromNBT(questTag);
                        QuestDatabase.INSTANCE.put(questId, quest);
                    }
            );
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to traverse directory\n" + questDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        if (!loadWorldSettings) {
            // Don't load world-specific settings, so restore them from the snapshot we took.
            QuestDatabase.INSTANCE.readProgressFromNBT(jsonP, false);
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
            QuestSettings.INSTANCE.setProperty(NativeProps.HARDCORE, hardMode);
        }

        if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.load2", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.load");
        }

        NetSettingSync.sendSync(null);
        NetQuestSync.quickSync(null, true, true);
        NetChapterSync.sendSync(null, null);
        SaveLoadHandler.INSTANCE.markDirty();
    }

    public static void loadLegacy(@Nullable ICommandSender sender, @Nullable String databaseName, File legacyFile, boolean loadWorldSettings) {
        if (legacyFile.exists()) {
            boolean editMode = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
            boolean hardMode = QuestSettings.INSTANCE.getProperty(NativeProps.HARDCORE);
            NBTTagList jsonP = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);

            JsonObject j1 = JsonHelper.ReadFromFile(legacyFile);
            NBTTagCompound nbt1 = NBTConverter.JSONtoNBT_Object(j1, new NBTTagCompound(), true);

            QuestSettings.INSTANCE.readFromNBT(nbt1.getCompoundTag("questSettings"));
            QuestDatabase.INSTANCE.readFromNBT(nbt1.getTagList("questDatabase", Constants.NBT.TAG_COMPOUND), false);
            QuestLineDatabase.INSTANCE.readFromNBT(nbt1.getTagList("questLines", Constants.NBT.TAG_COMPOUND), false);

            if (!loadWorldSettings) {
                // Don't load world-specific settings, so restore them from the snapshot we took.
                QuestDatabase.INSTANCE.readProgressFromNBT(jsonP, false);
                QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, editMode);
                QuestSettings.INSTANCE.setProperty(NativeProps.HARDCORE, hardMode);
            }

            if (databaseName != null && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
                sendChatMessage(sender, "betterquesting.cmd.default.load2", databaseName + ".json");
            } else {
                sendChatMessage(sender, "betterquesting.cmd.default.load");
            }

            NetSettingSync.sendSync(null);
            NetQuestSync.quickSync(null, true, true);
            NetChapterSync.sendSync(null, null);
            SaveLoadHandler.INSTANCE.markDirty();
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
        }
    }

    public static void set(@Nullable ICommandSender sender, String databaseName, File dataDir) {
        if (!dataDir.exists() || databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
            return;
        }

        File defDir = new File(BQ_Settings.defaultDir, DEFAULT_FILE);

        if (defDir.exists()) {
            try {
                FileUtils.deleteDirectory(defDir);
            } catch (IOException e) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to delete directory\n" + defDir, e);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }
        }

        try {
            FileUtils.copyDirectory(dataDir, defDir);
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to copy directory\n" + dataDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
    }

    public static void setLegacy(@Nullable ICommandSender sender, String databaseName, File legacyFile) {
        if (legacyFile.exists() && !databaseName.equalsIgnoreCase(DEFAULT_FILE)) {
            File defFile = new File(BQ_Settings.defaultDir, DEFAULT_FILE + ".json");

            if (defFile.exists()) {
                defFile.delete();
            }

            JsonHelper.CopyPaste(legacyFile, defFile);

            sendChatMessage(sender, "betterquesting.cmd.default.set", databaseName);
        } else {
            sendChatMessage(sender, "betterquesting.cmd.default.none");
        }
    }

    public static void exportLang(@Nullable ICommandSender sender, @Nullable String databaseName, File dataDir) {
        Multiset<UUID> questOccurrenceCount = HashMultiset.create(QuestDatabase.INSTANCE.size());
        QuestLineDatabase.INSTANCE.values()
                .forEach(questLine -> questOccurrenceCount.addAll(questLine.keySet()));

        if (!dataDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directory\n{}", dataDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        File langFile = new File(dataDir, LANG_FILE);

        try (
                OutputStreamWriter writer =
                        new OutputStreamWriter(Files.newOutputStream(langFile.toPath()), StandardCharsets.UTF_8)) {

            Function<String, String> escapeName = s -> removeChatFormatting(s.replaceAll("\n", ""));
            Function<String, String> escapeLangString =
                    s -> s.replaceAll("%", "%%").replaceAll("\n", "%n");

            Consumer<IQuest> writeQuest =
                    quest -> {
                        UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);

                        try {
                            writer.write(
                                    String.format(
                                            "\n# Quest: %s\n",
                                            escapeName.apply(quest.getProperty(NativeProps.NAME))));
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
                        String.format(
                                "\n\n## Quest Line: %s\n",
                                escapeName.apply(questLine.getProperty(NativeProps.NAME))));
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
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create file\n" + langFile, e);
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
        return string.replaceAll("§[0-9a-fk-or]", "");
    }
}
