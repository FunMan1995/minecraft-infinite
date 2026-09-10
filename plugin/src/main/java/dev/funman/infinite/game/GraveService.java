package dev.funman.infinite.game;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Death drops go into a chest grave. When that chest is emptied, the grave
 * is removed.
 */
public final class GraveService implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey graveKey;
    private final NamespacedKey ownerKey;

    public GraveService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.graveKey = new NamespacedKey(plugin, "grave");
        this.ownerKey = new NamespacedKey(plugin, "grave_owner");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop != null && drop.getType() != Material.AIR) {
                items.add(drop.clone());
            }
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (items.isEmpty()) {
            return;
        }
        Location at = findGraveSpot(player.getLocation());
        if (at == null) {
            for (ItemStack item : items) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            return;
        }
        at.getBlock().setType(Material.CHEST, false);
        if (!(at.getBlock().getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().set(graveKey, PersistentDataType.BYTE, (byte) 1);
        tile.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        tile.update();
        Inventory inv = ((org.bukkit.block.Container) at.getBlock().getState()).getInventory();
        for (ItemStack item : items) {
            inv.addItem(item);
        }
        player.sendMessage(Component.text(
                "A gravestone holds your items at "
                        + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                        + ".",
                NamedTextColor.GOLD
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof org.bukkit.block.Container container)) {
            return;
        }
        if (!isGrave(container.getBlock())) {
            return;
        }
        if (!isEmpty(event.getInventory())) {
            return;
        }
        container.getBlock().setType(Material.AIR, false);
        if (event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.text("The gravestone crumbles, empty.", NamedTextColor.GRAY));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!isGrave(event.getBlock())) {
            return;
        }
        event.setDropItems(false);
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
                }
            }
            container.getInventory().clear();
        }
    }

    private boolean isGrave(Block block) {
        if (!(block.getState() instanceof TileState tile)) {
            return false;
        }
        Byte flag = tile.getPersistentDataContainer().get(graveKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    private static boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private static Location findGraveSpot(Location origin) {
        if (origin.getWorld() == null) {
            return null;
        }
        Block start = origin.getBlock();
        for (int dy = 0; dy <= 8; dy++) {
            Block up = start.getRelative(0, dy, 0);
            if (up.getType().isAir() || up.isReplaceable()) {
                return up.getLocation();
            }
        }
        for (int dy = 1; dy <= 8; dy++) {
            Block down = start.getRelative(0, -dy, 0);
            if (down.getType().isAir() || down.isReplaceable()) {
                return down.getLocation();
            }
        }
        return origin.getWorld().getHighestBlockAt(origin).getLocation().add(0, 1, 0);
    }
}
