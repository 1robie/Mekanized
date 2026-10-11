package fr.robie.mekanized.machine;

/**
 * How the digital miner works: the {@code digital-miner} section of the server's {@code config.yml}
 * ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it without Bukkit;
 * the defaults are the numbers Mekanized shipped with.
 */
public final class DigitalMinerSettings {
    public static final int DEFAULT_SCAN_PER_TICK = 2_048;
    public static final int DEFAULT_MAX_RADIUS = 32;

    /** Positions the miner looks at per tick for its next block. */
    public static volatile int scanPerTick = DEFAULT_SCAN_PER_TICK;
    /** The widest radius it digs: 32 is a 65 x 65 area. */
    public static volatile int maxRadius = DEFAULT_MAX_RADIUS;

    private DigitalMinerSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        scanPerTick = DEFAULT_SCAN_PER_TICK;
        maxRadius = DEFAULT_MAX_RADIUS;
    }
}
