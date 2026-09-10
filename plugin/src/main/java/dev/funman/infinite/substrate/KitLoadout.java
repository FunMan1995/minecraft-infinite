package dev.funman.infinite.substrate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Personal client-mod kit on a player UUID: toggle, private/public, grab from
 * an area. Integral names are forced public so they apply on world generate.
 */
public final class KitLoadout {
    public record Entry(String file, boolean enabled, boolean pub, boolean integral) {}

    private final JavaPlugin plugin;
    private final Substrate substrate;
    private final Set<String> integralNames;

    public KitLoadout(JavaPlugin plugin, Substrate substrate, List<String> integralNames) {
        this.plugin = plugin;
        this.substrate = substrate;
        this.integralNames = integralNames.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public List<Entry> list(UUID clientId) {
        File mods = modsDir(clientId);
        mods.mkdirs();
        YamlConfiguration yaml = yaml(clientId);
        File[] jars = mods.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        List<Entry> out = new ArrayList<>();
        if (jars != null) {
            for (File jar : jars) {
                out.add(read(yaml, jar.getName()));
            }
        }
        save(clientId, yaml, out);
        return out;
    }

    public Entry toggle(UUID clientId, String file) {
        YamlConfiguration yaml = yaml(clientId);
        Entry cur = read(yaml, file);
        Entry next = new Entry(cur.file(), !cur.enabled(), cur.pub(), cur.integral());
        write(yaml, next);
        save(clientId, yaml, null);
        return next;
    }

    public Entry setPublic(UUID clientId, String file, boolean pub) {
        YamlConfiguration yaml = yaml(clientId);
        Entry cur = read(yaml, file);
        if (cur.integral() && !pub) {
            throw new IllegalStateException(file + " is integral and must stay public.");
        }
        Entry next = new Entry(cur.file(), cur.enabled(), pub, cur.integral());
        write(yaml, next);
        save(clientId, yaml, null);
        return next;
    }

    /** Copy public (and integral) client-mods from the seed's tree into this kit. */
    public int grab(UUID clientId, int seed) {
        File src = substrate.clientModsForSeed(seed);
        if (src == null || !src.isDirectory()) {
            throw new IllegalStateException("This seed has no client-mods to grab.");
        }
        File dest = modsDir(clientId);
        dest.mkdirs();
        YamlConfiguration yaml = yaml(clientId);
        File[] jars = src.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
        int n = 0;
        if (jars != null) {
            for (File jar : jars) {
                Entry srcMeta = visibilityFromOwner(seed, jar.getName());
                if (!srcMeta.pub() && !srcMeta.integral()) {
                    continue;
                }
                try {
                    Files.copy(jar.toPath(), new File(dest, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    Entry got = read(yaml, jar.getName());
                    write(yaml, new Entry(got.file(), true, srcMeta.integral() || srcMeta.pub(), srcMeta.integral()));
                    n++;
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.WARNING, "Kit grab failed for " + jar.getName(), ex);
                }
            }
        }
        save(clientId, yaml, null);
        applyIntegralToWorld(clientId);
        return n;
    }

    /** Mods others may download, and that apply to world generation. */
    public List<String> publicEnabled(UUID clientId) {
        return list(clientId).stream()
                .filter(e -> e.enabled() && (e.pub() || e.integral()))
                .map(Entry::file)
                .toList();
    }

    public boolean isPublic(UUID clientId, String file) {
        Entry e = read(yaml(clientId), file);
        return e.pub() || e.integral();
    }

    private Entry read(YamlConfiguration yaml, String file) {
        boolean integral = isIntegral(file);
        ConfigurationSection s = yaml.getConfigurationSection("mods." + safe(file));
        boolean enabled = s == null || s.getBoolean("enabled", true);
        boolean pub = integral || (s != null && s.getBoolean("public", false));
        if (s == null) {
            pub = integral;
        }
        return new Entry(file, enabled, pub, integral);
    }

    private Entry visibilityFromOwner(int seed, String file) {
        Substrate.Kit kit = substrate.kitOfSeed(seed);
        if (kit != null) {
            return read(yaml(kit.clientId()), file);
        }
        return new Entry(file, true, true, isIntegral(file));
    }

    private void write(YamlConfiguration yaml, Entry entry) {
        String path = "mods." + safe(entry.file());
        yaml.set(path + ".file", entry.file());
        yaml.set(path + ".enabled", entry.enabled());
        yaml.set(path + ".public", entry.integral() || entry.pub());
        yaml.set(path + ".integral", entry.integral());
    }

    private void applyIntegralToWorld(UUID clientId) {
        Substrate.Kit kit = substrate.kit(clientId);
        if (kit == null) {
            return;
        }
        File world = new File(kit.worlds(substrate.host()), Integer.toString(kit.spawnSeed()));
        world.mkdirs();
        List<String> apply = list(clientId).stream()
                .filter(e -> e.enabled() && e.integral())
                .map(Entry::file)
                .toList();
        try {
            Files.write(new File(world, "applied-integral-mods.txt").toPath(), apply);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write applied-integral-mods.txt", ex);
        }
    }

    private boolean isIntegral(String file) {
        String n = file.toLowerCase(Locale.ROOT);
        return integralNames.contains(n) || integralNames.stream().anyMatch(n::contains);
    }

    private File modsDir(UUID clientId) {
        return new File(new File(new File(substrate.host(), "kits"), clientId.toString()), "client-mods");
    }

    private YamlConfiguration yaml(UUID clientId) {
        File file = new File(new File(new File(substrate.host(), "kits"), clientId.toString()), "settings.yml");
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void save(UUID clientId, YamlConfiguration yaml, List<Entry> sync) {
        if (sync != null) {
            for (Entry e : sync) {
                write(yaml, e);
            }
        }
        File file = new File(new File(new File(substrate.host(), "kits"), clientId.toString()), "settings.yml");
        file.getParentFile().mkdirs();
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save kit settings", ex);
        }
    }

    private static String safe(String file) {
        return file.replace('.', '_');
    }
}
