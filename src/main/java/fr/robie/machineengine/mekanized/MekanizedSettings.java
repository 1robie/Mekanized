package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.ConfigFile;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.config.ConfigValues;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server owners' settings ({@code plugins/Mekanized/config.yml}, keys in {@link MekanizedConfig}), read
 * once when the plugin loads, before Mekanized registers its machines. Defaults are Mekanism's numbers;
 * headless tests use them. Recipes (durations, energy, outputs) are edited in their own definition files instead.
 */
public final class MekanizedSettings {
    public static final long[] DEFAULT_CABLE_RATES = {8_000, 128_000, 1_024_000, 8_192_000};
    public static final long[] DEFAULT_TUBE_RATES = {750, 2_000, 64_000, 256_000};
    public static final long[] DEFAULT_PIPE_RATES = {250, 1_000, 8_000, 32_000};
    public static final long[] DEFAULT_TRANSPORTER_RATES = {8, 16, 32, 64};
    public static final long DEFAULT_TANK_FLUID_PER_BLOCK = 350_000;
    public static final long DEFAULT_TANK_CHEMICAL_PER_BLOCK = 16_000_000;
    public static final long DEFAULT_EVAPORATION_PER_LEVEL = 10;
    public static final long DEFAULT_EVAPORATION_TANK_PER_LEVEL = 16_000;

    /** Per tier (basic, advanced, elite, ultimate): Joules per tick a universal cable network moves. */
    public static long[] cableRates = DEFAULT_CABLE_RATES.clone();
    /** mB per tick a pressurized tube network moves. */
    public static long[] tubeRates = DEFAULT_TUBE_RATES.clone();
    /** mB per tick a mechanical pipe network moves. */
    public static long[] pipeRates = DEFAULT_PIPE_RATES.clone();
    /** Items a logistical transporter network moves every 10 ticks. */
    public static long[] transporterRates = DEFAULT_TRANSPORTER_RATES.clone();
    /** Dynamic Tank: mB of fluid, and of chemical, per block inside. */
    public static long tankFluidPerBlock = DEFAULT_TANK_FLUID_PER_BLOCK;
    public static long tankChemicalPerBlock = DEFAULT_TANK_CHEMICAL_PER_BLOCK;
    /** Thermal Evaporation Plant: input mB evaporated per tick per level (by day), input tank mB per level. */
    public static long evaporationPerLevel = DEFAULT_EVAPORATION_PER_LEVEL;
    public static long evaporationTankPerLevel = DEFAULT_EVAPORATION_TANK_PER_LEVEL;

    private MekanizedSettings() {
    }

    /** Loads {@code config.yml}, adding the settings it is missing, and applies it. */
    static void load(@NotNull Plugin plugin) {
        apply(ConfigFile.load(plugin, "config.yml", MekanizedConfig.SPEC));
    }

    /** Copies the settings into the classes that use them. */
    public static void apply(@NotNull ConfigValues config) {
        cableRates = longs(config.get(MekanizedConfig.UNIVERSAL_CABLE));
        tubeRates = longs(config.get(MekanizedConfig.PRESSURIZED_TUBE));
        pipeRates = longs(config.get(MekanizedConfig.MECHANICAL_PIPE));
        transporterRates = longs(config.get(MekanizedConfig.LOGISTICAL_TRANSPORTER));
        tankFluidPerBlock = config.get(MekanizedConfig.TANK_FLUID_PER_BLOCK);
        tankChemicalPerBlock = config.get(MekanizedConfig.TANK_CHEMICAL_PER_BLOCK);
        evaporationPerLevel = config.get(MekanizedConfig.EVAPORATION_PER_LEVEL);
        evaporationTankPerLevel = config.get(MekanizedConfig.EVAPORATION_TANK_PER_LEVEL);

        TeleporterSettings.baseCost = config.get(MekanizedConfig.TELEPORT_BASE_COST);
        TeleporterSettings.costPerBlock = config.get(MekanizedConfig.TELEPORT_COST_PER_BLOCK);
        TeleporterSettings.otherWorldCost = config.get(MekanizedConfig.TELEPORT_OTHER_WORLD_COST);
        TeleporterSettings.cooldownMs = Math.round(config.get(MekanizedConfig.TELEPORT_COOLDOWN) * 1000);
        ChunkLoadingSettings.maxRadius = config.get(MekanizedConfig.CHUNK_MAX_RADIUS);
        ChunkLoadingSettings.energyPerChunk = config.get(MekanizedConfig.CHUNK_ENERGY);

        RadiationSettings.background = config.get(MekanizedConfig.RADIATION_BACKGROUND);
        RadiationSettings.sourceDecayPerSecond = config.get(MekanizedConfig.SOURCE_DECAY);
        RadiationSettings.minSource = config.get(MekanizedConfig.MIN_SOURCE);
        RadiationSettings.doseDecayPerSecond = config.get(MekanizedConfig.DOSE_DECAY);
        RadiationSettings.shieldingPerPiece = config.get(MekanizedConfig.SHIELDING);
        RadiationSettings.sicknessDose = config.get(MekanizedConfig.SICKNESS_DOSE);
        RadiationSettings.damageDose = config.get(MekanizedConfig.DAMAGE_DOSE);
        RadiationSettings.lethalDose = config.get(MekanizedConfig.LETHAL_DOSE);
        RadiationSettings.barrelCapacity = config.get(MekanizedConfig.BARREL_CAPACITY);
        RadiationSettings.barrelDecay = config.get(MekanizedConfig.BARREL_DECAY);

        DigitalMinerSettings.energyPerBlock = config.get(MekanizedConfig.MINER_ENERGY);
        DigitalMinerSettings.ticksPerBlock = config.get(MekanizedConfig.MINER_TICKS);
        DigitalMinerSettings.scanPerTick = config.get(MekanizedConfig.MINER_SCAN);
        DigitalMinerSettings.maxRadius = config.get(MekanizedConfig.MINER_MAX_RADIUS);
        QioSettings.driveItems = longs(config.get(MekanizedConfig.QIO_DRIVE_ITEMS));
        QioSettings.driveKinds = longs(config.get(MekanizedConfig.QIO_DRIVE_KINDS));
        QioSettings.arrayKinds = config.get(MekanizedConfig.QIO_ARRAY_KINDS);
        QioSettings.ioRate = config.get(MekanizedConfig.QIO_IO_RATE);

        applyGear(config);
    }

    private static void applyGear(ConfigValues config) {
        GearSettings.energyUnitFactor = config.get(MekanizedConfig.ENERGY_UNIT_FACTOR);
        GearSettings.scubaOxygen = config.get(MekanizedConfig.SCUBA_OXYGEN);

        GearSettings.toolEnergyPerBlock = config.get(MekanizedConfig.TOOL_ENERGY_PER_BLOCK);
        GearSettings.veinLimit = config.get(MekanizedConfig.VEIN_LIMIT);
        GearSettings.veinLimitWithUnit = config.get(MekanizedConfig.VEIN_LIMIT_WITH_UNIT);
        GearSettings.shearEnergy = config.get(MekanizedConfig.SHEAR_ENERGY);
        GearSettings.disassemblerHitEnergy = config.get(MekanizedConfig.DISASSEMBLER_HIT_ENERGY);
        GearSettings.disassemblerDamage = config.get(MekanizedConfig.DISASSEMBLER_DAMAGE);
        GearSettings.mekaToolHitEnergy = config.get(MekanizedConfig.MEKA_TOOL_HIT_ENERGY);
        GearSettings.mekaToolDamage = config.get(MekanizedConfig.MEKA_TOOL_DAMAGE);
        GearSettings.mekaToolAmplifiedDamage = config.get(MekanizedConfig.MEKA_TOOL_AMPLIFIED_DAMAGE);
        GearSettings.bowEnergyPerShot = config.get(MekanizedConfig.BOW_ENERGY);
        GearSettings.teleportRange = config.get(MekanizedConfig.TELEPORT_RANGE);
        GearSettings.flamethrowerHydrogen = config.get(MekanizedConfig.FLAMETHROWER_HYDROGEN);
        GearSettings.flamethrowerDamage = config.get(MekanizedConfig.FLAMETHROWER_DAMAGE);

        GearSettings.jetpackHydrogenPerTick = config.get(MekanizedConfig.JETPACK_HYDROGEN);
        GearSettings.jetpackThrust = config.get(MekanizedConfig.JETPACK_THRUST);
        GearSettings.jetpackMaxSpeed = config.get(MekanizedConfig.JETPACK_MAX_SPEED);
        GearSettings.jetpackWalkSpeed = config.get(MekanizedConfig.JETPACK_WALK_SPEED);
        GearSettings.jetpackSprintSpeed = config.get(MekanizedConfig.JETPACK_SPRINT_SPEED);

        GearSettings.fallEnergyPerDamage = config.get(MekanizedConfig.FALL_ENERGY);
        GearSettings.magnetEnergyPerItem = config.get(MekanizedConfig.MAGNET_ENERGY);
        GearSettings.magnetRange = config.get(MekanizedConfig.MAGNET_RANGE);
        GearSettings.breathingEnergy = config.get(MekanizedConfig.BREATHING_ENERGY);
        GearSettings.visionEnergy = config.get(MekanizedConfig.VISION_ENERGY);
        GearSettings.nutritionEnergy = config.get(MekanizedConfig.NUTRITION_ENERGY);
        GearSettings.purificationEnergy = config.get(MekanizedConfig.PURIFICATION_ENERGY);
        GearSettings.flightEnergy = config.get(MekanizedConfig.FLIGHT_ENERGY);
        GearSettings.locomotiveEnergy = config.get(MekanizedConfig.LOCOMOTIVE_ENERGY);
        GearSettings.hydraulicEnergy = config.get(MekanizedConfig.HYDRAULIC_ENERGY);
        GearSettings.repulsorEnergy = config.get(MekanizedConfig.REPULSOR_ENERGY);
        GearSettings.gyroscopicEnergy = config.get(MekanizedConfig.GYROSCOPIC_ENERGY);
        GearSettings.servoMinEnergy = config.get(MekanizedConfig.SERVO_MIN_ENERGY);
        GearSettings.solarEnergy = config.get(MekanizedConfig.SOLAR_ENERGY);
        GearSettings.geothermalEnergy = config.get(MekanizedConfig.GEOTHERMAL_ENERGY);
        GearSettings.distributionEnergy = config.get(MekanizedConfig.DISTRIBUTION_ENERGY);
    }

    private static long[] longs(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }
}
