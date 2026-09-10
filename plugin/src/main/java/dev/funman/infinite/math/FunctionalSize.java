package dev.funman.infinite.math;

/**
 * Caps each seed's coordinates so float32 error at the rim stays usable.
 *
 * Entity positions on Java are doubles, but Bedrock (Geyser) and the GPU
 * camera still use float32. ULP at magnitude 2^e is 2^(e-23). Requiring
 * ULP ≤ {@code minPrecision} yields |x| &lt; 2^(23 + log2(minPrecision)).
 */
public final class FunctionalSize {
    private static final int FLOAT32_MANTISSA = 23;

    private FunctionalSize() {}

    public static int maxAbsCoord(double minPrecisionBlocks) {
        if (!(minPrecisionBlocks > 0.0) || minPrecisionBlocks > 1.0) {
            throw new IllegalArgumentException("minPrecisionBlocks must be in (0, 1]: " + minPrecisionBlocks);
        }
        double log2p = Math.log(minPrecisionBlocks) / Math.log(2.0);
        int exponent = (int) Math.floor(FLOAT32_MANTISSA + log2p);
        if (exponent < 8) {
            exponent = 8;
        }
        long max = 1L << exponent;
        if (max > 29_999_984L) {
            max = 29_999_984L;
        }
        return (int) max;
    }

    /**
     * Playable half-extent for Overworld/End. Multiple of {@code netherScale}
     * so the nether rim is an integer and 2H is chunk-aligned (16-block).
     */
    public static int playableHalf(int maxAbs, int overhangBlocks, int netherScale) {
        if (netherScale < 1) {
            throw new IllegalArgumentException("netherScale");
        }
        int raw = maxAbs - Math.max(0, overhangBlocks);
        if (raw < netherScale * 16) {
            raw = netherScale * 16;
        }
        int aligned = raw - (raw % netherScale);
        // 2H % 16 == 0 for chunk-aligned see-through (true if H % 8 == 0; netherScale is 8).
        if ((2 * aligned) % 16 != 0) {
            aligned -= netherScale;
        }
        return Math.max(aligned, netherScale * 16);
    }

    public static int netherHalf(int overworldHalf, int netherScale) {
        return overworldHalf / netherScale;
    }
}
