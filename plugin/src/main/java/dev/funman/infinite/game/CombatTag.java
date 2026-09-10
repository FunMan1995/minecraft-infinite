package dev.funman.infinite.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Factions-style combat tag. Teleport commands refuse this while active. */
public final class CombatTag implements Listener {
    private final JavaPlugin plugin;
    private final int seconds;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();

    public CombatTag(JavaPlugin plugin, int seconds) {
        this.plugin = plugin;
        this.seconds = seconds;
    }

    public boolean tagged(Player player) {
        Long stamp = until.get(player.getUniqueId());
        if (stamp == null) {
            return false;
        }
        if (stamp <= System.currentTimeMillis()) {
            until.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public int remainingSeconds(Player player) {
        Long stamp = until.get(player.getUniqueId());
        if (stamp == null) {
            return 0;
        }
        long left = (stamp - System.currentTimeMillis() + 999) / 1000;
        return (int) Math.max(0, left);
    }

    public boolean denyIfTagged(Player player, String action) {
        if (!tagged(player)) {
            return false;
        }
        player.sendMessage(Component.text(
                "You cannot " + action + " while combat tagged (" + remainingSeconds(player) + "s).",
                NamedTextColor.RED
        ));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (!(event.getEntity() instanceof Player victim) || attacker == null) {
            return;
        }
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        tag(attacker);
        tag(victim);
    }

    private void tag(Player player) {
        boolean fresh = !tagged(player);
        until.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        if (fresh) {
            player.sendMessage(Component.text("Combat tagged for " + seconds + "s.", NamedTextColor.GOLD));
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
