package dev.funman.infinite.game;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackWorlds;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sideways seed travel (same plane) requires this seed's dragon dead, or a
 * dragon egg in inventory. Vertical stack travel is never gated.
 */
public final class DragonGate implements Listener {
    private final JavaPlugin plugin;
    private final StackWorlds worlds;
    private final File file;
    private final Set<Integer> defeated = new HashSet<>();

    public DragonGate(JavaPlugin plugin, StackWorlds worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
        this.file = new File(plugin.getDataFolder(), "dragons.yml");
        load();
    }

    public boolean canCrossSideways(Player player, int fromSeed) {
        if (defeated.contains(fromSeed) || battleKilled(fromSeed)) {
            return true;
        }
        return hasDragonEgg(player);
    }

    public void denyMessage(Player player) {
        player.sendMessage(Component.text(
                "Sideways travel to another seed is locked until this world's Ender Dragon is defeated, or you carry a dragon egg.",
                NamedTextColor.RED
        ));
        player.sendMessage(Component.text(
                "Vertical travel through the stack stays open.",
                NamedTextColor.DARK_GRAY
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }
        if (event.getEntity().getWorld() == null) {
            return;
        }
        int seed = worlds.locate(event.getEntity().getLocation()).seedIndex();
        if (defeated.add(seed)) {
            save();
            Player killer = event.getEntity().getKiller();
            Component msg = Component.text(
                    "The Ender Dragon of seed " + seed + " is dead. Sideways travel from this world is unlocked.",
                    NamedTextColor.LIGHT_PURPLE
            );
            if (killer != null) {
                killer.sendMessage(msg);
            } else {
                plugin.getServer().broadcast(msg);
            }
        }
    }

    private boolean battleKilled(int seed) {
        World end = worlds.find(seed, InfiniteDimension.END);
        if (end == null) {
            return false;
        }
        DragonBattle battle = end.getEnderDragonBattle();
        return battle != null && battle.hasBeenPreviouslyKilled();
    }

    private static boolean hasDragonEgg(Player player) {
        if (player.getInventory().contains(Material.DRAGON_EGG)) {
            return true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off.getType() == Material.DRAGON_EGG;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Integer> list = yaml.getIntegerList("defeated");
        defeated.clear();
        defeated.addAll(list);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("defeated", List.copyOf(defeated));
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save dragons.yml", ex);
        }
    }
}
