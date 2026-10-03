package fr.robie.machineengine.mekanized;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Logger;

/**
 * Server owners' settings ({@code plugins/Mekanized/config.yml}), read once when the plugin loads,
 * before Mekanized registers its machines. Defaults are Mekanism's numbers; headless tests use them.
 * Recipes (durations, energy, outputs) are edited in their own definition files instead.
 */
public final class MekanizedSettings {
    /** Per tier (basic, advanced, elite, ultimate): Joules per tick a universal cable network moves. */
    public static long[] cableRates = {8_000, 128_000, 1_024_000, 8_192_000};
    /** mB per tick a pressurized tube network moves. */
    public static long[] tubeRates = {750, 2_000, 64_000, 256_000};
    /** mB per tick a mechanical pipe network moves. */
    public static long[] pipeRates = {250, 1_000, 8_000, 32_000};
    /** Items a logistical transporter network moves every 10 ticks. */
    public static long[] transporterRates = {8, 16, 32, 64};
    /** Dynamic Tank: mB of fluid, and of chemical, per block inside. */
    public static long tankFluidPerBlock = 350_000;
    public static long tankChemicalPerBlock = 16_000_000;
    /** Thermal Evaporation Plant: input mB evaporated per tick per level (by day), input tank mB per level. */
    public static long evaporationPerLevel = 10;
    public static long evaporationTankPerLevel = 16_000;

    private MekanizedSettings() {
    }

    static void load(@NotNull ConfigurationSection config, @NotNull Logger logger) {
        cableRates = tiers(config, "transport.universal-cable", cableRates, logger);
        tubeRates = tiers(config, "transport.pressurized-tube", tubeRates, logger);
        pipeRates = tiers(config, "transport.mechanical-pipe", pipeRates, logger);
        transporterRates = tiers(config, "transport.logistical-transporter", transporterRates, logger);
        tankFluidPerBlock = positive(config, "dynamic-tank.fluid-per-block", tankFluidPerBlock, logger);
        tankChemicalPerBlock = positive(config, "dynamic-tank.chemical-per-block", tankChemicalPerBlock, logger);
        evaporationPerLevel = positive(config, "thermal-evaporation.input-per-level-per-tick", evaporationPerLevel, logger);
        evaporationTankPerLevel = positive(config, "thermal-evaporation.input-tank-per-level", evaporationTankPerLevel, logger);
    }

    private static long[] tiers(ConfigurationSection config, String path, long[] fallback, Logger logger) {
        if (!config.contains(path)) return fallback;
        List<Long> values = config.getLongList(path);
        if (values.size() != 4 || values.stream().anyMatch(value -> value <= 0)) {
            logger.warning(path + " needs four positive numbers (basic, advanced, elite, ultimate); using the defaults");
            return fallback;
        }
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    private static long positive(ConfigurationSection config, String path, long fallback, Logger logger) {
        if (!config.contains(path)) return fallback;
        long value = config.getLong(path);
        if (value > 0) return value;
        logger.warning(path + " needs a positive number; using the default " + fallback);
        return fallback;
    }
}
