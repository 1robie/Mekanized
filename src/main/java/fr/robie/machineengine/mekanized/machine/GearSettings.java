package fr.robie.machineengine.mekanized.machine;

import java.util.Map;

/**
 * The numbers of the gear (tools, suits, packs, modules): the {@code gear} section of the server's
 * {@code config.yml} ({@code MekanizedSettings} fills it in). Plain Java, so machine code and tests use it
 * without Bukkit; the defaults are the numbers Mekanized shipped with (Mekanism's, simplified).
 * Energy is in Joules, chemicals in mB.
 */
public final class GearSettings {
    // -- defaults ---------------------------------------------------------------------------------

    public static final long DEFAULT_ENERGY_UNIT_FACTOR = 4;
    public static final long DEFAULT_TOOL_ENERGY_PER_BLOCK = 100;
    public static final int DEFAULT_VEIN_LIMIT = 64;
    public static final int DEFAULT_VEIN_LIMIT_WITH_UNIT = 256;
    public static final long DEFAULT_SHEAR_ENERGY = 500;
    public static final long DEFAULT_DISASSEMBLER_HIT_ENERGY = 2_000;
    public static final double DEFAULT_DISASSEMBLER_DAMAGE = 20;
    public static final long DEFAULT_MEKA_TOOL_HIT_ENERGY = 3_000;
    public static final double DEFAULT_MEKA_TOOL_DAMAGE = 10;
    public static final double DEFAULT_MEKA_TOOL_AMPLIFIED_DAMAGE = 30;
    public static final long DEFAULT_BOW_ENERGY_PER_SHOT = 120;
    public static final int DEFAULT_TELEPORT_RANGE = 100;
    public static final long DEFAULT_FLAMETHROWER_HYDROGEN = 5;
    public static final double DEFAULT_FLAMETHROWER_DAMAGE = 2;
    public static final long DEFAULT_JETPACK_HYDROGEN_PER_TICK = 1;
    public static final double DEFAULT_JETPACK_THRUST = 0.15;
    public static final double DEFAULT_JETPACK_MAX_SPEED = 0.5;
    public static final double DEFAULT_JETPACK_WALK_SPEED = 0.3;
    public static final double DEFAULT_JETPACK_SPRINT_SPEED = 0.45;
    public static final long DEFAULT_SCUBA_OXYGEN_PER_SECOND = 20;
    public static final long DEFAULT_FALL_ENERGY_PER_DAMAGE = 50;
    public static final long DEFAULT_MAGNET_ENERGY_PER_ITEM = 20;
    public static final int DEFAULT_MAGNET_RANGE = 8;
    public static final long DEFAULT_BREATHING_ENERGY = 2_000;
    public static final long DEFAULT_VISION_ENERGY = 200;
    public static final long DEFAULT_NUTRITION_ENERGY = 2_500;
    public static final long DEFAULT_PURIFICATION_ENERGY = 2_000;
    public static final long DEFAULT_FLIGHT_ENERGY = 20_000;
    public static final long DEFAULT_LOCOMOTIVE_ENERGY = 2_000;
    public static final long DEFAULT_HYDRAULIC_ENERGY = 200;
    public static final long DEFAULT_REPULSOR_ENERGY = 500;
    public static final long DEFAULT_GYROSCOPIC_ENERGY = 200;
    public static final long DEFAULT_SERVO_MIN_ENERGY = 100;
    public static final long DEFAULT_SOLAR_ENERGY = 2_000;
    public static final long DEFAULT_GEOTHERMAL_ENERGY = 10_000;
    public static final long DEFAULT_DISTRIBUTION_ENERGY = 20_000;

    // -- capacities -------------------------------------------------------------------------------

    /** Joules each energy item holds when full, by item id (a Mekanized item). */
    public static volatile Map<String, Long> energyCapacity = defaultEnergyCapacity();
    /** The energy unit multiplies a piece's capacity by this. */
    public static volatile long energyUnitFactor = DEFAULT_ENERGY_UNIT_FACTOR;
    /** mB of chemical each gear item holds, by item id. */
    public static volatile Map<String, Long> chemicalCapacity = defaultChemicalCapacity();

    // -- tools ------------------------------------------------------------------------------------

    /** Joules each block the disassembler or Meka-Tool breaks costs (a tool needs this much to count as charged). */
    public static volatile long toolEnergyPerBlock = DEFAULT_TOOL_ENERGY_PER_BLOCK;
    /** Blocks one vein mining breaks at most: with sneaking, and with the vein mining unit. */
    public static volatile int veinLimit = DEFAULT_VEIN_LIMIT;
    public static volatile int veinLimitWithUnit = DEFAULT_VEIN_LIMIT_WITH_UNIT;
    /** Shearing unit: Joules per animal. */
    public static volatile long shearEnergy = DEFAULT_SHEAR_ENERGY;
    /** Charged tools: Joules per hit and the damage dealt at least (20 is ten hearts). */
    public static volatile long disassemblerHitEnergy = DEFAULT_DISASSEMBLER_HIT_ENERGY;
    public static volatile double disassemblerDamage = DEFAULT_DISASSEMBLER_DAMAGE;
    public static volatile long mekaToolHitEnergy = DEFAULT_MEKA_TOOL_HIT_ENERGY;
    public static volatile double mekaToolDamage = DEFAULT_MEKA_TOOL_DAMAGE;
    public static volatile double mekaToolAmplifiedDamage = DEFAULT_MEKA_TOOL_AMPLIFIED_DAMAGE;
    /** Electric bow: Joules per shot. */
    public static volatile long bowEnergyPerShot = DEFAULT_BOW_ENERGY_PER_SHOT;
    /** Teleportation unit: how far it looks (blocks); the cost is the teleporter's ({@link TeleporterSettings}). */
    public static volatile int teleportRange = DEFAULT_TELEPORT_RANGE;
    /** Flamethrower: mB of hydrogen per burst, and the damage of a burst. */
    public static volatile long flamethrowerHydrogen = DEFAULT_FLAMETHROWER_HYDROGEN;
    public static volatile double flamethrowerDamage = DEFAULT_FLAMETHROWER_DAMAGE;

    // -- jetpack and breathing --------------------------------------------------------------------

    /** Jetpack: mB of hydrogen per tick of thrust, the speed it adds up each tick, and the top upward speed. */
    public static volatile long jetpackHydrogenPerTick = DEFAULT_JETPACK_HYDROGEN_PER_TICK;
    public static volatile double jetpackThrust = DEFAULT_JETPACK_THRUST;
    public static volatile double jetpackMaxSpeed = DEFAULT_JETPACK_MAX_SPEED;
    /** Jetpack: the horizontal speed steered with the movement keys, walking and sprinting. */
    public static volatile double jetpackWalkSpeed = DEFAULT_JETPACK_WALK_SPEED;
    public static volatile double jetpackSprintSpeed = DEFAULT_JETPACK_SPRINT_SPEED;
    /** Scuba tank: mB of oxygen for each time it refills the player's air. */
    public static volatile long scubaOxygen = DEFAULT_SCUBA_OXYGEN_PER_SECOND;

    // -- module costs -----------------------------------------------------------------------------

    /** Free runners and MekaSuit boots: Joules per point of fall damage taken. */
    public static volatile long fallEnergyPerDamage = DEFAULT_FALL_ENERGY_PER_DAMAGE;
    /** Magnetic attraction unit: Joules per item pulled, and the range (blocks). */
    public static volatile long magnetEnergyPerItem = DEFAULT_MAGNET_ENERGY_PER_ITEM;
    public static volatile int magnetRange = DEFAULT_MAGNET_RANGE;
    /** Joules per second of the units that run each second (per use for the purification unit). */
    public static volatile long breathingEnergy = DEFAULT_BREATHING_ENERGY;
    public static volatile long visionEnergy = DEFAULT_VISION_ENERGY;
    public static volatile long nutritionEnergy = DEFAULT_NUTRITION_ENERGY;
    public static volatile long purificationEnergy = DEFAULT_PURIFICATION_ENERGY;
    /** Gravitational modulating unit: Joules per second of flight (and the charge needed to start flying). */
    public static volatile long flightEnergy = DEFAULT_FLIGHT_ENERGY;
    public static volatile long locomotiveEnergy = DEFAULT_LOCOMOTIVE_ENERGY;
    public static volatile long hydraulicEnergy = DEFAULT_HYDRAULIC_ENERGY;
    public static volatile long repulsorEnergy = DEFAULT_REPULSOR_ENERGY;
    public static volatile long gyroscopicEnergy = DEFAULT_GYROSCOPIC_ENERGY;
    /** Motorized servo unit: the charge the legs need to step up blocks. */
    public static volatile long servoMinEnergy = DEFAULT_SERVO_MIN_ENERGY;
    /** Energy makers, Joules per second: solar recharging (in sunlight), geothermal (in lava or on fire). */
    public static volatile long solarEnergy = DEFAULT_SOLAR_ENERGY;
    public static volatile long geothermalEnergy = DEFAULT_GEOTHERMAL_ENERGY;
    /** Charge distribution unit: Joules per second given to each other piece. */
    public static volatile long distributionEnergy = DEFAULT_DISTRIBUTION_ENERGY;

    private GearSettings() {
    }

    /** The gear that holds energy, and how much; the MekaSuit's four pieces and the free runners have one number each. */
    public static Map<String, Long> defaultEnergyCapacity() {
        return Map.ofEntries(
                Map.entry("energy_tablet", 1_000_000L),
                Map.entry("atomic_disassembler", 1_000_000L),
                Map.entry("portable_teleporter", 1_000_000L),
                Map.entry("meka_tool", 16_000_000L),
                Map.entry("electric_bow", 120_000L),
                Map.entry("free_runners", 64_000L),
                Map.entry("free_runners_armored", 64_000L),
                Map.entry("mekasuit_helmet", 16_000_000L),
                Map.entry("mekasuit_bodyarmor", 16_000_000L),
                Map.entry("mekasuit_pants", 16_000_000L),
                Map.entry("mekasuit_boots", 16_000_000L));
    }

    /** The gear that holds a chemical, and how much (the chemical itself is fixed). */
    public static Map<String, Long> defaultChemicalCapacity() {
        return Map.of(
                "jetpack", 24_000L, "jetpack_armored", 24_000L, "flamethrower", 24_000L, "scuba_tank", 24_000L,
                "mekasuit_bodyarmor", 24_000L, "canteen", 64_000L);
    }

    /** The capacity of a gear item that holds a chemical, or 0. */
    public static long chemicalCapacity(String item) {
        return chemicalCapacity.getOrDefault(item, 0L);
    }

    /** Back to the defaults (tests). */
    public static void reset() {
        energyCapacity = defaultEnergyCapacity();
        energyUnitFactor = DEFAULT_ENERGY_UNIT_FACTOR;
        chemicalCapacity = defaultChemicalCapacity();
        toolEnergyPerBlock = DEFAULT_TOOL_ENERGY_PER_BLOCK;
        veinLimit = DEFAULT_VEIN_LIMIT;
        veinLimitWithUnit = DEFAULT_VEIN_LIMIT_WITH_UNIT;
        shearEnergy = DEFAULT_SHEAR_ENERGY;
        disassemblerHitEnergy = DEFAULT_DISASSEMBLER_HIT_ENERGY;
        disassemblerDamage = DEFAULT_DISASSEMBLER_DAMAGE;
        mekaToolHitEnergy = DEFAULT_MEKA_TOOL_HIT_ENERGY;
        mekaToolDamage = DEFAULT_MEKA_TOOL_DAMAGE;
        mekaToolAmplifiedDamage = DEFAULT_MEKA_TOOL_AMPLIFIED_DAMAGE;
        bowEnergyPerShot = DEFAULT_BOW_ENERGY_PER_SHOT;
        teleportRange = DEFAULT_TELEPORT_RANGE;
        flamethrowerHydrogen = DEFAULT_FLAMETHROWER_HYDROGEN;
        flamethrowerDamage = DEFAULT_FLAMETHROWER_DAMAGE;
        jetpackHydrogenPerTick = DEFAULT_JETPACK_HYDROGEN_PER_TICK;
        jetpackThrust = DEFAULT_JETPACK_THRUST;
        jetpackMaxSpeed = DEFAULT_JETPACK_MAX_SPEED;
        jetpackWalkSpeed = DEFAULT_JETPACK_WALK_SPEED;
        jetpackSprintSpeed = DEFAULT_JETPACK_SPRINT_SPEED;
        scubaOxygen = DEFAULT_SCUBA_OXYGEN_PER_SECOND;
        fallEnergyPerDamage = DEFAULT_FALL_ENERGY_PER_DAMAGE;
        magnetEnergyPerItem = DEFAULT_MAGNET_ENERGY_PER_ITEM;
        magnetRange = DEFAULT_MAGNET_RANGE;
        breathingEnergy = DEFAULT_BREATHING_ENERGY;
        visionEnergy = DEFAULT_VISION_ENERGY;
        nutritionEnergy = DEFAULT_NUTRITION_ENERGY;
        purificationEnergy = DEFAULT_PURIFICATION_ENERGY;
        flightEnergy = DEFAULT_FLIGHT_ENERGY;
        locomotiveEnergy = DEFAULT_LOCOMOTIVE_ENERGY;
        hydraulicEnergy = DEFAULT_HYDRAULIC_ENERGY;
        repulsorEnergy = DEFAULT_REPULSOR_ENERGY;
        gyroscopicEnergy = DEFAULT_GYROSCOPIC_ENERGY;
        servoMinEnergy = DEFAULT_SERVO_MIN_ENERGY;
        solarEnergy = DEFAULT_SOLAR_ENERGY;
        geothermalEnergy = DEFAULT_GEOTHERMAL_ENERGY;
        distributionEnergy = DEFAULT_DISTRIBUTION_ENERGY;
    }
}
