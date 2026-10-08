package fr.robie.machineengine.mekanized.machine;

/**
 * What the dimensional stabilizer can do: the {@code chunk-loading} section of the server's
 * {@code config.yml} ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it
 * without Bukkit; the defaults are Mekanism's numbers.
 */
public final class ChunkLoadingSettings {
    public static final int DEFAULT_MAX_RADIUS = 2;

    /** Chunks around its own the stabilizer can load: 2 is a 5 x 5 area. */
    public static volatile int maxRadius = DEFAULT_MAX_RADIUS;

    private ChunkLoadingSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        maxRadius = DEFAULT_MAX_RADIUS;
    }
}
