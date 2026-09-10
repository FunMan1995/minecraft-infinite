package dev.funman.infinite.game;

import dev.funman.infinite.stack.StackWorlds;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Main spawn is seed 0. Hardcore deaths still respawn there (graves keep the items). */
public final class SpawnListener implements Listener {
    private final JavaPlugin plugin;
    private final StackWorlds worlds;

    public SpawnListener(JavaPlugin plugin, StackWorlds worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPlayedBefore()) {
            event.getPlayer().teleport(worlds.mainSpawn());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(worlds.mainSpawn());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                event.getPlayer().setGameMode(GameMode.SURVIVAL);
            }
            event.getPlayer().teleport(worlds.mainSpawn());
        });
    }
}
