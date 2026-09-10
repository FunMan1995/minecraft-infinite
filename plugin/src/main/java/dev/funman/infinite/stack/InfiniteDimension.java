package dev.funman.infinite.stack;

import org.bukkit.World;

public enum InfiniteDimension {
    OVERWORLD,
    NETHER,
    END;

    public static InfiniteDimension of(World.Environment environment) {
        return switch (environment) {
            case NETHER -> NETHER;
            case THE_END -> END;
            default -> OVERWORLD;
        };
    }

    public World.Environment environment() {
        return switch (this) {
            case OVERWORLD -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case END -> World.Environment.THE_END;
        };
    }

    public String worldSuffix() {
        return switch (this) {
            case OVERWORLD -> "overworld";
            case NETHER -> "nether";
            case END -> "the_end";
        };
    }
}
