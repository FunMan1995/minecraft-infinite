package dev.funman.infinite.listener;

import dev.funman.infinite.game.DragonGate;
import dev.funman.infinite.stack.StackCoord;
import dev.funman.infinite.substrate.Substrate;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class BorderCrossListener implements Listener {
    private final JavaPlugin plugin;
    private final Topology topology;
    private final StackWorlds worlds;
    private final DragonGate dragonGate;
    private final Substrate substrate;
    private final Map<UUID, Long> coolUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPopup = new ConcurrentHashMap<>();

    public BorderCrossListener(JavaPlugin plugin, Topology topology, StackWorlds worlds, DragonGate dragonGate, Substrate substrate) {
        this.plugin = plugin;
        this.topology = topology;
        this.worlds = worlds;
        this.dragonGate = dragonGate;
        this.substrate = substrate;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        popup(event.getPlayer(), worlds.locate(from).seedIndex(), worlds.locate(to).seedIndex());
        handle(event.getPlayer(), from, to, event, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        popup(event.getPlayer(), worlds.locate(event.getFrom()).seedIndex(), worlds.locate(to).seedIndex());
        handle(event.getPlayer(), event.getFrom(), to, null, event);
    }

    private void handle(Player player, Location from, Location to, PlayerMoveEvent move, PlayerTeleportEvent teleport) {
        long now = player.getWorld().getFullTime();
        Long until = coolUntil.get(player.getUniqueId());
        if (until != null && until > now) {
            return;
        }
        StackWorlds.Located located = worlds.locate(to);
        int h = topology.half(located.dimension());
        if (Math.abs(to.getX()) < h - 1
                && Math.abs(to.getZ()) < h - 1
                && to.getY() > to.getWorld().getMinHeight()
                && to.getY() < to.getWorld().getMaxHeight() - 1) {
            return;
        }
        StackCoord at = StackCoord.from(to, located.seedIndex(), located.dimension());
        Topology.Result folded = topology.fold(at, to.getWorld());
        if (folded.crossing() == Topology.Crossing.NONE) {
            return;
        }
        if (folded.crossing() == Topology.Crossing.HORIZONTAL
                && !dragonGate.canCrossSideways(player, located.seedIndex())) {
            dragonGate.denyMessage(player);
            Location bounce = from.clone();
            if (move != null) {
                move.setTo(bounce);
            }
            if (teleport != null) {
                teleport.setTo(bounce);
            }
            player.setVelocity(new Vector(0, 0, 0));
            return;
        }
        if (folded.crossing() == Topology.Crossing.VERTICAL_BLACK) {
            Location clamp = folded.coord().toLocation(to.getWorld());
            clamp.setYaw(to.getYaw());
            clamp.setPitch(to.getPitch());
            if (move != null) {
                move.setTo(clamp);
            }
            if (teleport != null) {
                teleport.setTo(clamp);
            }
            player.setVelocity(new Vector(0, folded.face() == Topology.Face.DOWN ? 0.4 : -0.2, 0));
            return;
        }
        World destWorld = worlds.worldFor(folded.coord().seedIndex(), folded.coord().dimension());
        Location dest = folded.coord().toLocation(destWorld);
        dest.setY(clampY(destWorld, dest.getY()));
        coolUntil.put(player.getUniqueId(), now + 10);
        if (!destWorld.equals(to.getWorld())) {
            if (move != null) {
                move.setCancelled(true);
            }
            if (teleport != null) {
                teleport.setCancelled(true);
            }
            player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return;
        }
        if (move != null) {
            move.setTo(dest);
        } else if (teleport != null) {
            teleport.setTo(dest);
        }
        plugin.getLogger().fine(() -> player.getName() + " crossed " + folded.face()
                + " → seed " + folded.coord().seedIndex() + " " + folded.coord().dimension());
    }

    private void popup(Player player, int fromSeed, int toSeed) {
        if (fromSeed == toSeed) {
            return;
        }
        Substrate.Sub sub = substrate.ownerOfSeed(toSeed);
        if (sub == null) {
            return;
        }
        String tag = sub.address() + ":" + toSeed;
        if (tag.equals(lastPopup.get(player.getUniqueId()))) {
            return;
        }
        lastPopup.put(player.getUniqueId(), tag);
        String kind = sub.kind() == Substrate.Kind.MODDED ? "modded pack" : "vanilla sub";
        player.sendMessage(net.kyori.adventure.text.Component.text(
                "You entered " + sub.address() + " (" + kind + "). Mods: "
                        + (sub.mods().isEmpty() ? "none" : String.join(", ", sub.mods())),
                net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
        if (sub.kind() == Substrate.Kind.MODDED) {
            player.sendTitle(sub.address(), "Modded subserver — crossing may clash", 10, 50, 10);
        }
    }

    private static double clampY(World world, double y) {
        double min = world.getMinHeight() + 0.1;
        double max = world.getMaxHeight() - 2.0;
        if (y < min) {
            return min;
        }
        if (y > max) {
            return max;
        }
        return y;
    }
}
