package dev.funman.infinite.substrate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * A seed's files live in vanilla/, subs/&lt;name&gt;/worlds/, or kits/&lt;uuid&gt;/worlds/.
 * Paper still loads through vanilla/ via a symlink so the seed exists where it belongs.
 */
public final class WorldMover {
    private WorldMover() {}

    public static void relocate(File vanillaDir, int seed, File destWorldDir) throws IOException {
        String name = Integer.toString(seed);
        File vanillaWorld = new File(vanillaDir, name);
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            loaded.save();
            Bukkit.unloadWorld(loaded, true);
        }
        destWorldDir.getParentFile().mkdirs();
        if (vanillaWorld.exists() && Files.isSymbolicLink(vanillaWorld.toPath())) {
            return;
        }
        if (vanillaWorld.exists() && !vanillaWorld.equals(destWorldDir)) {
            if (destWorldDir.exists() && !isNonEmpty(destWorldDir)) {
                Files.delete(destWorldDir.toPath());
            }
            if (!destWorldDir.exists()) {
                Files.move(vanillaWorld.toPath(), destWorldDir.toPath());
            }
        } else {
            destWorldDir.mkdirs();
        }
        link(vanillaWorld.toPath(), destWorldDir.toPath());
    }

    public static void link(Path link, Path target) throws IOException {
        Files.createDirectories(target);
        if (Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(link)) {
                Files.delete(link);
            } else {
                return;
            }
        }
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, link.getParent().relativize(target));
    }

    private static boolean isNonEmpty(File dir) {
        String[] names = dir.list();
        return names != null && names.length > 0;
    }
}
