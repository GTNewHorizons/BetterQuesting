package betterquesting.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static final String EXTENSION = ".dat";
    // Hashes come from the server and become file names, so only accept a SHA-256 hex digest
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
     * Returns the cached snapshot with this hash, or null if it is missing or damaged
     */
    @Nullable
    public static byte[] load(String hash) {
        File file = getFile(hash);
        if (file == null || !file.isFile()) return null;

        try (InputStream in = new GZIPInputStream(new FileInputStream(file))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            byte[] data = out.toByteArray();

            if (!hash.equals(NetDatabaseCache.hash(data))) {
                BetterQuesting.logger.warn("Cached quest database {} is damaged, deleting it", hash);
                file.delete();
                return null;
            }

            file.setLastModified(System.currentTimeMillis());
            return data;
        } catch (IOException e) {
            BetterQuesting.logger.warn("Unable to read cached quest database " + hash, e);
            file.delete();
            return null;
        }
    }

    /**
     * Writes the snapshot in the background and drops the least recently used ones
     */
    public static void store(String hash, byte[] data) {
        File file = getFile(hash);
        if (file == null) return;

        BQThreadedIO.DISK_IO.enqueue(() -> {
            File dir = getDir();
            File tmp = new File(dir, hash + ".tmp");
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Unable to create " + dir);

                try (OutputStream out = new GZIPOutputStream(new FileOutputStream(tmp))) {
                    out.write(data);
                }
                if (file.exists()) file.delete();
                if (!tmp.renameTo(file)) throw new IOException("Unable to rename " + tmp + " to " + file);
            } catch (IOException e) {
                BetterQuesting.logger.warn("Unable to cache quest database " + hash, e);
                tmp.delete();
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
