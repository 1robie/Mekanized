package fr.robie.machineengine.mekanized.machine;

/**
 * What the QIO network holds and moves: the {@code qio} section of the server's {@code config.yml}
 * ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it without Bukkit;
 * the defaults are Mekanism's numbers.
 */
public final class QioSettings {
    /** The drives in order: base, hyper-dense, time-dilating, supermassive. */
    public static final int DRIVES = 4;
    public static final long[] DEFAULT_DRIVE_ITEMS = {16_000, 128_000, 1_048_000, 16_000_000};
    public static final long[] DEFAULT_DRIVE_KINDS = {128, 256, 1_024, 8_192};
    public static final int DEFAULT_ARRAY_KINDS = 1_024;
    public static final long DEFAULT_IO_RATE = 64;

    /** Items each kind of drive holds. */
    public static volatile long[] driveItems = DEFAULT_DRIVE_ITEMS.clone();
    /** Kinds of item each kind of drive holds. */
    public static volatile long[] driveKinds = DEFAULT_DRIVE_KINDS.clone();
    /** Item kinds a drive array holds at most, whatever its drives (its item slots, fixed when machines are registered). */
    public static volatile int arrayKinds = DEFAULT_ARRAY_KINDS;
    /** Items an importer or exporter moves per second. */
    public static volatile long ioRate = DEFAULT_IO_RATE;

    private QioSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        driveItems = DEFAULT_DRIVE_ITEMS.clone();
        driveKinds = DEFAULT_DRIVE_KINDS.clone();
        arrayKinds = DEFAULT_ARRAY_KINDS;
        ioRate = DEFAULT_IO_RATE;
    }
}
