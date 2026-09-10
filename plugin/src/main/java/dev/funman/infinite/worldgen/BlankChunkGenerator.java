package dev.funman.infinite.worldgen;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/**
 * Empty hub terrain. Real Nether/End are still those dimension types inside
 * the same world save — not biomes painted into the Overworld.
 */
public final class BlankChunkGenerator extends ChunkGenerator {
    private static final int PLATFORM_Y = 64;

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        if (chunkX != 0 || chunkZ != 0) {
            return;
        }
        for (int x = 4; x <= 11; x++) {
            for (int z = 4; z <= 11; z++) {
                chunkData.setBlock(x, PLATFORM_Y, z, Material.LIGHT_GRAY_CONCRETE);
            }
        }
        chunkData.setBlock(8, PLATFORM_Y, 8, Material.BEDROCK);
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 8.5, PLATFORM_Y + 1, 8.5, 0f, 0f);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.THE_VOID);
            }
        };
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }
}
