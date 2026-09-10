package dev.funman.infinite.stack;

import dev.funman.infinite.config.InfiniteConfig;
import org.bukkit.World;

/**
 * Helix + dimension column.
 * Ground plane (Minecraft X and Z — the sideways "X/Y" plane): same dimension,
 * neighboring seed. That crossing is gated (dragon / egg) elsewhere.
 * Vertical (Minecraft Y — "Z" through the stack) always stays open so a
 * griefed End cannot soft-lock anyone.
 */
public final class Topology {
    public enum Face {
        POS_X,
        NEG_X,
        POS_Z,
        NEG_Z,
        UP,
        DOWN
    }

    public enum Crossing {
        NONE,
        HORIZONTAL,
        VERTICAL_OPEN,
        VERTICAL_BLACK
    }

    public record Result(StackCoord coord, Crossing crossing, Face face) {}

    private final InfiniteConfig config;

    public Topology(InfiniteConfig config) {
        this.config = config;
    }

    public InfiniteConfig config() {
        return config;
    }

    public int half(InfiniteDimension dimension) {
        return config.halfFor(dimension);
    }

    /**
     * Fold a position into the canonical playable box, updating seed index
     * and dimension. Returns VERTICAL_BLACK if the move is the OW↔Nether gap.
     */
    public Result fold(StackCoord at, World world) {
        double x = at.x();
        double y = at.y();
        double z = at.z();
        int index = at.seedIndex();
        InfiniteDimension dim = at.dimension();

        int h = half(dim);
        Crossing crossing = Crossing.NONE;
        Face face = null;

        while (x >= h) {
            x -= 2.0 * h;
            index += 1;
            crossing = Crossing.HORIZONTAL;
            face = Face.POS_X;
        }
        while (x < -h) {
            x += 2.0 * h;
            index -= 1;
            crossing = Crossing.HORIZONTAL;
            face = Face.NEG_X;
        }
        while (z >= h) {
            z -= 2.0 * h;
            index += 1;
            crossing = Crossing.HORIZONTAL;
            face = Face.POS_Z;
        }
        while (z < -h) {
            z += 2.0 * h;
            index -= 1;
            crossing = Crossing.HORIZONTAL;
            face = Face.NEG_Z;
        }

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        if (y >= maxY) {
            return switch (dim) {
                case OVERWORLD -> new Result(
                        new StackCoord(index, InfiniteDimension.END, x, minYOf(InfiniteDimension.END) + 2.0, z, at.yaw(), at.pitch()),
                        Crossing.VERTICAL_OPEN,
                        Face.UP
                );
                case END -> new Result(
                        new StackCoord(
                                index + 1,
                                InfiniteDimension.NETHER,
                                x / config.netherScale(),
                                minYOf(InfiniteDimension.NETHER) + 2.0,
                                z / config.netherScale(),
                                at.yaw(),
                                at.pitch()
                        ),
                        Crossing.VERTICAL_OPEN,
                        Face.UP
                );
                case NETHER -> new Result(at.withPosition(x, maxY - 2.0, z), Crossing.VERTICAL_BLACK, Face.UP);
            };
        }
        if (y < minY) {
            return switch (dim) {
                case END -> new Result(
                        new StackCoord(index, InfiniteDimension.OVERWORLD, x, maxYOf(InfiniteDimension.OVERWORLD) - 3.0, z, at.yaw(), at.pitch()),
                        Crossing.VERTICAL_OPEN,
                        Face.DOWN
                );
                case NETHER -> new Result(
                        new StackCoord(
                                index - 1,
                                InfiniteDimension.END,
                                x * config.netherScale(),
                                maxYOf(InfiniteDimension.END) - 3.0,
                                z * config.netherScale(),
                                at.yaw(),
                                at.pitch()
                        ),
                        Crossing.VERTICAL_OPEN,
                        Face.DOWN
                );
                case OVERWORLD -> new Result(at.withPosition(x, minY + 1.5, z), Crossing.VERTICAL_BLACK, Face.DOWN);
            };
        }

        if (crossing == Crossing.NONE) {
            return new Result(at.withPosition(x, y, z), Crossing.NONE, null);
        }
        return new Result(new StackCoord(index, dim, x, y, z, at.yaw(), at.pitch()), crossing, face);
    }

    public StackCoord netherPortalDestination(StackCoord from) {
        int scale = config.netherScale();
        if (from.dimension() == InfiniteDimension.OVERWORLD) {
            return new StackCoord(
                    from.seedIndex(),
                    InfiniteDimension.NETHER,
                    from.x() / scale,
                    from.y() / 2.0,
                    from.z() / scale,
                    from.yaw(),
                    from.pitch()
            );
        }
        if (from.dimension() == InfiniteDimension.NETHER) {
            return new StackCoord(
                    from.seedIndex(),
                    InfiniteDimension.OVERWORLD,
                    from.x() * scale,
                    from.y() * 2.0,
                    from.z() * scale,
                    from.yaw(),
                    from.pitch()
            );
        }
        return from;
    }

    public StackCoord endPortalDestination(StackCoord from) {
        if (from.dimension() == InfiniteDimension.END) {
            return new StackCoord(from.seedIndex(), InfiniteDimension.OVERWORLD, 0, 320, 0, from.yaw(), from.pitch());
        }
        return new StackCoord(from.seedIndex(), InfiniteDimension.END, 100, 50, 0, from.yaw(), from.pitch());
    }

    private static int minYOf(InfiniteDimension dim) {
        return switch (dim) {
            case OVERWORLD -> -64;
            case NETHER, END -> 0;
        };
    }

    private static int maxYOf(InfiniteDimension dim) {
        return switch (dim) {
            case OVERWORLD -> 320;
            case NETHER, END -> 256;
        };
    }
}
