package fr.robie.mekanized.machine;

/**
 * How radiation behaves: the {@code radiation} section of the server's {@code config.yml}
 * ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it without Bukkit;
 * the defaults are the numbers Mekanized shipped with (Mekanism's, simplified).
 */
public final class RadiationSettings {
    public static final double DEFAULT_BACKGROUND = 100e-9;
    public static final double DEFAULT_SOURCE_DECAY_PER_SECOND = 0.995;
    public static final double DEFAULT_MIN_SOURCE = 1e-6;
    public static final double DEFAULT_DOSE_DECAY_PER_SECOND = 0.999;
    public static final double DEFAULT_SICKNESS_DOSE = 1;
    public static final double DEFAULT_DAMAGE_DOSE = 10;
    public static final double DEFAULT_LETHAL_DOSE = 100;

    /** Sv/h everywhere. */
    public static volatile double background = DEFAULT_BACKGROUND;
    /** Each source keeps this much of its strength every second (0.995: half-life about 2.3 minutes). */
    public static volatile double sourceDecayPerSecond = DEFAULT_SOURCE_DECAY_PER_SECOND;
    /** Sources weaker than this (Sv/h) are forgotten. */
    public static volatile double minSource = DEFAULT_MIN_SOURCE;
    /** A player's dose keeps this much every second (0.999: half-life about 11 minutes). */
    public static volatile double doseDecayPerSecond = DEFAULT_DOSE_DECAY_PER_SECOND;
    /** Doses (Sv) from which a player gets hungry, weak and nauseous and hurt, and from which each second hurts badly. */
    public static volatile double sicknessDose = DEFAULT_SICKNESS_DOSE;
    public static volatile double damageDose = DEFAULT_DAMAGE_DOSE;
    public static volatile double lethalDose = DEFAULT_LETHAL_DOSE;

    private RadiationSettings() {
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        background = DEFAULT_BACKGROUND;
        sourceDecayPerSecond = DEFAULT_SOURCE_DECAY_PER_SECOND;
        minSource = DEFAULT_MIN_SOURCE;
        doseDecayPerSecond = DEFAULT_DOSE_DECAY_PER_SECOND;
        sicknessDose = DEFAULT_SICKNESS_DOSE;
        damageDose = DEFAULT_DAMAGE_DOSE;
        lethalDose = DEFAULT_LETHAL_DOSE;
    }
}
