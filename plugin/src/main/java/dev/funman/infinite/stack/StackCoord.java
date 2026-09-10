package dev.funman.infinite.stack;

import org.bukkit.Location;
import org.bukkit.World;

public record StackCoord(
        int seedIndex,
        InfiniteDimension dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public StackCoord withPosition(double nx, double ny, double nz) {
        return new StackCoord(seedIndex, dimension, nx, ny, nz, yaw, pitch);
    }

    public StackCoord withIndex(int index) {
        return new StackCoord(index, dimension, x, y, z, yaw, pitch);
    }

    public StackCoord withDimension(InfiniteDimension dim) {
        return new StackCoord(seedIndex, dim, x, y, z, yaw, pitch);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static StackCoord from(Location location, int seedIndex, InfiniteDimension dimension) {
        return new StackCoord(
                seedIndex,
                dimension,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }
}
