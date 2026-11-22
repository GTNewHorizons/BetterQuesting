package betterquesting.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import betterquesting.api2.utils.BQThreadedIO;
import betterquesting.core.BetterQuesting;
import betterquesting.network.handlers.NetDatabaseCache;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Quest database snapshots received from servers, stored by hash so servers running the same quest book share them.
 */
@SideOnly(Side.CLIENT)
public class QuestDatabaseCache {

    private static final int MAX_ENTRIES = 4;
    private static final int READ_BUFFER_BYTES = 64 * 1024;
    private static final String EXTENSION = ".dat";
    // Use only SHA-256 hex digests as cache file names
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private static File getDir() {
        return new File(Minecraft.getMinecraft().mcDataDir, "betterquesting/cache");
    }

    @Nullable
    private static File getFile(String hash) {
        return HASH.matcher(hash)
            .matches() ? new File(getDir(), hash + EXTENSION) : null;
    }

    private static List<File> listFiles() {
        File[] files = getDir().listFiles(
            (dir, name) -> name.endsWith(EXTENSION)
                && HASH.matcher(name.substring(0, name.length() - EXTENSION.length()))
                    .matches());
        if (files == null) return Collections.emptyList();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        list.sort(
            Comparator.comparingLong(File::lastModified)
                .reversed());
        return list;
    }

    /**
     * Hashes of the cached snapshots, most recently used first
     */
    public static List<String> listHashes() {
        List<String> hashes = new ArrayList<>();
        for (File file : listFiles()) {
            String name = file.getName();
            hashes.add(name.substring(0, name.length() - EXTENSION.length()));
        }
        return hashes;
    }

    /**
     * Returns the cached snapshot with this hash, or null for a missing or damaged file.
     */
    @Nullable
    public static byte[] load(String hash) {
        File file = getFile(hash);
        if (file == null || !file.isFile()) return null;

        byte[] data;
        try (InputStream raw = new FileInputStream(file); InputStream in = new GZIPInputStream(raw)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[READ_BUFFER_BYTES];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            data = out.toByteArray();
        } catch (IOException e) {
            BetterQuesting.logger.warn("Unable to read cached quest database {}", hash, e);
            file.delete();
            return null;
        }
        if (!hash.equals(NetDatabaseCache.hash(data))) {
            BetterQuesting.logger.warn("Cached quest database {} is damaged, deleting it", hash);
            file.delete();
            return null;
        }
        file.setLastModified(System.currentTimeMillis());
        return data;
    }

    /**
     * Writes the snapshot in the background and drops the least recently used ones
     */
    public static void store(String hash, byte[] data) {
        File file = getFile(hash);
        if (file == null) return;

        BQThreadedIO.DISK_IO.enqueue(() -> {
            try {
                Path directory = Files.createDirectories(
                    file.toPath()
                        .getParent());
                Path temp = Files.createTempFile(directory, hash, ".tmp");
                try {
                    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temp))) {
                        out.write(data);
                    }
                    Files
                        .move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (IOException e) {
                BetterQuesting.logger.warn("Unable to cache quest database {}", hash, e);
                return;
            }

            List<File> files = listFiles();
            for (int i = MAX_ENTRIES; i < files.size(); i++) {
                files.get(i)
                    .delete();
            }
        });
    }
}
