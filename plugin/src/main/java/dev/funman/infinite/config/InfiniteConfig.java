package dev.funman.infinite.config;

import dev.funman.infinite.math.FunctionalSize;
import org.bukkit.configuration.file.FileConfiguration;

public final class InfiniteConfig {
    private final double minPrecisionBlocks;
    private final int overhangChunks;
    private final int netherScale;
    private final int overworldHalf;
    private final int netherHalf;
    private final int maxAbs;
    private final Long pinnedBaseSeed;
    private final boolean hardcore;
    private final int combatTagSeconds;
    private final int tpaTimeoutSeconds;
    private final int maxHomes;
    private final int wildSeedRange;

    private InfiniteConfig(
            double minPrecisionBlocks,
            int overhangChunks,
            int netherScale,
            int overworldHalf,
            int netherHalf,
            int maxAbs,
            Long pinnedBaseSeed,
            boolean hardcore,
            int combatTagSeconds,
            int tpaTimeoutSeconds,
            int maxHomes,
            int wildSeedRange
    ) {
        this.minPrecisionBlocks = minPrecisionBlocks;
        this.overhangChunks = overhangChunks;
        this.netherScale = netherScale;
        this.overworldHalf = overworldHalf;
        this.netherHalf = netherHalf;
        this.maxAbs = maxAbs;
        this.pinnedBaseSeed = pinnedBaseSeed;
        this.hardcore = hardcore;
        this.combatTagSeconds = combatTagSeconds;
        this.tpaTimeoutSeconds = tpaTimeoutSeconds;
        this.maxHomes = maxHomes;
        this.wildSeedRange = wildSeedRange;
    }

    public static InfiniteConfig load(FileConfiguration yaml) {
        double precision = yaml.getDouble("min-precision-blocks", 0.0625);
        int overhangChunks = Math.max(4, yaml.getInt("overhang-chunks", 16));
        int netherScale = Math.max(1, yaml.getInt("nether-scale", 8));
        int maxAbs = FunctionalSize.maxAbsCoord(precision);
        int overhangBlocks = overhangChunks * 16;
        int autoHalf = FunctionalSize.playableHalf(maxAbs, overhangBlocks, netherScale);

        int overworldHalf = autoHalf;
        String halfSpec = String.valueOf(yaml.get("border-half", "auto"));
        if (!"auto".equalsIgnoreCase(halfSpec.trim())) {
            overworldHalf = yaml.getInt("border-half", autoHalf);
            if (overworldHalf % netherScale != 0) {
                overworldHalf -= overworldHalf % netherScale;
            }
            overworldHalf = Math.min(overworldHalf, autoHalf);
            overworldHalf = Math.max(overworldHalf, netherScale * 16);
        }

        Long pinned = null;
        String seedSpec = String.valueOf(yaml.get("base-seed", "auto"));
        if (!"auto".equalsIgnoreCase(seedSpec.trim())) {
            pinned = yaml.getLong("base-seed");
        }

        return new InfiniteConfig(
                precision,
                overhangChunks,
                netherScale,
                overworldHalf,
                FunctionalSize.netherHalf(overworldHalf, netherScale),
                maxAbs,
                pinned,
                yaml.getBoolean("hardcore", true),
                Math.max(1, yaml.getInt("combat-tag-seconds", 15)),
                Math.max(5, yaml.getInt("tpa-timeout-seconds", 60)),
                Math.max(1, yaml.getInt("max-homes", 1)),
                Math.max(1, yaml.getInt("wild-seed-range", 64))
        );
    }

    public double minPrecisionBlocks() {
        return minPrecisionBlocks;
    }

    public int overhangChunks() {
        return overhangChunks;
    }

    public int overhangBlocks() {
        return overhangChunks * 16;
    }

    public int netherScale() {
        return netherScale;
    }

    public int overworldHalf() {
        return overworldHalf;
    }

    public int netherHalf() {
        return netherHalf;
    }

    public int maxAbs() {
        return maxAbs;
    }

    public Long pinnedBaseSeed() {
        return pinnedBaseSeed;
    }

    public boolean hardcore() {
        return hardcore;
    }

    public int combatTagSeconds() {
        return combatTagSeconds;
    }

    public int tpaTimeoutSeconds() {
        return tpaTimeoutSeconds;
    }

    public int maxHomes() {
        return maxHomes;
    }

    public int wildSeedRange() {
        return wildSeedRange;
    }

    public int halfFor(dev.funman.infinite.stack.InfiniteDimension dimension) {
        return dimension == dev.funman.infinite.stack.InfiniteDimension.NETHER ? netherHalf : overworldHalf;
    }
}
