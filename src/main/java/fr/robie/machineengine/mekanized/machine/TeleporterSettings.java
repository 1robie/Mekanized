package fr.robie.machineengine.mekanized.machine;

/**
 * What teleporting costs and how often a player may do it: the {@code teleporter} section of the
 * server's {@code config.yml} ({@code MekanizedSettings} fills it in). Plain Java, so machine code and
 * tests use it without Bukkit; the defaults are Mekanism's numbers.
 */
public final class TeleporterSettings {
    public static final long DEFAULT_BASE_COST = 1_000;
    public static final long DEFAULT_COST_PER_BLOCK = 10;
    public static final long DEFAULT_OTHER_WORLD_COST = 100_000;
    public static final long DEFAULT_COOLDOWN_MS = 3_000;

    /** Joules of any teleport. */
    public static volatile long baseCost = DEFAULT_BASE_COST;
    /** Joules more for each block travelled in one world. */
    public static volatile long costPerBlock = DEFAULT_COST_PER_BLOCK;
    /** Joules of a teleport to another world (the distance does not count). */
    public static volatile long otherWorldCost = DEFAULT_OTHER_WORLD_COST;
    /** Milliseconds a player waits between two teleports through a teleporter or portable teleporter. */
    public static volatile long cooldownMs = DEFAULT_COOLDOWN_MS;

    private TeleporterSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        baseCost = DEFAULT_BASE_COST;
        costPerBlock = DEFAULT_COST_PER_BLOCK;
        otherWorldCost = DEFAULT_OTHER_WORLD_COST;
        cooldownMs = DEFAULT_COOLDOWN_MS;
    }
}
