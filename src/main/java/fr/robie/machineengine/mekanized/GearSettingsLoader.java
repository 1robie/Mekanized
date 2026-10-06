package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.machine.GearSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Reads the {@code gear} section of {@code config.yml} into {@link GearSettings}, keeping the defaults for what is missing or invalid. */
final class GearSettingsLoader {
    /** Upper bound of a Joule or mB setting: far above any real value, small enough that sums never overflow. */
    private static final long MAX = 1_000_000_000_000L;

    private final ConfigurationSection config;
    private final Logger logger;

    private GearSettingsLoader(ConfigurationSection config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    static void load(@NotNull ConfigurationSection config, @NotNull Logger logger) {
        new GearSettingsLoader(config, logger).load();
    }

    private void load() {
        // Capacities: one number per group of items.
        GearSettings.energyCapacity = group("gear.energy-capacity", GearSettings.energyCapacity, Map.of(
                "energy-tablet", List.of("energy_tablet"),
                "atomic-disassembler", List.of("atomic_disassembler"),
                "portable-teleporter", List.of("portable_teleporter"),
                "meka-tool", List.of("meka_tool"),
                "electric-bow", List.of("electric_bow"),
                "free-runners", List.of("free_runners", "free_runners_armored"),
                "mekasuit", List.of("mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots")));
        GearSettings.energyUnitFactor = whole("gear.energy-unit-factor", GearSettings.DEFAULT_ENERGY_UNIT_FACTOR, 1, 1_000);
        GearSettings.chemicalCapacity = group("gear.chemical-capacity", GearSettings.chemicalCapacity, Map.of(
                "jetpack", List.of("jetpack", "jetpack_armored", "mekasuit_bodyarmor"),
                "flamethrower", List.of("flamethrower"),
                "scuba-tank", List.of("scuba_tank"),
                "canteen", List.of("canteen")));

        // Tools.
        GearSettings.toolEnergyPerBlock = whole("gear.tools.energy-per-block", GearSettings.DEFAULT_TOOL_ENERGY_PER_BLOCK, 0, MAX);
        GearSettings.veinLimit = (int) whole("gear.tools.vein-limit", GearSettings.DEFAULT_VEIN_LIMIT, 1, 4_096);
        GearSettings.veinLimitWithUnit = (int) whole("gear.tools.vein-limit-with-unit", GearSettings.DEFAULT_VEIN_LIMIT_WITH_UNIT, 1, 4_096);
        GearSettings.shearEnergy = whole("gear.tools.shear-energy", GearSettings.DEFAULT_SHEAR_ENERGY, 0, MAX);
        GearSettings.disassemblerHitEnergy = whole("gear.tools.disassembler-hit-energy", GearSettings.DEFAULT_DISASSEMBLER_HIT_ENERGY, 0, MAX);
        GearSettings.disassemblerDamage = decimal("gear.tools.disassembler-damage", GearSettings.DEFAULT_DISASSEMBLER_DAMAGE, 0, 1_000);
        GearSettings.mekaToolHitEnergy = whole("gear.tools.meka-tool-hit-energy", GearSettings.DEFAULT_MEKA_TOOL_HIT_ENERGY, 0, MAX);
        GearSettings.mekaToolDamage = decimal("gear.tools.meka-tool-damage", GearSettings.DEFAULT_MEKA_TOOL_DAMAGE, 0, 1_000);
        GearSettings.mekaToolAmplifiedDamage = decimal("gear.tools.meka-tool-amplified-damage", GearSettings.DEFAULT_MEKA_TOOL_AMPLIFIED_DAMAGE, 0, 1_000);
        GearSettings.bowEnergyPerShot = whole("gear.tools.bow-energy-per-shot", GearSettings.DEFAULT_BOW_ENERGY_PER_SHOT, 0, MAX);
        GearSettings.teleportRange = (int) whole("gear.tools.teleport-range", GearSettings.DEFAULT_TELEPORT_RANGE, 1, 256);
        GearSettings.flamethrowerHydrogen = whole("gear.tools.flamethrower-hydrogen", GearSettings.DEFAULT_FLAMETHROWER_HYDROGEN, 0, MAX);
        GearSettings.flamethrowerDamage = decimal("gear.tools.flamethrower-damage", GearSettings.DEFAULT_FLAMETHROWER_DAMAGE, 0, 1_000);

        // Jetpack and breathing.
        GearSettings.jetpackHydrogenPerTick = whole("gear.jetpack.hydrogen-per-tick", GearSettings.DEFAULT_JETPACK_HYDROGEN_PER_TICK, 0, MAX);
        GearSettings.jetpackThrust = decimal("gear.jetpack.thrust", GearSettings.DEFAULT_JETPACK_THRUST, 0, 2);
        GearSettings.jetpackMaxSpeed = decimal("gear.jetpack.max-speed", GearSettings.DEFAULT_JETPACK_MAX_SPEED, 0, 2);
        GearSettings.jetpackWalkSpeed = decimal("gear.jetpack.walk-speed", GearSettings.DEFAULT_JETPACK_WALK_SPEED, 0, 2);
        GearSettings.jetpackSprintSpeed = decimal("gear.jetpack.sprint-speed", GearSettings.DEFAULT_JETPACK_SPRINT_SPEED, 0, 2);
        GearSettings.scubaOxygen = whole("gear.scuba-oxygen-per-second", GearSettings.DEFAULT_SCUBA_OXYGEN_PER_SECOND, 0, MAX);

        // Module costs.
        GearSettings.fallEnergyPerDamage = whole("gear.modules.fall-energy-per-damage", GearSettings.DEFAULT_FALL_ENERGY_PER_DAMAGE, 0, MAX);
        GearSettings.magnetEnergyPerItem = whole("gear.modules.magnetic-attraction-energy", GearSettings.DEFAULT_MAGNET_ENERGY_PER_ITEM, 0, MAX);
        GearSettings.magnetRange = (int) whole("gear.modules.magnetic-attraction-range", GearSettings.DEFAULT_MAGNET_RANGE, 1, 64);
        GearSettings.breathingEnergy = whole("gear.modules.electrolytic-breathing-energy", GearSettings.DEFAULT_BREATHING_ENERGY, 0, MAX);
        GearSettings.visionEnergy = whole("gear.modules.vision-enhancement-energy", GearSettings.DEFAULT_VISION_ENERGY, 0, MAX);
        GearSettings.nutritionEnergy = whole("gear.modules.nutritional-injection-energy", GearSettings.DEFAULT_NUTRITION_ENERGY, 0, MAX);
        GearSettings.purificationEnergy = whole("gear.modules.inhalation-purification-energy", GearSettings.DEFAULT_PURIFICATION_ENERGY, 0, MAX);
        GearSettings.flightEnergy = whole("gear.modules.gravitational-modulating-energy", GearSettings.DEFAULT_FLIGHT_ENERGY, 0, MAX);
        GearSettings.locomotiveEnergy = whole("gear.modules.locomotive-boosting-energy", GearSettings.DEFAULT_LOCOMOTIVE_ENERGY, 0, MAX);
        GearSettings.hydraulicEnergy = whole("gear.modules.hydraulic-propulsion-energy", GearSettings.DEFAULT_HYDRAULIC_ENERGY, 0, MAX);
        GearSettings.repulsorEnergy = whole("gear.modules.hydrostatic-repulsor-energy", GearSettings.DEFAULT_REPULSOR_ENERGY, 0, MAX);
        GearSettings.gyroscopicEnergy = whole("gear.modules.gyroscopic-stabilization-energy", GearSettings.DEFAULT_GYROSCOPIC_ENERGY, 0, MAX);
        GearSettings.servoMinEnergy = whole("gear.modules.motorized-servo-min-energy", GearSettings.DEFAULT_SERVO_MIN_ENERGY, 0, MAX);
        GearSettings.solarEnergy = whole("gear.modules.solar-recharging-energy", GearSettings.DEFAULT_SOLAR_ENERGY, 0, MAX);
        GearSettings.geothermalEnergy = whole("gear.modules.geothermal-generator-energy", GearSettings.DEFAULT_GEOTHERMAL_ENERGY, 0, MAX);
        GearSettings.distributionEnergy = whole("gear.modules.charge-distribution-energy", GearSettings.DEFAULT_DISTRIBUTION_ENERGY, 0, MAX);
    }

    private long whole(String path, long fallback, long min, long max) {
        return MekanizedSettings.bounded(this.config, path, fallback, min, max, this.logger);
    }

    private double decimal(String path, double fallback, double min, double max) {
        return MekanizedSettings.boundedDouble(this.config, path, fallback, min, max, this.logger);
    }

    /**
     * A table of capacities with one key per group of items: {@code current} with each present group's number
     * (a positive whole number) written to its items.
     */
    private Map<String, Long> group(String path, Map<String, Long> current, Map<String, List<String>> groups) {
        Map<String, Long> result = new HashMap<>(current);
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            String key = path + "." + group.getKey();
            if (!this.config.contains(key)) continue;
            long fallback = current.getOrDefault(group.getValue().getFirst(), 1L);
            long value = whole(key, fallback, 1, MAX);
            for (String item : group.getValue()) result.put(item, value);
        }
        return Map.copyOf(result);
    }
}
