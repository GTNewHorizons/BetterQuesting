package betterquesting.handlers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import com.google.common.util.concurrent.Striped;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import betterquesting.api.events.DatabaseEvent;
import betterquesting.api.events.DatabaseEvent.DBType;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.client.QuestNotification;
import betterquesting.client.gui2.GuiHome;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetDatabaseCache;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.party.PartyManager;
import betterquesting.storage.LifeDatabase;
import betterquesting.storage.NameCache;
import betterquesting.storage.PlayerProgressJson;
import betterquesting.storage.QuestSettings;
import cpw.mods.fml.common.Loader;
import io.netty.util.internal.ConcurrentSet;

public class SaveLoadHandler {

    public static SaveLoadHandler INSTANCE = new SaveLoadHandler();

    private boolean hasUpdate = false;
    private boolean isDirty = false;

    private File fileDatabase = null, fileParties = null, fileLives = null, fileNames = null;
    private volatile File dirProgress;

    private final Set<UUID> dirtyPlayers = new ConcurrentSet<>();
    private final Set<UUID> loadedPlayers = new ConcurrentSet<>();
    private final Set<UUID> loadingPlayers = new HashSet<>();
    private final Map<UUID, Long> failedReads = new HashMap<>();
    private static final long READ_RETRY_NANOS = TimeUnit.SECONDS.toNanos(10);
    private final Map<UUID, Future<Void>> pendingWrites = new HashMap<>();
    private final Striped<Lock> progressFileLocks = Striped.lazyWeakLock(256);
    private CompletableFuture<ProgressRewriteResult> progressRewrite;
    private volatile boolean allProgressLoaded;
    private long lastFailedGlobalRead;
    private boolean globalReadFailed;

    public boolean hasUpdate() {
        return this.hasUpdate;
    }

    public void resetUpdate() {
        this.hasUpdate = false;
    }

    public void markDirty() {
        this.isDirty = true;
        NetDatabaseCache.invalidateServerSnapshot();
    }

    public void addDirtyPlayers(UUID... players) {
        this.dirtyPlayers.addAll(Arrays.asList(players));
    }

    public void addDirtyPlayers(Collection<UUID> players) {
        this.dirtyPlayers.addAll(players);
    }

    public void loadDatabases(MinecraftServer server) {
        NetDatabaseCache.invalidateServerSnapshot();
        hasUpdate = false;
        dirProgress = null;

        if (BetterQuesting.proxy.isClient()) {
            GuiHome.bookmark = null;
            QuestNotification.resetNotices();
        }

        if (BetterQuesting.proxy.isClient()) {
            BQ_Settings.curWorldDir = server.getFile("saves/" + server.getFolderName() + "/betterquesting");
        } else {
            BQ_Settings.curWorldDir = server.getFile(server.getFolderName() + "/betterquesting");
        }

        fileDatabase = new File(BQ_Settings.curWorldDir, "QuestDatabase.json");
        fileParties = new File(BQ_Settings.curWorldDir, "QuestingParties.json");
        fileLives = new File(BQ_Settings.curWorldDir, "LifeDatabase.json");
        fileNames = new File(BQ_Settings.curWorldDir, "NameCache.json");

        loadConfig();

        LoadParties();

        loadNames();

        loadLives();

        BetterQuesting.logger.info("Loaded " + QuestDatabase.INSTANCE.size() + " quests");
        BetterQuesting.logger.info("Loaded " + QuestLineDatabase.INSTANCE.size() + " quest lines");
        BetterQuesting.logger.info("Loaded " + PartyManager.INSTANCE.size() + " parties");
        BetterQuesting.logger.info("Loaded " + NameCache.INSTANCE.size() + " names");

        dirProgress = new File(BQ_Settings.curWorldDir, "QuestProgress");
        MinecraftForge.EVENT_BUS.post(new DatabaseEvent.Load(DBType.ALL));
    }

    public void saveDatabases() {
        // Forge can save the world during shutdown after a failed startup
        if (dirProgress == null) return;
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

    public synchronized void unloadDatabases() {
        NetDatabaseCache.invalidateServerSnapshot();
        BQ_Settings.curWorldDir = null;
        hasUpdate = false;
        isDirty = false;
        dirProgress = null;
        loadedPlayers.clear();
        loadingPlayers.clear();
        dirtyPlayers.clear();
        failedReads.clear();
        pendingWrites.clear();
        allProgressLoaded = false;
        globalReadFailed = false;

        QuestSettings.INSTANCE.reset();
        QuestDatabase.INSTANCE.clear();
        QuestLineDatabase.INSTANCE.clear();
        LifeDatabase.INSTANCE.reset();
        NameCache.INSTANCE.reset();

        // QuestCache.INSTANCE.reset();

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

        Path database = fileDatabase.toPath();
        if (!Files.notExists(database, LinkOption.NOFOLLOW_LINKS)) {
            String buildVer = QuestCommandDefaults.loadWorldDatabase(database);
            try {
                QuestSettings settings = new QuestSettings();
                settings.readFromNBT(QuestCommandDefaults.readDefaultSettings());
                packVer = settings.getProperty(NativeProps.PACK_VER);
                packName = settings.getProperty(NativeProps.PACK_NAME);
            } catch (IOException failure) {
                BetterQuesting.logger.error("Could not read default quest settings", failure);
            }

            String currVer = Loader.instance()
                .activeModContainer()
                .getVersion();

            if (!currVer.equalsIgnoreCase(buildVer)) // RUN BACKUPS
            {
                String fsVer = JsonHelper.makeFileNameSafe(buildVer);

                if (fsVer.isEmpty()) {
                    fsVer = "pre-251";
                }

                BetterQuesting.logger.warn(
                    "BetterQuesting has been updated to from \"" + fsVer
                        + "\" to \""
                        + currVer
                        + "\"! Creating backups...");

                JsonHelper.CopyPaste(
                    fileDatabase,
                    new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "QuestDatabase_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(
                    fileParties,
                    new File(
                        BQ_Settings.curWorldDir + "/backup/" + fsVer,
                        "QuestingParties_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(
                    fileNames,
                    new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "NameCache_backup_" + fsVer + ".json"));
                JsonHelper.CopyPaste(
                    fileLives,
                    new File(BQ_Settings.curWorldDir + "/backup/" + fsVer, "LifeDatabase_backup_" + fsVer + ".json"));
            }
        } else { // LOAD DEFAULTS
            isDirty = QuestCommandDefaults.loadDefaults(null, true);
            QuestSettings.INSTANCE.setProperty(NativeProps.EDIT_MODE, false); // Force edit off
        }

        hasUpdate = packName.equals(QuestSettings.INSTANCE.getProperty(NativeProps.PACK_NAME))
            && packVer > QuestSettings.INSTANCE.getProperty(NativeProps.PACK_VER);
    }

    public boolean ensureReferencedProgressLoaded(NBTBase progress) {
        if (dirProgress == null) return true;
        Set<UUID> players = new HashSet<>();
        collectProgressPlayers(progress, players);
        return ensurePlayerProgressLoaded(players);
    }

    private static void collectProgressPlayers(NBTBase tag, Set<UUID> players) {
        if (tag instanceof NBTTagCompound compound) {
            if (compound.hasKey("uuid", Constants.NBT.TAG_STRING)) {
                recordProgressPlayer(compound.getString("uuid"), players);
            }
            NBTTagList completed = compound.getTagList("completeUsers", Constants.NBT.TAG_STRING);
            for (int i = 0; i < completed.tagCount(); i++) {
                recordProgressPlayer(completed.getStringTagAt(i), players);
            }
            for (Object key : compound.func_150296_c()) {
                collectProgressPlayers(compound.getTag((String) key), players);
            }
        } else if (tag instanceof NBTTagList list) {
            NBTConverter.getTagList(list)
                .forEach(child -> collectProgressPlayers(child, players));
        }
    }

    private static void recordProgressPlayer(String id, Set<UUID> players) {
        try {
            players.add(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            // Ignore invalid player IDs in typed progress
        }
    }

    /** Loads saved progress for the given player. */
    public boolean ensurePlayerProgressLoaded(UUID player) {
        if (dirProgress == null || loadedPlayers.contains(player)) return true;
        synchronized (this) {
            Long lastFailure = failedReads.get(player);
            if (lastFailure != null && System.nanoTime() - lastFailure < READ_RETRY_NANOS) return false;
            try {
                loadPlayerProgress(player);
                return true;
            } catch (IOException e) {
                if (failedReads.put(player, System.nanoTime()) == null) {
                    BetterQuesting.logger
                        .error("Could not load BetterQuesting progress from {}", playerProgressFile(player), e);
                }
                return false;
            }
        }
    }

    public boolean ensurePlayerProgressLoaded(Collection<UUID> players) {
        for (UUID player : players) {
            if (!ensurePlayerProgressLoaded(player)) return false;
        }
        return true;
    }

    public List<UUID> loadReadablePlayerProgress(Collection<UUID> players) {
        List<UUID> readable = new ArrayList<>(players.size());
        for (UUID player : players) {
            if (ensurePlayerProgressLoaded(player)) readable.add(player);
        }
        return readable;
    }

    public boolean hasFailedProgressRead(UUID player) {
        if (dirProgress == null || loadedPlayers.contains(player)) return false;
        synchronized (this) {
            return failedReads.containsKey(player);
        }
    }

    /** Replaces an unreadable record only after its owner requests recovery. */
    public int recoverPartyProgress(UUID player) throws IOException {
        File directory = dirProgress;
        if (directory == null || loadedPlayers.contains(player)) return -1;
        Lock lock = progressFileLocks.get(player);
        lock.lock();
        try {
            synchronized (this) {
                checkProgressDirectory(directory);
                if (loadedPlayers.contains(player) || !failedReads.containsKey(player)) return -1;
                Path file = playerProgressFile(player);
                byte[] original = Files.readAllBytes(file);
                try (InputStreamReader reader = new InputStreamReader(
                    new ByteArrayInputStream(original),
                    StandardCharsets.UTF_8.newDecoder())) {
                    PlayerProgressJson.readRecord(reader, player);
                } catch (PlayerProgressJson.UnsupportedVersionException failure) {
                    throw failure;
                } catch (IOException invalidRecord) {
                    DBEntry<IParty> party = PartyManager.INSTANCE.getParty(player);
                    if (party == null) throw new IllegalStateException("betterquesting.cmd.recover.no_source");
                    List<UUID> members = new ArrayList<>(
                        party.getValue()
                            .getMembers());
                    members.remove(player);
                    List<UUID> donors = loadReadablePlayerProgress(members);
                    if (donors.isEmpty()) throw new IllegalStateException("betterquesting.cmd.recover.no_source");
                    NBTTagList recovered = QuestDatabase.INSTANCE.recoverPartyCompletions(player, donors);
                    Path backup = Files.createTempFile(file.getParent(), player + ".", ".bak");
                    Files.write(backup, original, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                    writePlayerProgress(directory, file, player, recovered);
                    loadPlayerProgress(player);
                    BetterQuesting.logger.info(
                        "Recovered {} quests for {}. Original progress saved to {}",
                        recovered.tagCount(),
                        player,
                        backup);
                    return recovered.tagCount();
                }
                // The original file may already have been restored since login
                loadPlayerProgress(player);
                return -1;
            }
        } finally {
            lock.unlock();
        }
    }

    private void loadPlayerProgress(UUID player) throws IOException {
        if (dirProgress == null || loadedPlayers.contains(player) || !loadingPlayers.add(player)) return;
        try {
            readPlayerProgress(player);
            loadedPlayers.add(player);
            if (failedReads.remove(player) != null) globalReadFailed = false;
        } catch (RuntimeException e) {
            throw new IOException("Could not apply player progress for " + player, e);
        } finally {
            loadingPlayers.remove(player);
        }
    }

    private void readPlayerProgress(UUID player) throws IOException {
        PlayerProgressJson.ReadResult record;
        try (BufferedReader reader = Files.newBufferedReader(playerProgressFile(player), StandardCharsets.UTF_8)) {
            record = PlayerProgressJson.readRecord(reader, player);
        } catch (NoSuchFileException e) {
            return;
        }

        QuestDatabase.INSTANCE.readLoadedProgressFromNBT(record.progress(), true);
    }

    public boolean canUseQuestProgress(IQuest quest) {
        if (dirProgress == null || allProgressLoaded) return true;
        if (quest.getProperty(NativeProps.GLOBAL)) return ensureAllProgressLoaded();
        for (UUID id : quest.getRequirements()) {
            IQuest parent = QuestDatabase.INSTANCE.get(id);
            if (parent != null && parent.getProperty(NativeProps.GLOBAL)) return ensureAllProgressLoaded();
        }
        return true;
    }

    public synchronized boolean ensureGlobalProgressLoaded() {
        return allProgressLoaded || QuestDatabase.INSTANCE.values()
            .stream()
            .filter(Objects::nonNull)
            .noneMatch(quest -> quest.getProperty(NativeProps.GLOBAL)) || ensureAllProgressLoaded();
    }

    public synchronized boolean ensureAllProgressLoaded() {
        if (dirProgress == null || allProgressLoaded) return true;

        if (globalReadFailed && System.nanoTime() - lastFailedGlobalRead < READ_RETRY_NANOS) return false;
        Set<UUID> players = new HashSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dirProgress.toPath(), "*.json")) {
            for (Path file : files) {
                UUID player = playerId(file);
                if (player != null) players.add(player);
            }
        } catch (NoSuchFileException ignored) {
            // A new world has no progress directory
        } catch (IOException | DirectoryIteratorException e) {
            globalReadFailed = true;
            lastFailedGlobalRead = System.nanoTime();
            BetterQuesting.logger.error("Could not list BetterQuesting player progress", e);
            return false;
        }

        boolean ready = true;
        for (UUID player : players) {
            if (!ensurePlayerProgressLoaded(player)) ready = false;
        }
        globalReadFailed = !ready;
        if (!ready) lastFailedGlobalRead = System.nanoTime();
        allProgressLoaded = ready && loadingPlayers.isEmpty();
        return ready;
    }

    private Path playerProgressFile(UUID player) {
        return dirProgress.toPath()
            .resolve(player.toString() + ".json");
    }

    private static UUID playerId(Path file) {
        String name = file.getFileName()
            .toString();
        if (name.length() != 41 || !name.endsWith(".json")) return null;
        try {
            UUID player = UUID.fromString(name.substring(0, 36));
            return name.equals(player + ".json") ? player : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Converts saved per-player records without loading them into the running quest database.
     */
    public synchronized CompletableFuture<ProgressRewriteResult> rewriteSavedProgress() {
        File directory = dirProgress;
        if (directory == null) throw new IllegalStateException("No BetterQuesting world is loaded");
        if (progressRewrite != null && !progressRewrite.isDone()) {
            throw new IllegalStateException("A BetterQuesting progress migration is already running");
        }

        CompletableFuture<ProgressRewriteResult> result = new CompletableFuture<>();
        BQThreadedIO.DISK_IO.enqueue(() -> {
            try {
                result.complete(rewriteProgressFiles(directory));
            } catch (Exception failure) {
                result.completeExceptionally(failure);
            }
        });
        progressRewrite = result;
        return result;
    }

    private ProgressRewriteResult rewriteProgressFiles(File directory) throws IOException {
        int converted = 0;
        int current = 0;
        List<String> failures = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory.toPath(), "*.json")) {
            for (Path file : files) {
                checkProgressDirectory(directory);
                UUID player = playerId(file);
                if (player == null || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (rewriteProgressFile(directory, file, player)) converted++;
                    else current++;
                } catch (IOException | RuntimeException failure) {
                    checkProgressDirectory(directory);
                    String reason = failure.getMessage();
                    failures.add(
                        file + ": "
                            + (reason == null ? failure.getClass()
                                .getSimpleName() : reason));
                }
            }
        } catch (NoSuchFileException ignored) {
            // A new world has no progress directory
        }
        return new ProgressRewriteResult(converted, current, failures);
    }

    private boolean rewriteProgressFile(File directory, Path file, UUID player) throws IOException {
        Lock lock = progressFileLocks.get(player);
        lock.lock();
        try {
            checkProgressDirectory(directory);
            PlayerProgressJson.ReadResult record;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                record = PlayerProgressJson.readRecord(reader, player);
            }
            if (!record.needsRewrite()) return false;
            writePlayerProgress(directory, file, player, record.progress());
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void checkProgressDirectory(File directory) {
        if (dirProgress != directory) throw new CancellationException("The BetterQuesting world was unloaded");
    }

    public static final class ProgressRewriteResult {

        public final int converted;
        public final int current;
        public final int failed;
        public final List<String> failures;

        private ProgressRewriteResult(int converted, int current, List<String> failures) {
            this.converted = converted;
            this.current = current;
            this.failures = Collections.unmodifiableList(failures);
            this.failed = failures.size();
        }
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
        json.setString(
            "build",
            Loader.instance()
                .activeModContainer()
                .getVersion());

        return JsonHelper.WriteToFile2(fileDatabase, out -> NBTConverter.NBTtoJSON_Compound(json, out, true));
    }

    private synchronized List<Future<Void>> saveProgress() {
        pendingWrites.values()
            .removeIf(Future::isDone);
        Set<UUID> players = new HashSet<>(dirtyPlayers);
        List<Future<Void>> futures = new ArrayList<>(players.size());
        for (UUID player : players) {
            futures.add(savePlayerProgress(player));
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

    public synchronized Future<Void> savePlayerProgress(UUID player) {
        if (dirProgress == null) return CompletableFuture.completedFuture(null);

        Future<Void> previous = pendingWrites.get(player);
        if (previous != null && !previous.isDone()) return previous;
        pendingWrites.remove(player);

        try {
            if (!loadedPlayers.contains(player)) {
                Files.readAttributes(playerProgressFile(player), BasicFileAttributes.class);
                return failedWrite(
                    player,
                    new IOException("Refusing to overwrite unread player progress for " + player));
            }
        } catch (NoSuchFileException ignored) {
            // Start a new file for a player without saved progress
        } catch (IOException e) {
            return failedWrite(player, e);
        }

        if (!ensurePlayerProgressLoaded(player)) {
            return failedWrite(player, new IOException("Cannot save unread player progress for " + player));
        }

        dirtyPlayers.remove(player);
        final NBTTagList progress;
        try {
            progress = QuestDatabase.INSTANCE
                .writeLoadedProgressToNBT(new NBTTagList(), Collections.singletonList(player));
        } catch (RuntimeException e) {
            return failedWrite(player, e);
        }

        File saveDirectory = dirProgress;
        Path destination = playerProgressFile(player);
        Future<Void> write = BQThreadedIO.DISK_IO.enqueue(() -> {
            try {
                Lock lock = progressFileLocks.get(player);
                lock.lock();
                try {
                    writePlayerProgress(saveDirectory, destination, player, progress);
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                synchronized (this) {
                    if (dirProgress == saveDirectory) dirtyPlayers.add(player);
                }
                throw e;
            }
            return null;
        });
        pendingWrites.put(player, write);
        return write;
    }

    private Future<Void> failedWrite(UUID player, Exception e) {
        dirtyPlayers.add(player);
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(e);
        return future;
    }

    private void writePlayerProgress(File saveDirectory, Path destination, UUID player, NBTTagList progress)
        throws IOException {
        Path directory = destination.getParent();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, player.toString(), ".tmp");
        try {
            try (FileOutputStream file = new FileOutputStream(temp.toFile());
                JsonWriter writer = new JsonWriter(
                    new BufferedWriter(new OutputStreamWriter(file, StandardCharsets.UTF_8)))) {
                PlayerProgressJson.write(writer, progress, player);
                writer.flush();
                file.getFD()
                    .sync();
            }
            synchronized (this) {
                checkProgressDirectory(saveDirectory);
                Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
