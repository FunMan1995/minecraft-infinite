package dev.funman.infinite;

import dev.funman.infinite.config.InfiniteConfig;
import dev.funman.infinite.stack.InfiniteDimension;
import dev.funman.infinite.stack.StackCoord;
import dev.funman.infinite.stack.StackWorlds;
import dev.funman.infinite.stack.Topology;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Stable hook for later command mechanics. Stack math lives here so commands
 * never talk to world borders directly.
 */
public final class InfiniteApi {
    private final InfiniteConfig config;
    private final Topology topology;
    private final StackWorlds worlds;

    public InfiniteApi(InfiniteConfig config, Topology topology, StackWorlds worlds) {
        this.config = config;
        this.topology = topology;
        this.worlds = worlds;
    }

    public InfiniteConfig config() {
        return config;
    }

    public Topology topology() {
        return topology;
    }

    public StackWorlds worlds() {
        return worlds;
    }

    public StackCoord locate(Location location) {
        StackWorlds.Located located = worlds.locate(location);
        return StackCoord.from(location, located.seedIndex(), located.dimension());
    }

    public Location resolve(StackCoord coord) {
        World world = worlds.worldFor(coord.seedIndex(), coord.dimension());
        return coord.toLocation(world);
    }

    public int borderHalf(InfiniteDimension dimension) {
        return topology.half(dimension);
    }

    public long seedFor(int index) {
        return worlds.seedFor(index);
    }
}
