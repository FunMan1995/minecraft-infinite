package dev.funman.infinite.stack;

import dev.funman.infinite.config.InfiniteConfig;
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
        defaultOverworld = Bukkit.getWorlds().getFirst();
        baseSeed = config.pinnedBaseSeed() != null ? config.pinnedBaseSeed() : defaultOverworld.getSeed();
        World nether = Bukkit.getWorld(defaultOverworld.getName() + "_nether");
        World end = Bukkit.getWorld(defaultOverworld.getName() + "_the_end");
        cache.put(key(0, InfiniteDimension.OVERWORLD), defaultOverworld);
        if (nether != null) {
            cache.put(key(0, InfiniteDimension.NETHER), nether);
        }
        if (end != null) {
            cache.put(key(0, InfiniteDimension.END), end);
        }
        applyBorder(defaultOverworld, InfiniteDimension.OVERWORLD, 0);
        if (nether != null) {
            applyBorder(nether, InfiniteDimension.NETHER, 0);
        }
        if (end != null) {
            applyBorder(end, InfiniteDimension.END, 0);
        }
        plugin.getLogger().info(() -> "Stack origin seed=" + baseSeed
                + " overworldHalf=" + config.overworldHalf()
                + " netherHalf=" + config.netherHalf()
                + " floatCap=" + config.maxAbs()
                + " precision=" + config.minPrecisionBlocks());
    }

    public long baseSeed() {
        return baseSeed;
    }

    public long seedFor(int index) {
        return baseSeed + (long) index;
    }

    public World worldFor(int seedIndex, InfiniteDimension dimension) {
        return cache.computeIfAbsent(key(seedIndex, dimension), ignored -> create(seedIndex, dimension));
    }

    public Located locate(org.bukkit.Location location) {
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
        String name = world.getName();
        Located parsed = parseWorldName(name);
        if (parsed != null) {
            return new Located(parsed.seedIndex(), parsed.dimension(), world);
        }
        // Leftover vanilla "world" folder from before numbered packs.
        if (name.endsWith("_nether")) {
            return new Located(0, InfiniteDimension.NETHER, world);
        }
        if (name.endsWith("_the_end")) {
            return new Located(0, InfiniteDimension.END, world);
        }
        return new Located(0, InfiniteDimension.OVERWORLD, world);
    }

    public boolean isHub(org.bukkit.Location location) {
        return locate(location).seedIndex() == 0;
    }

    private World create(int seedIndex, InfiniteDimension dimension) {
        if (seedIndex == 0) {
            World existing = switch (dimension) {
                case OVERWORLD -> defaultOverworld;
                case NETHER -> Bukkit.getWorld(defaultOverworld.getName() + "_nether");
                case END -> Bukkit.getWorld(defaultOverworld.getName() + "_the_end");
            };
            if (existing != null) {
                applyBorder(existing, dimension, seedIndex);
                return existing;
            }
        }
        String name = worldName(seedIndex, dimension);
        World already = Bukkit.getWorld(name);
        if (already != null) {
            applyBorder(already, dimension, seedIndex);
            return already;
        }
        plugin.getLogger().info("Creating stack world " + name + " seed=" + seedFor(seedIndex));
        WorldCreator creator = new WorldCreator(name);
        creator.seed(seedFor(seedIndex));
        creator.environment(dimension.environment());
        creator.type(WorldType.NORMAL);
        creator.generateStructures(true);
        World created = creator.createWorld();
        if (created == null) {
            throw new IllegalStateException("Failed to create " + name);
        }
        applyBorder(created, dimension, seedIndex);
        return created;
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

    public Location mainSpawn() {
        World overworld = worldFor(0, InfiniteDimension.OVERWORLD);
        return overworld.getSpawnLocation();
    }

    public String worldName(int seedIndex, InfiniteDimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> Integer.toString(seedIndex);
            case NETHER -> seedIndex + "_nether";
            case END -> seedIndex + "_the_end";
        };
    }

    /** `12`, `12_nether`, `-3_the_end` — one pack per seed index. */
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
