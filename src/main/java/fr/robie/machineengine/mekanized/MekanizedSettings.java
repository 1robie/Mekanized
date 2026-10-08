package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.ConfigFile;
import fr.robie.machineengine.api.config.ConfigValues;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Server owners' settings ({@code plugins/Mekanized/config.yml}, keys in {@link MekanizedConfig}), read
 * once when the plugin loads, before Mekanized registers its machines. Defaults are Mekanism's numbers;
 * headless tests use them. Recipes (durations, energy, outputs) are edited in their own definition files instead.
 */
public final class MekanizedSettings {
    public static final long DEFAULT_TANK_FLUID_PER_BLOCK = 350_000;
    public static final long DEFAULT_TANK_CHEMICAL_PER_BLOCK = 16_000_000;
    public static final long DEFAULT_EVAPORATION_PER_LEVEL = 10;
    public static final long DEFAULT_EVAPORATION_TANK_PER_LEVEL = 16_000;

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
        tankFluidPerBlock = config.get(MekanizedConfig.TANK_FLUID_PER_BLOCK);
        tankChemicalPerBlock = config.get(MekanizedConfig.TANK_CHEMICAL_PER_BLOCK);
        evaporationPerLevel = config.get(MekanizedConfig.EVAPORATION_PER_LEVEL);
        evaporationTankPerLevel = config.get(MekanizedConfig.EVAPORATION_TANK_PER_LEVEL);

        TeleporterSettings.baseCost = config.get(MekanizedConfig.TELEPORT_BASE_COST);
        TeleporterSettings.costPerBlock = config.get(MekanizedConfig.TELEPORT_COST_PER_BLOCK);
        TeleporterSettings.otherWorldCost = config.get(MekanizedConfig.TELEPORT_OTHER_WORLD_COST);
        TeleporterSettings.cooldownMs = Math.round(config.get(MekanizedConfig.TELEPORT_COOLDOWN) * 1000);
        ChunkLoadingSettings.maxRadius = config.get(MekanizedConfig.CHUNK_MAX_RADIUS);

        RadiationSettings.background = config.get(MekanizedConfig.RADIATION_BACKGROUND);
        RadiationSettings.sourceDecayPerSecond = config.get(MekanizedConfig.SOURCE_DECAY);
        RadiationSettings.minSource = config.get(MekanizedConfig.MIN_SOURCE);
        RadiationSettings.doseDecayPerSecond = config.get(MekanizedConfig.DOSE_DECAY);
        RadiationSettings.sicknessDose = config.get(MekanizedConfig.SICKNESS_DOSE);
        RadiationSettings.damageDose = config.get(MekanizedConfig.DAMAGE_DOSE);
        RadiationSettings.lethalDose = config.get(MekanizedConfig.LETHAL_DOSE);

        DigitalMinerSettings.scanPerTick = config.get(MekanizedConfig.MINER_SCAN);
        DigitalMinerSettings.maxRadius = config.get(MekanizedConfig.MINER_MAX_RADIUS);
        QioSettings.arrayKinds = config.get(MekanizedConfig.QIO_ARRAY_KINDS);

    }

    private static long[] longs(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).toArray();
    }
}
