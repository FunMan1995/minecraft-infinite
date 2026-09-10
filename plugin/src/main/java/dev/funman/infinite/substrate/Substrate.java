package dev.funman.infinite.substrate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Host registry: unique addresses, import keys, vanilla vs modded packs,
 * seed claims. Subs request a grant from master before they generate, then
 * upload pack info back. Locked rules (hardcore, reincarnate) live here.
 */
public final class Substrate {
    public static final String MASTER = "master";

    public enum Kind {
        VANILLA,
        MODDED
    }

    public record Sub(
            String address,
            Kind kind,
            UUID owner,
            int spawnSeed,
            int reincarnateTimeoutSeconds,
            List<String> mods
    ) {
        public File folder(File root) {
            return new File(new File(root, "subs"), address);
        }
    }

    private final JavaPlugin plugin;
    private final File root;
    private final File registryFile;
    private final File playersFile;
    private final YamlConfiguration registry;
    private final YamlConfiguration players;
    private final int masterTimeoutSeconds;
    private final Map<Integer, String> seedOwner = new ConcurrentHashMap<>();
    private final Map<String, Sub> subs = new ConcurrentHashMap<>();
    private final Map<UUID, String> loginHome = new ConcurrentHashMap<>();

    public Substrate(JavaPlugin plugin, int masterTimeoutSeconds) {
        this.plugin = plugin;
        this.masterTimeoutSeconds = masterTimeoutSeconds;
        this.root = new File(plugin.getServer().getWorldContainer(), "substrate");
        this.registryFile = new File(root, "registry.yml");
        this.playersFile = new File(root, "players.yml");
        this.root.mkdirs();
        new File(root, "subs").mkdirs();
        this.registry = registryFile.exists()
                ? YamlConfiguration.loadConfiguration(registryFile)
                : new YamlConfiguration();
        this.players = playersFile.exists()
                ? YamlConfiguration.loadConfiguration(playersFile)
                : new YamlConfiguration();
        load();
        writeMasterRules();
    }

    public File root() {
        return root;
    }

    public int masterTimeoutSeconds() {
        return masterTimeoutSeconds;
    }

    public Sub sub(String address) {
        return subs.get(normalize(address));
    }

    public Sub ownerOfSeed(int seedIndex) {
        String address = seedOwner.get(seedIndex);
        return address == null ? null : subs.get(address);
    }

    public boolean isSubHome(int seedIndex) {
        Sub sub = ownerOfSeed(seedIndex);
        return sub != null && sub.spawnSeed() == seedIndex;
    }

    public boolean isSubOp(UUID player, int seedIndex) {
        Sub sub = ownerOfSeed(seedIndex);
        return sub != null && sub.owner().equals(player);
    }

    public String loginHome(UUID player) {
        return loginHome.getOrDefault(player, MASTER);
    }

    public void setLoginHome(UUID player, String home) {
        loginHome.put(player, home);
        players.set(player + ".login-home", home);
        savePlayers();
    }

    /**
     * Timer source for a death: master 24h if the blocks are master's, or if
     * the player transferred in from master. Own-sub deaths may use a shorter wait.
     */
    public int timeoutForDeath(UUID player, int deathSeed) {
        Sub land = ownerOfSeed(deathSeed);
        String home = loginHome(player);
        if (land == null || deathSeed == 0) {
            return masterTimeoutSeconds;
        }
        boolean ownSub = land.address().equals(home) || land.owner().equals(player);
        if (!ownSub) {
            return masterTimeoutSeconds;
        }
        return Math.min(masterTimeoutSeconds, Math.max(60, land.reincarnateTimeoutSeconds()));
    }

    public String timerSource(UUID player, int deathSeed) {
        int timeout = timeoutForDeath(player, deathSeed);
        if (timeout >= masterTimeoutSeconds) {
            return MASTER;
        }
        Sub land = ownerOfSeed(deathSeed);
        return land == null ? MASTER : land.address();
    }

    /**
     * Sub requests a grant from master before generating worlds. Returns the
     * plaintext key once; later starts use the stored file.
     */
    public String request(UUID owner, String wanted, Kind kind, List<String> mods, int spawnSeed) {
        String address = allocate(wanted);
        if (ownerOfSeed(spawnSeed) != null && !owner.equals(ownerOfSeed(spawnSeed).owner())) {
            throw new IllegalStateException("Seed " + spawnSeed + " is already claimed.");
        }
        if (spawnSeed == 0) {
            throw new IllegalStateException("Seed 0 is the master hub and cannot be claimed.");
        }
        String key = HexFormat.of().formatHex(randomKey());
        Sub sub = new Sub(
                address,
                kind,
                owner,
                spawnSeed,
                masterTimeoutSeconds,
                List.copyOf(mods)
        );
        File folder = sub.folder(root);
        new File(folder, "mods").mkdirs();
        new File(folder, "worlds/Home").mkdirs();
        try {
            Files.writeString(new File(folder, "key").toPath(), key + "\n");
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write sub key", ex);
        }
        snapshotMods(folder, mods);
        subs.put(address, sub);
        seedOwner.put(spawnSeed, address);
        persistSub(sub);
        saveRegistry();
        return key;
    }

    public void upload(String address, List<String> mods) {
        Sub current = sub(address);
        if (current == null) {
            throw new IllegalStateException("Unknown address " + address);
        }
        Sub updated = new Sub(
                current.address(),
                current.kind(),
                current.owner(),
                current.spawnSeed(),
                current.reincarnateTimeoutSeconds(),
                List.copyOf(mods)
        );
        subs.put(address, updated);
        snapshotMods(updated.folder(root), mods);
        persistSub(updated);
        saveRegistry();
    }

    public void setSubTimeout(String address, UUID actor, int seconds) {
        Sub current = sub(address);
        if (current == null || !current.owner().equals(actor)) {
            throw new IllegalStateException("Only that sub's operator can change its reincarnate wait.");
        }
        int capped = Math.min(masterTimeoutSeconds, Math.max(60, seconds));
        Sub updated = new Sub(
                current.address(),
                current.kind(),
                current.owner(),
                current.spawnSeed(),
                capped,
                current.mods()
        );
        subs.put(address, updated);
        persistSub(updated);
        saveRegistry();
    }

    public boolean keyMatches(String address, String presented) {
        Sub sub = sub(address);
        if (sub == null || presented == null) {
            return false;
        }
        File keyFile = new File(sub.folder(root), "key");
        if (!keyFile.isFile()) {
            return false;
        }
        try {
            String stored = Files.readString(keyFile.toPath()).trim();
            return stored.equals(presented.trim());
        } catch (IOException ex) {
            return false;
        }
    }

    private String allocate(String wanted) {
        String address = normalize(wanted);
        if (address.isEmpty() || MASTER.equals(address)) {
            throw new IllegalStateException("Invalid address.");
        }
        if (!address.matches("[a-z0-9][a-z0-9-]{1,22}")) {
            throw new IllegalStateException("Address must be 2-23 letters, digits, or dashes.");
        }
        if (subs.containsKey(address)) {
            throw new IllegalStateException("Address already taken.");
        }
        return address;
    }

    private static String normalize(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    private static byte[] randomKey() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private void snapshotMods(File folder, List<String> mods) {
        File list = new File(new File(folder, "mods"), "in-use.txt");
        try {
            Files.write(list.toPath(), mods);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not snapshot mods", ex);
        }
    }

    private void persistSub(Sub sub) {
        String path = "subs." + sub.address();
        registry.set(path + ".kind", sub.kind().name());
        registry.set(path + ".owner", sub.owner().toString());
        registry.set(path + ".spawn-seed", sub.spawnSeed());
        registry.set(path + ".reincarnate-timeout-seconds", sub.reincarnateTimeoutSeconds());
        registry.set(path + ".mods", sub.mods());
        registry.set("seeds." + sub.spawnSeed(), sub.address());
    }

    private void load() {
        ConfigurationSection seedSec = registry.getConfigurationSection("seeds");
        if (seedSec != null) {
            for (String key : seedSec.getKeys(false)) {
                try {
                    seedOwner.put(Integer.parseInt(key), seedSec.getString(key));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        ConfigurationSection subSec = registry.getConfigurationSection("subs");
        if (subSec != null) {
            for (String address : subSec.getKeys(false)) {
                ConfigurationSection s = subSec.getConfigurationSection(address);
                if (s == null) {
                    continue;
                }
                UUID owner;
                try {
                    owner = UUID.fromString(s.getString("owner", ""));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Kind kind;
                try {
                    kind = Kind.valueOf(s.getString("kind", "VANILLA"));
                } catch (IllegalArgumentException ex) {
                    kind = Kind.VANILLA;
                }
                List<String> mods = s.getStringList("mods");
                Sub sub = new Sub(
                        address,
                        kind,
                        owner,
                        s.getInt("spawn-seed"),
                        s.getInt("reincarnate-timeout-seconds", masterTimeoutSeconds),
                        mods
                );
                subs.put(address, sub);
                seedOwner.put(sub.spawnSeed(), address);
            }
        }
        if (players.getKeys(false) != null) {
            for (String id : players.getKeys(false)) {
                try {
                    loginHome.put(UUID.fromString(id), players.getString(id + ".login-home", MASTER));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
    }

    private void writeMasterRules() {
        File master = new File(root, "master.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hardcore", true);
        yaml.set("reincarnate-timeout-seconds", masterTimeoutSeconds);
        yaml.set("allow-sub-shorter-timeout", true);
        yaml.set("sub-cannot-lengthen-past-master", true);
        yaml.set("sub-cannot-disable-hardcore", true);
        yaml.set("sub-cannot-disable-reincarnate", true);
        try {
            yaml.save(master);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write master.yml", ex);
        }
    }

    private void saveRegistry() {
        try {
            registry.save(registryFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save registry.yml", ex);
        }
    }

    private void savePlayers() {
        try {
            players.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save players.yml", ex);
        }
    }
}
