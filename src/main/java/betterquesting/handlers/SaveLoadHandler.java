package betterquesting.handlers;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.client.QuestNotification;
import betterquesting.client.gui2.GuiHome;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.core.BetterQuesting;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.LifeDatabase;
import betterquesting.storage.NameCache;
import betterquesting.storage.QuestSettings;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import io.netty.util.internal.ConcurrentSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class SaveLoadHandler {
    public static SaveLoadHandler INSTANCE = new SaveLoadHandler();

    private boolean hasUpdate = false;
    private boolean isDirty = false;

    private File
            fileDatabase = null,
            fileProgress = null,
            dirProgress = null,
            fileParties = null,
            fileLives = null,
            fileNames = null;

    private final Set<UUID> dirtyPlayers = new ConcurrentSet<>();

    public boolean hasUpdate() {
        return this.hasUpdate;
    }

    public void resetUpdate() {
        this.hasUpdate = false;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public void addDirtyPlayers(UUID... players) {
        this.dirtyPlayers.addAll(Arrays.asList(players));
    }

    public void addDirtyPlayers(Collection<UUID> players) {
        this.dirtyPlayers.addAll(players);
    }

    public void loadDatabases(MinecraftServer server) {
        hasUpdate = false;

        if (BetterQuesting.proxy.isClient()) {
            GuiHome.bookmark = null;
            QuestNotification.resetNotices();
        }

        File rootDir;

        if (BetterQuesting.proxy.isClient()) {
            BQ_Settings.curWorldDir = server.getFile("saves/" + server.getFolderName() + "/betterquesting");
            rootDir = server.getFile("saves/" + server.getFolderName());
        } else {
            BQ_Settings.curWorldDir = server.getFile(server.getFolderName() + "/betterquesting");
            rootDir = server.getFile(server.getFolderName());
        }

        fileDatabase = new File(BQ_Settings.curWorldDir, "QuestDatabase.json");
        fileProgress = new File(BQ_Settings.curWorldDir, "QuestProgress.json");
        dirProgress = new File(BQ_Settings.curWorldDir, "QuestProgress");
        fileParties = new File(BQ_Settings.curWorldDir, "QuestingParties.json");
        fileLives = new File(BQ_Settings.curWorldDir, "LifeDatabase.json");
        fileNames = new File(BQ_Settings.curWorldDir, "NameCache.json");

        loadConfig();

        loadProgress();

        LoadParties();

        loadNames();

        loadLives();

        BetterQuesting.logger.info("Loaded " + QuestDatabase.INSTANCE.size() + " quests");
        BetterQuesting.logger.info("Loaded " + QuestLineDatabase.INSTANCE.size() + " quest lines");
        BetterQuesting.logger.info("Loaded " + PartyManager.INSTANCE.size() + " parties");
        BetterQuesting.logger.info("Loaded " + NameCache.INSTANCE.size() + " names");

        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Load(DBType.ALL));
    }

    public void saveDatabases() {
        List<Future<Void>> allFutures = new ArrayList<>(5);

        if (!BQ_Settings.dirtyMode || isDirty || QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE)) {
            allFutures.add(saveConfig());
        }

        allFutures.addAll(saveProgress());

        allFutures.add(saveParties());

        allFutures.add(saveNames());

        allFutures.add(saveLives());

        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Save(DBType.ALL));

        for (Future<Void> future : allFutures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                BetterQuesting.logger.warn("Saving interrupted!", e);
            } catch (ExecutionException e) {
                BetterQuesting.logger.warn("Saving failed!", e.getCause());
            }
        }

        isDirty = false;
    }

    public void unloadDatabases() {
        BQ_Settings.curWorldDir = null;
        hasUpdate = false;
        isDirty = false;

        QuestSettings.INSTANCE.reset();
        QuestDatabase.INSTANCE.clear();
        QuestLineDatabase.INSTANCE.clear();
        LifeDatabase.INSTANCE.reset();
        NameCache.INSTANCE.reset();

        //QuestCache.INSTANCE.reset();

        if (BetterQuesting.proxy.isClient()) {
            GuiHome.bookmark = null;
            QuestNotification.resetNotices();
        }
    }

    private void loadConfig() {
        QuestSettings.INSTANCE.reset();
        QuestDatabase.INSTANCE.clear();
        QuestLineDatabase.INSTANCE.clear();

        int packVer = 0;
        String packName = "";

        File defaultDatabaseFile = new File(BQ_Settings.defaultDir, QuestCommandDefaults.DEFAULT_FILE + ".json");
        File defaultDatabaseDir = new File(BQ_Settings.defaultDir, QuestCommandDefaults.DEFAULT_FILE);
        File defaultDatabaseSettingsFile = new File(defaultDatabaseDir, QuestCommandDefaults.SETTINGS_FILE);

        if (fileDatabase.exists())
        {
            boolean legacySettings = !defaultDatabaseSettingsFile.exists();
            File settingsFile = legacySettings ? defaultDatabaseFile : defaultDatabaseSettingsFile;
            JsonObject settingsJson = JsonHelper.ReadFromFile(settingsFile);
            NBTTagCompound settingsTag = NBTConverter.JSONtoNBT_Object(settingsJson, new NBTTagCompound(), true);

            QuestSettings tmpSettings = new QuestSettings();
            tmpSettings.readFromNBT(legacySettings ? settingsTag.getCompoundTag("questSettings") : settingsTag);
            packVer = tmpSettings.getProperty(NativeProps.PACK_VER);
            packName = tmpSettings.getProperty(NativeProps.PACK_NAME);

            // Getting the build version like this is a bit wasteful, as we read the JSON twice.
            // Perhaps we should improve this.
            JsonObject databaseJson = JsonHelper.ReadFromFile(fileDatabase);
            String buildVer =
                    NBTConverter.JSONtoNBT_Object(databaseJson, new NBTTagCompound(), true)
                            .getString("build");
            String currVer = Loader.instance().activeModContainer().getVersion();

            if (!currVer.equalsIgnoreCase(buildVer)) // RUN BACKUPS
            {
                String fsVer = JsonHelper.makeFileNameSafe(buildVer);

                if (fsVer.isEmpty())
                {
                    fsVer = "pre-251";
                }

                BetterQuesting.logger.warn("BetterQuesting has been updated to from \"" + fsVer + "\" to \"" + currVer + "\"! Creating backups...");

                JsonHelper.CopyPaste(fileDatabase, new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "QuestDatabase_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(fileProgress, new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "QuestProgress_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(fileParties, new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "QuestingParties_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(fileNames, new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "NameCache_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(fileLives, new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "LifeDatabase_backup_" + fsVer + ".json"));
            }

            QuestCommandDefaults.loadLegacy(null, null, fileDatabase, true);
        } else { // LOAD DEFAULTS
            if (defaultDatabaseDir.exists())
            {
                QuestCommandDefaults.load(null, null, defaultDatabaseDir, true);
            }
            else
            {
                QuestCommandDefaults.loadLegacy(null, null, defaultDatabaseFile, true);
            }

            isDirty = true;
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false); // Force edit off
        }

        hasUpdate = packName.equals(QuestSettings.INSTANCE.getProperty(NativeProps.PACK_NAME)) && packVer > QuestSettings.INSTANCE.getProperty(NativeProps.PACK_VER);
    }

    private void loadProgress() {
        if (fileProgress.exists()) {
            JsonObject json = JsonHelper.ReadFromFile(fileProgress);

            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            QuestDatabase.INSTANCE.readProgressFromNBT(nbt.getTagList("questProgress", 10), false);

            // Mark all data as dirty to migrate the file to the new format
            markDirty();
            HashSet<UUID> usersFound = new HashSet<>();
            for (IQuest quest : QuestDatabase.INSTANCE.values()) {
                if (quest instanceof QuestInstance) {
                    QuestInstance qi = (QuestInstance) quest;
                    qi.getUsersWithCompletionData(usersFound);
                }
            }
            dirtyPlayers.addAll(usersFound);
        }

        getPlayerProgressFiles().forEach(file -> {
            JsonObject json = JsonHelper.ReadFromFile(file);
            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            QuestDatabase.INSTANCE.readProgressFromNBT(nbt.getTagList("questProgress", 10), true);
        });
    }

    private void LoadParties() {
        JsonObject json = JsonHelper.ReadFromFile(fileParties);

        NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
        PartyManager.INSTANCE.readFromNBT(nbt.getTagList("parties", 10), false);
    }

    private void loadNames() {
        NameCache.INSTANCE.reset();
        JsonObject json = JsonHelper.ReadFromFile(fileNames);

        NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
        NameCache.INSTANCE.readFromNBT(nbt.getTagList("nameCache", 10), false);
    }

    private void loadLives() {
        LifeDatabase.INSTANCE.reset();
        JsonObject json = JsonHelper.ReadFromFile(fileLives);

        NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
        LifeDatabase.INSTANCE.readFromNBT(nbt.getCompoundTag("lifeDatabase"), false);
    }

    private Future<Void> saveConfig() {
        NBTTagCompound json = new NBTTagCompound();

        json.setTag("questSettings", QuestSettings.INSTANCE.writeToNBT(new NBTTagCompound()));
        json.setTag("questDatabase", QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));
        json.setTag("questLines", QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null));

        json.setString("format", BetterQuesting.FORMAT);
        json.setString("build", Loader.instance().activeModContainer().getVersion());

        return JsonHelper.WriteToFile2(fileDatabase, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    private List<Future<Void>> saveProgress() {
        final List<Future<Void>> futures = dirtyPlayers.stream().map(this::savePlayerProgress).collect(Collectors.toList());
        dirtyPlayers.clear();
        if (fileProgress != null && fileProgress.exists()) {
            String backupName = fileProgress.getName().replace(".json", ".backup.json");
            try {
                FileUtils.moveFile(fileProgress, new File(fileProgress.getParentFile(), backupName));
                // Only remove the file if the backup was successful
                FileUtils.forceDelete(fileProgress);
            } catch (Exception e) {
                BetterQuesting.logger.warn("Could not move old progress data out of the way {}", backupName, e);
            }
        }
        return futures;
    }

    private Future<Void> saveParties() {
        NBTTagCompound json = new NBTTagCompound();

        json.setTag("parties", PartyManager.INSTANCE.writeToNBT(new NBTTagList(), null));

        return JsonHelper.WriteToFile2(fileParties, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    private Future<Void> saveNames() {
        NBTTagCompound json = new NBTTagCompound();

        json.setTag("nameCache", NameCache.INSTANCE.writeToNBT(new NBTTagList(), null));

        return JsonHelper.WriteToFile2(fileNames, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    private Future<Void> saveLives() {
        NBTTagCompound json = new NBTTagCompound();

        json.setTag("lifeDatabase", LifeDatabase.INSTANCE.writeToNBT(new NBTTagCompound(), null));

        return JsonHelper.WriteToFile2(fileLives, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    public Future<Void> savePlayerProgress(UUID player) {
        NBTTagCompound json = new NBTTagCompound();

        json.setTag("questProgress", QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), Collections.singletonList(player)));

        return JsonHelper.WriteToFile2(new File(dirProgress, player.toString() + ".json"), out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    private List<File> getPlayerProgressFiles() {
        final File[] files = dirProgress.listFiles();
        if (files == null) {
            return new ArrayList<>();
        }
        return Arrays.stream(files).filter(f -> f.getName().endsWith(".json")).collect(Collectors.toList());
    }

}
