package dev.funman.infinite.listener;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackWorlds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Overworld floor and Nether ceiling stay black — no see-through into the
 * other side of the portal-only break.
 */
public final class BlackoutListener implements Listener {
    private static final int RADIUS = 8;
    private final StackWorlds worlds;
    private final BlockData black;

    public BlackoutListener(StackWorlds worlds) {
        this.worlds = worlds;
        this.black = Material.BLACK_CONCRETE.createBlockData();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        World world = to.getWorld();
        InfiniteDimension dim = worlds.locate(to).dimension();
        if (dim == InfiniteDimension.OVERWORLD && to.getY() <= world.getMinHeight() + 6) {
            paint(player, world, world.getMinHeight(), to.getBlockX(), to.getBlockZ());
        } else if (dim == InfiniteDimension.NETHER && to.getY() >= world.getMaxHeight() - 8) {
            paint(player, world, world.getMaxHeight() - 1, to.getBlockX(), to.getBlockZ());
        }
    }

    private void paint(Player player, World world, int y, int cx, int cz) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                Location loc = new Location(world, cx + dx, y, cz + dz);
                player.sendBlockChange(loc, black);
            }
        }
    }
}
