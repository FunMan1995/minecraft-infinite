package dev.funman.infinite.game;

import dev.funman.infinite.stack.StackWorlds;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** First join lands on seed 0. Hardcore deaths stay spectator until Reincarnate. */
public final class SpawnListener implements Listener {
    private final StackWorlds worlds;

    public SpawnListener(StackWorlds worlds) {
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPlayedBefore()) {
            event.getPlayer().teleport(worlds.mainSpawn());
        }
    }
}
