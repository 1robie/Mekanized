package fr.robie.machineengine.mekanized.machine;

/**
 * What the QIO network holds and moves: the {@code qio} section of the server's {@code config.yml}
 * ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it without Bukkit;
 * the defaults are Mekanism's numbers.
 */
public final class QioSettings {
    public static final int DEFAULT_ARRAY_KINDS = 1_024;

    /** Item kinds a drive array holds at most, whatever its drives (its item slots, fixed when machines are registered). */
    public static volatile int arrayKinds = DEFAULT_ARRAY_KINDS;

    private QioSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        arrayKinds = DEFAULT_ARRAY_KINDS;
    }
}
