package dev.funman.infinite.game;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackCoord;
import dev.funman.infinite.stack.StackWorlds;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HomeStore {
    private final JavaPlugin plugin;
    private final StackWorlds worlds;
    private final File file;
    private final YamlConfiguration yaml;
    private final int maxHomes;

    public HomeStore(JavaPlugin plugin, StackWorlds worlds, int maxHomes) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.maxHomes = maxHomes;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    public int maxHomes() {
        return maxHomes;
    }

    public int count(UUID player) {
        var section = yaml.getConfigurationSection(player.toString());
        return section == null ? 0 : section.getKeys(false).size();
    }

    public boolean has(UUID player, String name) {
        return yaml.contains(path(player, name));
    }

    public void set(Player player, String name, Location location) {
        StackWorlds.Located located = worlds.locate(location);
        String base = path(player.getUniqueId(), name);
        yaml.set(base + ".seed", located.seedIndex());
        yaml.set(base + ".dim", located.dimension().name());
        yaml.set(base + ".x", location.getX());
        yaml.set(base + ".y", location.getY());
        yaml.set(base + ".z", location.getZ());
        yaml.set(base + ".yaw", location.getYaw());
        yaml.set(base + ".pitch", location.getPitch());
        save();
    }

    public Location get(UUID player, String name) {
        String base = path(player, name);
        if (!yaml.contains(base)) {
            return null;
        }
        int seed = yaml.getInt(base + ".seed");
        InfiniteDimension dim = InfiniteDimension.valueOf(yaml.getString(base + ".dim", "OVERWORLD"));
        StackCoord coord = new StackCoord(
                seed,
                dim,
                yaml.getDouble(base + ".x"),
                yaml.getDouble(base + ".y"),
                yaml.getDouble(base + ".z"),
                (float) yaml.getDouble(base + ".yaw"),
                (float) yaml.getDouble(base + ".pitch")
        );
        return coord.toLocation(worlds.worldFor(seed, dim));
    }

    private static String path(UUID player, String name) {
        return player + "." + name.toLowerCase(Locale.ROOT);
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save homes.yml", ex);
        }
    }
}
