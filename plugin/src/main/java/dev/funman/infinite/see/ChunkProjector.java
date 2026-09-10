package dev.funman.infinite.see;

import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Streams the next seed's chunks just past the X/Z rim so the edge is a
 * see-through portal, not a vanilla world-border wall.
 */
public final class ChunkProjector extends BukkitRunnable {
    private final JavaPlugin plugin;
    private final Topology topology;
    private final StackWorlds worlds;
    private final NmsChunkSender sender;
    private final Map<UUID, Set<Long>> projected = new ConcurrentHashMap<>();

    public ChunkProjector(JavaPlugin plugin, Topology topology, StackWorlds worlds, NmsChunkSender sender) {
        this.plugin = plugin;
        this.topology = topology;
        this.worlds = worlds;
        this.sender = sender;
    }

    public void start() {
        runTaskTimer(plugin, 20L, 10L);
    }

    @Override
    public void run() {
        if (!sender.available()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            project(player);
        }
    }

    private void project(Player player) {
        World world = player.getWorld();
        StackWorlds.Located located = worlds.locate(player.getLocation());
        InfiniteDimension dim = located.dimension();
        int h = topology.half(dim);
        double x = player.getLocation().getX();
        double z = player.getLocation().getZ();
        int view = Math.min(topology.config().overhangChunks(), world.getViewDistance());
        Set<Long> want = new HashSet<>();

        if (x > h - view * 16.0) {
            fillStrip(player, located, h, view, true, true, want);
        }
        if (x < -h + view * 16.0) {
            fillStrip(player, located, h, view, true, false, want);
        }
        if (z > h - view * 16.0) {
            fillStrip(player, located, h, view, false, true, want);
        }
        if (z < -h + view * 16.0) {
            fillStrip(player, located, h, view, false, false, want);
        }

        Set<Long> have = projected.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        for (Long key : have) {
            if (!want.contains(key)) {
                int cx = (int) (key >> 32);
                int cz = key.intValue();
                sender.forget(player, cx, cz);
            }
        }
        have.clear();
        have.addAll(want);
    }

    private void fillStrip(
            Player player,
            StackWorlds.Located located,
            int h,
            int view,
            boolean axisX,
            boolean positive,
            Set<Long> want
    ) {
        int seedDelta = positive ? 1 : -1;
        World neighbor = worlds.worldFor(located.seedIndex() + seedDelta, located.dimension());
        int playerChunkA = (axisX ? player.getLocation().getBlockZ() : player.getLocation().getBlockX()) >> 4;
        int rimChunk = positive ? (h >> 4) : ((-h) >> 4) - 1;
        int depth = view;
        for (int along = -view; along <= view; along++) {
            for (int out = 0; out < depth; out++) {
                int destCx;
                int destCz;
                int srcCx;
                int srcCz;
                int shiftChunks = (2 * h) >> 4;
                if (axisX) {
                    destCx = positive ? rimChunk + out : rimChunk - out;
                    destCz = playerChunkA + along;
                    srcCx = destCx - (positive ? shiftChunks : -shiftChunks);
                    srcCz = destCz;
                } else {
                    destCz = positive ? rimChunk + out : rimChunk - out;
                    destCx = playerChunkA + along;
                    srcCz = destCz - (positive ? shiftChunks : -shiftChunks);
                    srcCx = destCx;
                }
                want.add(pack(destCx, destCz));
                neighbor.getChunkAtAsync(srcCx, srcCz, true).thenAccept(chunk -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    send(player, chunk, destCx, destCz);
                });
            }
        }
    }

    private void send(Player player, Chunk chunk, int destCx, int destCz) {
        if (!chunk.isLoaded()) {
            return;
        }
        sender.sendMappedChunk(player, chunk, destCx, destCz);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
