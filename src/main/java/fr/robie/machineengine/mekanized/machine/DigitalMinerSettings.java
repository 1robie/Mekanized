package fr.robie.machineengine.mekanized.machine;

/**
 * How the digital miner works: the {@code digital-miner} section of the server's {@code config.yml}
 * ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it without Bukkit;
 * the defaults are the numbers Mekanized shipped with.
 */
public final class DigitalMinerSettings {
    public static final long DEFAULT_ENERGY_PER_BLOCK = 2_000;
    public static final long DEFAULT_TICKS_PER_BLOCK = 10;
    public static final int DEFAULT_SCAN_PER_TICK = 2_048;
    public static final int DEFAULT_MAX_RADIUS = 32;

    /** Joules per block mined. */
    public static volatile long energyPerBlock = DEFAULT_ENERGY_PER_BLOCK;
    /** Ticks per block mined (speed upgrades shorten it). */
    public static volatile long ticksPerBlock = DEFAULT_TICKS_PER_BLOCK;
    /** Positions the miner looks at per tick for its next block. */
    public static volatile int scanPerTick = DEFAULT_SCAN_PER_TICK;
    /** The widest radius it digs: 32 is a 65 x 65 area. */
    public static volatile int maxRadius = DEFAULT_MAX_RADIUS;

    private DigitalMinerSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        energyPerBlock = DEFAULT_ENERGY_PER_BLOCK;
        ticksPerBlock = DEFAULT_TICKS_PER_BLOCK;
        scanPerTick = DEFAULT_SCAN_PER_TICK;
        maxRadius = DEFAULT_MAX_RADIUS;
    }
}
