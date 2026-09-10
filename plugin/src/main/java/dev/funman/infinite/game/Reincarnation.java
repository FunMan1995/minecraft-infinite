package dev.funman.infinite.game;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.substrate.Substrate;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hardcore death stays spectator. Stuff is in the grave. After a wall-clock
 * wait (24h on master; subs may shorten only for their own seeds), a
 * Reincarnate item appears in spectator inventory — even if they were offline
 * the whole time. Using it wipes play-settings and sends them to login home.
 */
public final class Reincarnation implements Listener {
    private final JavaPlugin plugin;
    private final Substrate substrate;
    private final StackWorlds worlds;
    private final HomeStore homes;
    private final NamespacedKey itemKey;
    private final File file;
    private final YamlConfiguration yaml;

    public Reincarnation(JavaPlugin plugin, Substrate substrate, StackWorlds worlds, HomeStore homes) {
        this.plugin = plugin;
        this.substrate = substrate;
        this.worlds = worlds;
        this.homes = homes;
        this.itemKey = new NamespacedKey(plugin, "reincarnate");
        this.file = new File(substrate.root(), "deaths.yml");
        this.yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickOnline, 20L, 20L * 15);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        int seed = worlds.locate(player.getLocation()).seedIndex();
        int timeout = substrate.timeoutForDeath(player.getUniqueId(), seed);
        String source = substrate.timerSource(player.getUniqueId(), seed);
        String path = path(player.getUniqueId());
        yaml.set(path + ".died-at", System.currentTimeMillis());
        yaml.set(path + ".timeout-ms", timeout * 1000L);
        yaml.set(path + ".source", source);
        yaml.set(path + ".login-home", substrate.loginHome(player.getUniqueId()));
        yaml.set(path + ".seed", seed);
        yaml.set(path + ".item-given", false);
        save();
        player.sendMessage(Component.text(
                "You are a spectator. A gravestone holds your items. Reincarnate in "
                        + format(timeout * 1000L) + " (" + source + " timer).",
                NamedTextColor.RED
        ));
        if (Substrate.MASTER.equals(source)) {
            player.sendMessage(Component.text(
                    "This wait is the master rule. A sub cannot shorten it for main-server deaths.",
                    NamedTextColor.DARK_GRAY
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!yaml.contains(path(player.getUniqueId()) + ".died-at")) {
            event.setRespawnLocation(worlds.mainSpawn());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            grantIfReady(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!event.getPlayer().hasPlayedBefore()) {
            substrate.setLoginHome(player.getUniqueId(), Substrate.MASTER);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> grantIfReady(player));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (!isItem(event.getItem()) && !isItem(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        if (!ready(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        reincarnate(event.getPlayer());
    }

    public boolean ready(UUID id) {
        String path = path(id);
        if (!yaml.contains(path + ".died-at")) {
            return false;
        }
        long died = yaml.getLong(path + ".died-at");
        long timeout = yaml.getLong(path + ".timeout-ms");
        return System.currentTimeMillis() >= died + timeout;
    }

    public long remainingMs(UUID id) {
        String path = path(id);
        if (!yaml.contains(path + ".died-at")) {
            return 0;
        }
        long readyAt = yaml.getLong(path + ".died-at") + yaml.getLong(path + ".timeout-ms");
        return Math.max(0, readyAt - System.currentTimeMillis());
    }

    public void reincarnate(Player player) {
        UUID id = player.getUniqueId();
        if (!yaml.contains(path(id) + ".died-at")) {
            player.sendMessage(Component.text("You are not waiting to reincarnate.", NamedTextColor.RED));
            return;
        }
        if (!ready(id)) {
            player.sendMessage(Component.text(
                    "Reincarnate is locked for " + format(remainingMs(id)) + ".",
                    NamedTextColor.RED
            ));
            return;
        }
        String home = yaml.getString(path(id) + ".login-home", substrate.loginHome(id));
        yaml.set(path(id), null);
        save();
        stripItem(player);
        player.getInventory().clear();
        homes.clear(id);
        player.getEnderChest().clear();
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);
        player.setFireTicks(0);
        player.clearActivePotionEffects();
        player.setGameMode(GameMode.SURVIVAL);
        Location dest = destination(home);
        player.teleport(dest);
        player.sendMessage(Component.text("Reincarnated at " + home + ".", NamedTextColor.GREEN));
    }

    private Location destination(String home) {
        if (home == null || Substrate.MASTER.equals(home)) {
            return worlds.mainSpawn();
        }
        Substrate.Sub sub = substrate.sub(home);
        if (sub == null) {
            return worlds.mainSpawn();
        }
        return worlds.worldFor(sub.spawnSeed(), InfiniteDimension.OVERWORLD).getSpawnLocation();
    }

    private void tickOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!yaml.contains(path(player.getUniqueId()) + ".died-at")) {
                continue;
            }
            if (player.getGameMode() == GameMode.SPECTATOR && !ready(player.getUniqueId())) {
                player.sendActionBar(Component.text(
                        "Reincarnate in " + format(remainingMs(player.getUniqueId())),
                        NamedTextColor.DARK_AQUA
                ));
            }
            grantIfReady(player);
        }
    }

    private void grantIfReady(Player player) {
        UUID id = player.getUniqueId();
        if (!ready(id)) {
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        if (!yaml.getBoolean(path(id) + ".item-given", false) || !hasItem(player)) {
            giveItem(player);
            yaml.set(path(id) + ".item-given", true);
            save();
            player.sendMessage(Component.text("Reincarnate is ready.", NamedTextColor.AQUA)
                    .append(Component.text(" Use the item or ", NamedTextColor.GRAY))
                    .append(Component.text("/reincarnate", NamedTextColor.YELLOW)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/reincarnate"))));
        }
    }

    private void giveItem(Player player) {
        stripItem(player);
        player.getInventory().setItem(0, item());
    }

    private ItemStack item() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("Reincarnate", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Return to your login home.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Clears play settings. Graves stay.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Byte flag = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    private boolean hasItem(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private void stripItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    private static String path(UUID id) {
        return id.toString();
    }

    static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return h + "h " + m + "m";
        }
        if (m > 0) {
            return m + "m " + sec + "s";
        }
        return sec + "s";
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save deaths.yml", ex);
        }
    }
}
