package betterquesting.commands.admin;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
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
import betterquesting.storage.QuestSettings;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SortedSetMultimap;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class QuestCommandDefaults extends QuestCommandBase {
    public static final String DEFAULT_FILE = "DefaultQuests";
    public static final String LANG_FILE = "en_US.lang";

    public static final String SETTINGS_FILE = "QuestSettings.json";
    public static final String QUEST_LINE_DIR = "QuestLines";
    public static final String QUEST_DIR = "Quests";
    public static final String MULTI_QUEST_LINE_DIRECTORY = "MultipleQuestLine";
    public static final String NO_QUEST_LINE_DIRECTORY = "NoQuestLine";

    @Override
    public String getUsageSuffix() {
        return "[save|load|set] [file_name]";
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
            return CommandBase.getListOfStringsMatchingLastWord(args, "save", "savelegacy", "load", "set");
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
                (name, id) ->
                        String.format(
                                "%s-%s",
                                name
                                        .replaceAll("§[0-9a-fk-or]", "")
                                        .replaceAll("[^a-zA-Z0-9]", ""),
                                UuidConverter.encodeUuid(id));

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

        File questLineDir = new File(dataDir, QUEST_LINE_DIR);
        if (!questLineDir.exists() && !questLineDir.mkdirs()) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questLineDir);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }

        int questLineIndex = 0;
        ListMultimap<UUID, IQuestLine> questToQuestLineMultimap =
                MultimapBuilder.hashKeys().arrayListValues().build();

        for (Map.Entry<UUID, IQuestLine> entry : QuestLineDatabase.INSTANCE.getOrderedEntries()) {
            UUID questLineId = entry.getKey();
            IQuestLine questLine = entry.getValue();
            questLine.keySet().forEach(key -> questToQuestLineMultimap.put(key, questLine));

            String questLineName = questLine.getProperty(NativeProps.NAME);
            File questLineFile = new File(questLineDir, buildFileName.apply(questLineName, questLineId) + ".json");

            NBTTagCompound questLineTag = questLine.writeToNBT(new NBTTagCompound());
            NBTConverter.UuidValueType.QUEST_LINE.writeId(questLineId, questLineTag);
            questLineTag.setInteger("order", questLineIndex++);
            JsonHelper.WriteToFile(questLineFile, NBTConverter.NBTtoJSON_Compound(questLineTag, new JsonObject(), true));
        }

        SortedMap<UUID, IQuest> questsInMultipleQuestLines = new TreeMap<>();
        SortedMap<UUID, IQuest> questsInZeroQuestLines = new TreeMap<>();

        for (Map.Entry<UUID, IQuest> entry : QuestDatabase.INSTANCE.entrySet()) {
            UUID questId = entry.getKey();
            IQuest quest = entry.getValue();
            List<IQuestLine> questLines = questToQuestLineMultimap.get(questId);

            File questDir = new File(dataDir, QUEST_DIR);
            switch (questLines.size()) {
                case 0:
                    questsInZeroQuestLines.put(questId, quest);
                    questDir = new File(questDir, NO_QUEST_LINE_DIRECTORY);
                    break;

                case 1:
                    IQuestLine questLine = questLines.get(0);
                    UUID questLineId = QuestLineDatabase.INSTANCE.lookupKey(questLine);
                    String questLineName = questLine.getProperty(NativeProps.NAME);
                    questDir = new File(questDir, buildFileName.apply(questLineName, questLineId));
                    break;

                default:
                    questsInMultipleQuestLines.put(questId, quest);
                    questDir = new File(questDir, MULTI_QUEST_LINE_DIRECTORY);
                    break;
            }

            String questName = quest.getProperty(NativeProps.NAME);
            File questFile = new File(questDir, buildFileName.apply(questName, questId) + ".json");
            if (!questFile.exists() && !questFile.mkdirs()) {
                QuestingAPI.getLogger().log(Level.ERROR, "Failed to create directories\n{}", questFile);
                sendChatMessage(sender, "betterquesting.cmd.error");
                return;
            }

            NBTTagCompound questTag = quest.writeToNBT(new NBTTagCompound());
            NBTConverter.UuidValueType.QUEST.writeId(questId, questTag);
            JsonHelper.WriteToFile(questFile, NBTConverter.NBTtoJSON_Compound(questTag, new JsonObject(), true));
        }

        File langFile = new File(dataDir, LANG_FILE);
        try (
                OutputStreamWriter writer =
                        new OutputStreamWriter(Files.newOutputStream(langFile.toPath()))) {

            Function<String, String> removeNewlines = s -> s.replaceAll("\n", "");
            Function<String, String> escapeLangString =
                    s -> s.replaceAll("%", "%%").replaceAll("\n", "%n");

            Consumer<IQuest> writeQuest =
                    quest -> {
                        UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);

                        try {
                            writer.write(
                                    String.format(
                                            "\n# Quest: %s\n",
                                            removeNewlines.apply(quest.getProperty(NativeProps.NAME))));
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
                                removeNewlines.apply(questLine.getProperty(NativeProps.NAME))));
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

                SortedMap<UUID, IQuest> quests =
                        new TreeMap<>(QuestDatabase.INSTANCE.filterKeys(questLine.keySet()));
                orderByRequirements(quests).forEach(writeQuest);
            }

            writer.write("\n\n### Quests in multiple quest lines ###\n");
            orderByRequirements(questsInMultipleQuestLines).forEach(writeQuest);

            writer.write("\n\n### Quests in no quest lines ###\n");
            orderByRequirements(questsInZeroQuestLines).forEach(writeQuest);

        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to create file\n" + langFile, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
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
        SortedMap<Integer, Map.Entry<UUID, IQuestLine>> questLines = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(questLineDir.toPath())) {
            paths.filter(Files::isRegularFile).forEach(
                    path -> {
                        File questLineFile = path.toFile();
                        NBTTagCompound questLineTag = readNbt.apply(questLineFile);
                        UUID questLineId = NBTConverter.UuidValueType.QUEST_LINE.readId(questLineTag);
                        int order = questLineTag.getInteger("order");

                        if (questLines.containsKey(order)) {
                            QuestingAPI.getLogger().log(Level.ERROR, "Found duplicate quest line order: {}, {}", order, UuidConverter.encodeUuid(questLineId));
                            QuestingAPI.getLogger().log(Level.ERROR, "You most likely have left over quest line files!");
                            sendChatMessage(sender, "betterquesting.cmd.error");
                            return;
                        }

                        IQuestLine questLine = new QuestLine();
                        questLine.readFromNBT(questLineTag);
                        questLines.put(order, Maps.immutableEntry(questLineId, questLine));
                    }
            );
        } catch (IOException e) {
            QuestingAPI.getLogger().log(Level.ERROR, "Failed to traverse directory\n" + questLineDir, e);
            sendChatMessage(sender, "betterquesting.cmd.error");
            return;
        }
        QuestLineDatabase.INSTANCE.setOrderedEntries(questLines.values());

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

    /**
     * Helper method which tries to order quests by their requirements,
     * for ordered output to {@code en_US.lang}.
     *
     * <p>This method needs to be stable, to prevent noisy Git changes in {@code en_US.lang}.
     * The input is required to be sorted by quest ID, to help with stability.
     */
    private static List<IQuest> orderByRequirements(SortedMap<UUID, IQuest> quests) {
        SortedSetMultimap<Map.Entry<UUID, IQuest>, Map.Entry<UUID, IQuest>> predecessors =
                MultimapBuilder
                        .hashKeys()
                        .<Map.Entry<UUID, IQuest>>treeSetValues(Map.Entry.comparingByKey())
                        .build();
        SortedSetMultimap<Map.Entry<UUID, IQuest>, Map.Entry<UUID, IQuest>> successors =
                MultimapBuilder
                        .hashKeys()
                        .<Map.Entry<UUID, IQuest>>treeSetValues(Map.Entry.comparingByKey())
                        .build();

        quests.entrySet().forEach(
                entry -> {
                    for (UUID requirementId : entry.getValue().getRequirements()) {
                        IQuest requirement = quests.get(requirementId);
                        if (requirement == null) {
                            continue;
                        }

                        Map.Entry<UUID, IQuest> requirementEntry =
                                Maps.immutableEntry(requirementId, requirement);
                        predecessors.put(entry, requirementEntry);
                        successors.put(requirementEntry, entry);
                    }
                });

        List<IQuest> orderedQuests = new ArrayList<>(quests.size());
        // Used to track which quests have already been added, to avoid adding duplicates.
        Set<Map.Entry<UUID, IQuest>> addedQuests = new HashSet<>(quests.size());

        Consumer<Map.Entry<UUID, IQuest>> addQuest =
                new Consumer<Map.Entry<UUID, IQuest>>() {
                    @Override
                    public void accept(Map.Entry<UUID, IQuest> entry) {
                        if (addedQuests.contains(entry)) {
                            return;
                        }
                        addedQuests.add(entry);

                        for (Map.Entry<UUID, IQuest> predecessor : predecessors.get(entry)) {
                            accept(predecessor);
                        }
                        orderedQuests.add(entry.getValue());
                        for (Map.Entry<UUID, IQuest> successor : successors.get(entry)) {
                            accept(successor);
                        }
                    }
                };
        quests.entrySet().forEach(addQuest);

        return orderedQuests;
    }
}
