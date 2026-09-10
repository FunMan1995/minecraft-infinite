package dev.funman.infinite.game;

import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.substrate.Substrate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Seed 0 is the hub: no build/edit except operators, PvP off, full saturation.
 */
public final class HubGuard implements Listener {
    public static final String EDIT_PERMISSION = "infinite.hub.edit";

    private final StackWorlds worlds;
    private final Substrate substrate;

    public HubGuard(StackWorlds worlds, Substrate substrate) {
        this.worlds = worlds;
        this.substrate = substrate;
    }

    public void start(JavaPlugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                int seed = worlds.locate(player.getLocation()).seedIndex();
                if (!worlds.isHub(player.getLocation()) && !substrate.isSubHome(seed)) {
                    continue;
                }
                player.setFoodLevel(20);
                player.setSaturation(20.0f);
                player.setExhaustion(0.0f);
            }
        }, 20L, 40L);
    }

    public static boolean canEdit(Player player) {
        return player.isOp() || player.hasPermission(EDIT_PERMISSION);
    }

    private boolean deny(Player player) {
        if (canEdit(player)) {
            return false;
        }
        player.sendMessage(Component.text("Seed 0 is the hub. Only operators can edit here.", NamedTextColor.RED));
        return true;
    }

    /** Hub is master-op only. A sub's Home seed is editable by that sub's operator. */
    private boolean protect(org.bukkit.Location location, Player player) {
        int seed = worlds.locate(location).seedIndex();
        if (seed == 0) {
            return deny(player);
        }
        if (!substrate.isSubHome(seed)) {
            return false;
        }
        if (substrate.isSubOp(player.getUniqueId(), seed) || canEdit(player)) {
            return false;
        }
        player.sendMessage(Component.text("This is a subserver Home. Only its operator can edit it.", NamedTextColor.RED));
        return true;
    }

    private boolean sanctuary(org.bukkit.Location location) {
        int seed = worlds.locate(location).seedIndex();
        return seed == 0 || substrate.isSubHome(seed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protect(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protect(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protect(event.getBlockClicked().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protect(event.getBlockClicked().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getState() instanceof Container) {
            return;
        }
        if (protect(event.getClickedBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (protect(event.getRightClicked().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (protect(event.getRightClicked().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && protect(event.getEntity().getLocation(), player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player
                && protect(event.getEntity().getLocation(), player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (worlds.isHub(event.getLocation())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        if (worlds.isHub(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (worlds.isHub(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && sanctuary(player.getLocation())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !sanctuary(victim.getLocation())) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }
        if (attacker != null) {
            event.setCancelled(true);
        }
    }
}
