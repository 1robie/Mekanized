package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.config.ConfigSpec;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every setting of {@code plugins/Mekanized/config.yml} (D30): the file is generated from {@link #SPEC},
 * and missing keys are added at startup. {@link MekanizedSettings#apply} copies the values into the
 * settings classes. Numbers out of range are limited to the nearest allowed value.
 */
public final class MekanizedConfig {
    /** Upper bound of a Joule or mB setting: far above any real value, small enough that sums never overflow. */
    public static final long MAX = 1_000_000_000_000L;

    private static final List<ConfigKey<?>> KEYS = new ArrayList<>();

    // Only world-wide rules are here. A block's or an item's own numbers are in its CraftEngine config: gear,
    // modules, drives and the gauge dropper in their item config (D32); cables, machines and each structure's
    // casing in their block config (D34). Old keys left in a file for those (gear.*, transport.*, dynamic-tank.*,
    // thermal-evaporation.*, ...) are kept and ignored.

    // -- teleporter, chunk loading ----------------------------------------------------------------

    public static final ConfigKey<Long> TELEPORT_BASE_COST = joules("teleporter.base-cost", TeleporterSettings.DEFAULT_BASE_COST, "J of any teleport");
    public static final ConfigKey<Long> TELEPORT_COST_PER_BLOCK = joules("teleporter.cost-per-block", TeleporterSettings.DEFAULT_COST_PER_BLOCK,
            "J more per block travelled in one world");
    public static final ConfigKey<Long> TELEPORT_OTHER_WORLD_COST = joules("teleporter.other-world-cost", TeleporterSettings.DEFAULT_OTHER_WORLD_COST,
            "J of a teleport to another world (distance does not count)");
    public static final ConfigKey<Double> TELEPORT_COOLDOWN = decimal("teleporter.cooldown-seconds", TeleporterSettings.DEFAULT_COOLDOWN_MS / 1000.0,
            0, 3600, "seconds a player waits between two teleports");
    public static final ConfigKey<Integer> CHUNK_MAX_RADIUS = integer("chunk-loading.max-radius", ChunkLoadingSettings.DEFAULT_MAX_RADIUS, 0, 8,
            "chunks around its own a stabilizer can load (0-8); 2 is a 5 x 5 area");

    // -- radiation --------------------------------------------------------------------------------

    public static final ConfigKey<Double> RADIATION_BACKGROUND = decimal("radiation.background", RadiationSettings.DEFAULT_BACKGROUND, 0, 1,
            "Sv/h everywhere");
    public static final ConfigKey<Double> SOURCE_DECAY = decimal("radiation.source-decay-per-second", RadiationSettings.DEFAULT_SOURCE_DECAY_PER_SECOND,
            0.5, 1, "a released source keeps this much of its strength each second (0.5-1; half-life about 2.3 minutes)");
    public static final ConfigKey<Double> MIN_SOURCE = decimal("radiation.min-source", RadiationSettings.DEFAULT_MIN_SOURCE, 0, 1,
            "Sv/h below which a source is forgotten");
    public static final ConfigKey<Double> DOSE_DECAY = decimal("radiation.dose-decay-per-second", RadiationSettings.DEFAULT_DOSE_DECAY_PER_SECOND,
            0.5, 1, "a player's dose keeps this much each second (0.5-1; half-life about 11 minutes)");
    public static final ConfigKey<Double> SICKNESS_DOSE = decimal("radiation.sickness-dose", RadiationSettings.DEFAULT_SICKNESS_DOSE, 0, 1_000_000,
            "Sv from which players get hungry");
    public static final ConfigKey<Double> DAMAGE_DOSE = decimal("radiation.damage-dose", RadiationSettings.DEFAULT_DAMAGE_DOSE, 0, 1_000_000,
            "Sv from which players get nauseous, weak and hurt");
    public static final ConfigKey<Double> LETHAL_DOSE = decimal("radiation.lethal-dose", RadiationSettings.DEFAULT_LETHAL_DOSE, 0, 1_000_000,
            "Sv from which every second does heavy damage");

    // -- digital miner, QIO -----------------------------------------------------------------------

    public static final ConfigKey<Integer> MINER_SCAN = integer("digital-miner.scan-per-tick", DigitalMinerSettings.DEFAULT_SCAN_PER_TICK, 1, 1_000_000,
            "positions it looks at per tick for the next block to mine");
    public static final ConfigKey<Integer> MINER_MAX_RADIUS = integer("digital-miner.max-radius", DigitalMinerSettings.DEFAULT_MAX_RADIUS, 1, 128,
            "widest radius (1-128); 32 is a 65 x 65 area");
    public static final ConfigKey<Integer> QIO_ARRAY_KINDS = integer("qio.array-kinds", QioSettings.DEFAULT_ARRAY_KINDS, 1, 16_384,
            "kinds of item a drive array holds at most, whatever its drives (1-16384)");

    public static final ConfigKey<Map<String, String>> COLORS = add(ConfigKey.map("colors", Map.of())
            .comment("Menu colors of chemicals and fluids: resource (e.g. steam, or fluid/brine for a fluid) to \"#RRGGBB\".",
                    "Empty: Mekanism's colors. Restart to apply."));

    public static final ConfigSpec SPEC = ConfigSpec.builder()
            .header("Mekanized settings. Read when the server starts: restart to apply changes.",
                    "Defaults are Mekanism's. Recipes (durations, energy per tick, outputs) are edited in",
                    "plugins/MachineEngine/definitions/mekanized/ instead, and reload with the server's recipes.",
                    "Missing settings are added with their defaults when the server starts.",
                    "Each block's and item's own numbers (capacities, rates, costs) are in its CraftEngine config,",
                    "plugins/CraftEngine/resources/mekanized/configuration/: under behavior (blocks) or behaviors (items).")
            .section("teleporter", "The teleporter and the portable teleporter. Costs are Joules; defaults are Mekanism's.")
            .section("chunk-loading", "The dimensional stabilizer. Anchor upgrades always load only the machine's own chunk, for free.")
            .section("radiation", "Radiation (Mekanism's, simplified). Dose and radiation are in Sieverts (Sv, Sv/h).")
            .section("digital-miner", "The digital miner.")
            .section("qio", "The QIO network. What each drive holds is in its item config, an importer's or exporter's rate in its block config.")
            .keys(KEYS)
            .build();

    private MekanizedConfig() {
    }

    private static <T> ConfigKey<T> add(ConfigKey<T> key) {
        KEYS.add(key);
        return key;
    }

    private static ConfigKey<Long> joules(String path, long defaultValue, String... comment) {
        return whole(path, defaultValue, 0, MAX, comment);
    }

    private static ConfigKey<Long> whole(String path, long defaultValue, long min, long max, String... comment) {
        return add(ConfigKey.whole(path, defaultValue, min, max).clamped().comment(comment));
    }

    private static ConfigKey<Integer> integer(String path, int defaultValue, int min, int max, String... comment) {
        return add(ConfigKey.integer(path, defaultValue, min, max).clamped().comment(comment));
    }

    private static ConfigKey<Double> decimal(String path, double defaultValue, double min, double max, String... comment) {
        return add(ConfigKey.decimal(path, defaultValue, min, max).clamped().comment(comment));
    }
}
