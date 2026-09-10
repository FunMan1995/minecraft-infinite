package dev.funman.infinite.stack;

import dev.funman.infinite.config.InfiniteConfig;
import dev.funman.infinite.worldgen.BlankChunkGenerator;
import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * One vanilla world save per seed index. Overworld, Nether, and End live in
 * {@code <index>/dimensions/minecraft/{overworld,the_nether,the_end}} like
 * vanilla 26.x — not three sibling packs and not biomes in one dimension.
 */
public final class StackWorlds {
    private final JavaPlugin plugin;
    private final InfiniteConfig config;
    private final Map<String, World> cache = new ConcurrentHashMap<>();
    private long baseSeed;
    private World defaultOverworld;

    public StackWorlds(JavaPlugin plugin, InfiniteConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void attachDefaultWorlds() {
        defaultOverworld = findLoaded(0, InfiniteDimension.OVERWORLD);
        if (defaultOverworld == null) {
            defaultOverworld = Bukkit.getWorlds().getFirst();
        }
        baseSeed = config.pinnedBaseSeed() != null ? config.pinnedBaseSeed() : defaultOverworld.getSeed();
        remember(0, InfiniteDimension.OVERWORLD, defaultOverworld);
        applyBorder(defaultOverworld, InfiniteDimension.OVERWORLD, 0);
        for (InfiniteDimension dim : new InfiniteDimension[] {InfiniteDimension.NETHER, InfiniteDimension.END}) {
            World extra = findLoaded(0, dim);
            if (extra != null) {
                remember(0, dim, extra);
                applyBorder(extra, dim, 0);
            }
        }
        plugin.getLogger().info(() -> "Seed pack 0 folder=" + packRoot(defaultOverworld)
                + " seed=" + baseSeed
                + " worlds=" + Bukkit.getWorlds().stream().map(w -> w.getName() + "/" + w.getEnvironment()).toList());
    }

    public long baseSeed() {
        return baseSeed;
    }

    public long seedFor(int index) {
        return baseSeed + (long) index;
    }

    public String packName(int seedIndex) {
        return Integer.toString(seedIndex);
    }

    public World worldFor(int seedIndex, InfiniteDimension dimension) {
        return cache.computeIfAbsent(key(seedIndex, dimension), ignored -> loadDimension(seedIndex, dimension));
    }

    public World find(int seedIndex, InfiniteDimension dimension) {
        World cached = cache.get(key(seedIndex, dimension));
        if (cached != null) {
            return cached;
        }
        return findLoaded(seedIndex, dimension);
    }

    public Located locate(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return new Located(0, InfiniteDimension.OVERWORLD, defaultOverworld);
        }
        for (Map.Entry<String, World> entry : cache.entrySet()) {
            if (entry.getValue().equals(world)) {
                ParsedKey parsed = parseKey(entry.getKey());
                return new Located(parsed.index, parsed.dimension, world);
            }
        }
        int index = seedIndexOf(world);
        return new Located(index, InfiniteDimension.of(world.getEnvironment()), world);
    }

    public boolean isHub(Location location) {
        return locate(location).seedIndex() == 0;
    }

    public Location mainSpawn() {
        World overworld = worldFor(0, InfiniteDimension.OVERWORLD);
        Location spawn = overworld.getSpawnLocation();
        if (spawn.getY() < overworld.getMinHeight() + 2) {
            spawn = new Location(overworld, 8.5, 65, 8.5);
        }
        return spawn;
    }

    /** Bukkit still names dimensions; the save on disk is the numbered pack folder. */
    public String worldName(int seedIndex, InfiniteDimension dimension) {
        return packName(seedIndex);
    }

    private World loadDimension(int seedIndex, InfiniteDimension dimension) {
        ensurePack(seedIndex);
        World loaded = findLoaded(seedIndex, dimension);
        if (loaded != null) {
            applyBorder(loaded, dimension, seedIndex);
            return loaded;
        }
        throw new IllegalStateException("Dimension " + dimension + " missing from seed pack " + seedIndex
                + " (expected " + packName(seedIndex) + "/dimensions/minecraft/"
                + dimension.worldSuffix() + " in the same save as the Overworld)");
    }

    private void ensurePack(int seedIndex) {
        if (findLoaded(seedIndex, InfiniteDimension.OVERWORLD) != null) {
            return;
        }
        String pack = packName(seedIndex);
        plugin.getLogger().info("Loading seed pack " + pack + " (vanilla overworld+nether+end in one save)");
        WorldCreator creator = new WorldCreator(pack);
        creator.seed(seedFor(seedIndex));
        creator.environment(World.Environment.NORMAL);
        if (seedIndex == 0) {
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.generator(new BlankChunkGenerator());
        } else {
            creator.type(WorldType.NORMAL);
            creator.generateStructures(true);
        }
        World created = creator.createWorld();
        if (created == null) {
            throw new IllegalStateException("Failed to create seed pack " + pack);
        }
        remember(seedIndex, InfiniteDimension.OVERWORLD, created);
        applyBorder(created, InfiniteDimension.OVERWORLD, seedIndex);
    }

    private World findLoaded(int seedIndex, InfiniteDimension dimension) {
        for (World world : Bukkit.getWorlds()) {
            if (InfiniteDimension.of(world.getEnvironment()) != dimension) {
                continue;
            }
            if (seedIndexOf(world) == seedIndex) {
                remember(seedIndex, dimension, world);
                return world;
            }
        }
        return null;
    }

    private int seedIndexOf(World world) {
        File root = packRoot(world);
        if (root != null) {
            try {
                return Integer.parseInt(root.getName());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        Located parsed = parseWorldName(world.getName());
        if (parsed != null) {
            return parsed.seedIndex();
        }
        return 0;
    }

    static File packRoot(World world) {
        File folder = world.getWorldFolder().getAbsoluteFile();
        File cursor = folder;
        for (int i = 0; i < 8 && cursor != null; i++) {
            if (new File(cursor, "level.dat").exists()) {
                return cursor;
            }
            cursor = cursor.getParentFile();
        }
        return folder;
    }

    private void remember(int seedIndex, InfiniteDimension dimension, World world) {
        cache.put(key(seedIndex, dimension), world);
    }

    public void applyBorder(World world, InfiniteDimension dimension, int seedIndex) {
        int half = config.halfFor(dimension);
        int diameter = 2 * (half + config.overhangBlocks());
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(diameter);
        border.setDamageAmount(0.0);
        border.setDamageBuffer(config.overhangBlocks());
        border.setWarningDistance(0);
        if (seedIndex == 0) {
            world.setPVP(false);
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setHardcore(false);
        } else if (config.hardcore()) {
            world.setHardcore(true);
            world.setDifficulty(Difficulty.HARD);
            world.setPVP(true);
        }
    }

    /** `0`, leftover `0_nether` API names, etc. */
    public static Located parseWorldName(String name) {
        InfiniteDimension dim = InfiniteDimension.OVERWORLD;
        String indexPart = name;
        if (name.endsWith("_the_end")) {
            dim = InfiniteDimension.END;
            indexPart = name.substring(0, name.length() - "_the_end".length());
        } else if (name.endsWith("_nether")) {
            dim = InfiniteDimension.NETHER;
            indexPart = name.substring(0, name.length() - "_nether".length());
        }
        try {
            return new Located(Integer.parseInt(indexPart), dim, null);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String key(int seedIndex, InfiniteDimension dimension) {
        return seedIndex + ":" + dimension.name().toLowerCase(Locale.ROOT);
    }

    private static ParsedKey parseKey(String key) {
        int split = key.indexOf(':');
        int index = Integer.parseInt(key.substring(0, split));
        InfiniteDimension dim = InfiniteDimension.valueOf(key.substring(split + 1).toUpperCase(Locale.ROOT));
        return new ParsedKey(index, dim);
    }

    public record Located(int seedIndex, InfiniteDimension dimension, World world) {}

    private record ParsedKey(int index, InfiniteDimension dimension) {}
}
