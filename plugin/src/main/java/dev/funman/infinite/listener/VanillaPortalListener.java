package dev.funman.infinite.listener;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackCoord;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Overworld ↔ Nether is the stack's only physical break: vanilla nether
 * portals, same seed, 8:1. End portals stay on the same seed.
 */
public final class VanillaPortalListener implements Listener {
    private final Topology topology;
    private final StackWorlds worlds;

    public VanillaPortalListener(Topology topology, StackWorlds worlds) {
        this.topology = topology;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        if (from.getWorld() == null) {
            return;
        }
        StackWorlds.Located located = worlds.locate(from);
        StackCoord at = StackCoord.from(from, located.seedIndex(), located.dimension());
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        StackCoord destCoord;
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (located.dimension() == InfiniteDimension.END) {
                event.setCancelled(true);
                return;
            }
            destCoord = topology.netherPortalDestination(at);
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            destCoord = topology.endPortalDestination(at);
        } else {
            return;
        }
        World destWorld = worlds.worldFor(destCoord.seedIndex(), destCoord.dimension());
        Location dest = destCoord.toLocation(destWorld);
        dest.setY(Math.min(destWorld.getMaxHeight() - 2, Math.max(destWorld.getMinHeight() + 1, dest.getY())));
        event.setTo(dest);
        event.setCanCreatePortal(true);
        event.setSearchRadius(128);
    }
}
