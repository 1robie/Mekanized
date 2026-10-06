package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.config.ConfigSpec;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every setting of {@code plugins/Mekanized/config.yml} (D30): the file is generated from {@link #SPEC},
 * and missing keys are added at startup. {@link MekanizedSettings#apply} copies the values into the
 * settings classes. Numbers out of range are limited to the nearest allowed value.
 */
public final class MekanizedConfig {
    /** Upper bound of a Joule or mB setting: far above any real value, small enough that sums never overflow. */
    static final long MAX = 1_000_000_000_000L;

    private static final List<ConfigKey<?>> KEYS = new ArrayList<>();

    // -- transport and multiblocks ----------------------------------------------------------------

    public static final ConfigKey<List<Long>> UNIVERSAL_CABLE = tiers("transport.universal-cable", MekanizedSettings.DEFAULT_CABLE_RATES, "Joules per tick");
    public static final ConfigKey<List<Long>> PRESSURIZED_TUBE = tiers("transport.pressurized-tube", MekanizedSettings.DEFAULT_TUBE_RATES, "mB per tick");
    public static final ConfigKey<List<Long>> MECHANICAL_PIPE = tiers("transport.mechanical-pipe", MekanizedSettings.DEFAULT_PIPE_RATES, "mB per tick");
    public static final ConfigKey<List<Long>> LOGISTICAL_TRANSPORTER = tiers("transport.logistical-transporter",
            MekanizedSettings.DEFAULT_TRANSPORTER_RATES, "items every 10 ticks");
    public static final ConfigKey<Long> TANK_FLUID_PER_BLOCK = add(ConfigKey.whole("dynamic-tank.fluid-per-block",
            MekanizedSettings.DEFAULT_TANK_FLUID_PER_BLOCK, 1, MAX).comment("mB of fluid per block inside the tank"));
    public static final ConfigKey<Long> TANK_CHEMICAL_PER_BLOCK = add(ConfigKey.whole("dynamic-tank.chemical-per-block",
            MekanizedSettings.DEFAULT_TANK_CHEMICAL_PER_BLOCK, 1, MAX).comment("mB of chemical per block inside the tank"));
    public static final ConfigKey<Long> EVAPORATION_PER_LEVEL = add(ConfigKey.whole("thermal-evaporation.input-per-level-per-tick",
            MekanizedSettings.DEFAULT_EVAPORATION_PER_LEVEL, 1, MAX)
            .comment("mB of input evaporated per tick per level, by day (half at night, half again in rain)"));
    public static final ConfigKey<Long> EVAPORATION_TANK_PER_LEVEL = add(ConfigKey.whole("thermal-evaporation.input-tank-per-level",
            MekanizedSettings.DEFAULT_EVAPORATION_TANK_PER_LEVEL, 1, MAX).comment("mB of input the tower holds per level"));

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
    public static final ConfigKey<Long> CHUNK_ENERGY = joules("chunk-loading.energy-per-chunk", ChunkLoadingSettings.DEFAULT_ENERGY_PER_CHUNK,
            "J per tick for each chunk it keeps loaded");

    // -- radiation --------------------------------------------------------------------------------

    public static final ConfigKey<Double> RADIATION_BACKGROUND = decimal("radiation.background", RadiationSettings.DEFAULT_BACKGROUND, 0, 1,
            "Sv/h everywhere");
    public static final ConfigKey<Double> SOURCE_DECAY = decimal("radiation.source-decay-per-second", RadiationSettings.DEFAULT_SOURCE_DECAY_PER_SECOND,
            0.5, 1, "a released source keeps this much of its strength each second (0.5-1; half-life about 2.3 minutes)");
    public static final ConfigKey<Double> MIN_SOURCE = decimal("radiation.min-source", RadiationSettings.DEFAULT_MIN_SOURCE, 0, 1,
            "Sv/h below which a source is forgotten");
    public static final ConfigKey<Double> DOSE_DECAY = decimal("radiation.dose-decay-per-second", RadiationSettings.DEFAULT_DOSE_DECAY_PER_SECOND,
            0.5, 1, "a player's dose keeps this much each second (0.5-1; half-life about 11 minutes)");
    public static final ConfigKey<Double> SHIELDING = decimal("radiation.shielding-per-piece", RadiationSettings.DEFAULT_SHIELDING_PER_PIECE, 0, 0.25,
            "share of radiation each hazmat piece (or MekaSuit piece with a shielding unit) stops (0-0.25)");
    public static final ConfigKey<Double> SICKNESS_DOSE = decimal("radiation.sickness-dose", RadiationSettings.DEFAULT_SICKNESS_DOSE, 0, 1_000_000,
            "Sv from which players get hungry");
    public static final ConfigKey<Double> DAMAGE_DOSE = decimal("radiation.damage-dose", RadiationSettings.DEFAULT_DAMAGE_DOSE, 0, 1_000_000,
            "Sv from which players get nauseous, weak and hurt");
    public static final ConfigKey<Double> LETHAL_DOSE = decimal("radiation.lethal-dose", RadiationSettings.DEFAULT_LETHAL_DOSE, 0, 1_000_000,
            "Sv from which every second does heavy damage");
    public static final ConfigKey<Long> BARREL_CAPACITY = whole("radiation.barrel-capacity", RadiationSettings.DEFAULT_BARREL_CAPACITY, 1, MAX,
            "mB a radioactive waste barrel holds (a restart rebuilds the machine)");
    public static final ConfigKey<Long> BARREL_DECAY = whole("radiation.barrel-decay-per-second", RadiationSettings.DEFAULT_BARREL_DECAY, 0, MAX,
            "mB that decay in a barrel each second");

    // -- digital miner, QIO -----------------------------------------------------------------------

    public static final ConfigKey<Long> MINER_ENERGY = joules("digital-miner.energy-per-block", DigitalMinerSettings.DEFAULT_ENERGY_PER_BLOCK,
            "J per block mined");
    public static final ConfigKey<Long> MINER_TICKS = whole("digital-miner.ticks-per-block", DigitalMinerSettings.DEFAULT_TICKS_PER_BLOCK, 1, 72_000,
            "ticks per block mined (speed upgrades shorten it)");
    public static final ConfigKey<Integer> MINER_SCAN = integer("digital-miner.scan-per-tick", DigitalMinerSettings.DEFAULT_SCAN_PER_TICK, 1, 1_000_000,
            "positions it looks at per tick for the next block to mine");
    public static final ConfigKey<Integer> MINER_MAX_RADIUS = integer("digital-miner.max-radius", DigitalMinerSettings.DEFAULT_MAX_RADIUS, 1, 128,
            "widest radius (1-128); 32 is a 65 x 65 area");
    public static final ConfigKey<List<Long>> QIO_DRIVE_ITEMS = add(ConfigKey.wholes("qio.drive-items", longs(QioSettings.DEFAULT_DRIVE_ITEMS), 4, 1,
            Long.MAX_VALUE).comment("items each drive holds"));
    public static final ConfigKey<List<Long>> QIO_DRIVE_KINDS = add(ConfigKey.wholes("qio.drive-kinds", longs(QioSettings.DEFAULT_DRIVE_KINDS), 4, 1,
            Long.MAX_VALUE).comment("kinds of item each drive holds"));
    public static final ConfigKey<Integer> QIO_ARRAY_KINDS = integer("qio.array-kinds", QioSettings.DEFAULT_ARRAY_KINDS, 1, 16_384,
            "kinds of item a drive array holds at most, whatever its drives (1-16384)");
    public static final ConfigKey<Long> QIO_IO_RATE = whole("qio.importer-exporter-rate", QioSettings.DEFAULT_IO_RATE, 1, 1_000_000,
            "items an importer or exporter moves per second");

    // -- gear -------------------------------------------------------------------------------------

    /** One capacity per group of items: {@code free-runners} also sets the armored ones, {@code mekasuit} all four pieces. */
    public static final Map<ConfigKey<Long>, List<String>> ENERGY_CAPACITY = groups("gear.energy-capacity", GearSettings.defaultEnergyCapacity(),
            "energy-tablet", List.of("energy_tablet"),
            "atomic-disassembler", List.of("atomic_disassembler"),
            "portable-teleporter", List.of("portable_teleporter"),
            "meka-tool", List.of("meka_tool"),
            "electric-bow", List.of("electric_bow"),
            "free-runners", List.of("free_runners", "free_runners_armored"),
            "mekasuit", List.of("mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots"));
    public static final ConfigKey<Long> ENERGY_UNIT_FACTOR = whole("gear.energy-unit-factor", GearSettings.DEFAULT_ENERGY_UNIT_FACTOR, 1, 1_000,
            "an energy unit multiplies a piece's capacity by this");
    public static final Map<ConfigKey<Long>, List<String>> CHEMICAL_CAPACITY = groups("gear.chemical-capacity", GearSettings.defaultChemicalCapacity(),
            "jetpack", List.of("jetpack", "jetpack_armored", "mekasuit_bodyarmor"),
            "flamethrower", List.of("flamethrower"),
            "scuba-tank", List.of("scuba_tank"),
            "canteen", List.of("canteen"));
    public static final ConfigKey<Long> SCUBA_OXYGEN = whole("gear.scuba-oxygen-per-second", GearSettings.DEFAULT_SCUBA_OXYGEN_PER_SECOND, 0, MAX,
            "mB of oxygen each time the tank refills the player's air");

    public static final ConfigKey<Long> TOOL_ENERGY_PER_BLOCK = joules("gear.tools.energy-per-block", GearSettings.DEFAULT_TOOL_ENERGY_PER_BLOCK,
            "J per block mined (and the charge a disassembler or Meka-Tool needs to mine fast)");
    public static final ConfigKey<Integer> VEIN_LIMIT = integer("gear.tools.vein-limit", GearSettings.DEFAULT_VEIN_LIMIT, 1, 4_096,
            "blocks one vein mining breaks (sneaking)");
    public static final ConfigKey<Integer> VEIN_LIMIT_WITH_UNIT = integer("gear.tools.vein-limit-with-unit", GearSettings.DEFAULT_VEIN_LIMIT_WITH_UNIT,
            1, 4_096, "the same with the vein mining unit");
    public static final ConfigKey<Long> SHEAR_ENERGY = joules("gear.tools.shear-energy", GearSettings.DEFAULT_SHEAR_ENERGY, "J per animal sheared");
    public static final ConfigKey<Long> DISASSEMBLER_HIT_ENERGY = joules("gear.tools.disassembler-hit-energy", GearSettings.DEFAULT_DISASSEMBLER_HIT_ENERGY,
            "J per hit of a charged atomic disassembler");
    public static final ConfigKey<Double> DISASSEMBLER_DAMAGE = decimal("gear.tools.disassembler-damage", GearSettings.DEFAULT_DISASSEMBLER_DAMAGE,
            0, 1_000, "damage it deals at least (20 is ten hearts)");
    public static final ConfigKey<Long> MEKA_TOOL_HIT_ENERGY = joules("gear.tools.meka-tool-hit-energy", GearSettings.DEFAULT_MEKA_TOOL_HIT_ENERGY,
            "J per hit of a charged Meka-Tool");
    public static final ConfigKey<Double> MEKA_TOOL_DAMAGE = decimal("gear.tools.meka-tool-damage", GearSettings.DEFAULT_MEKA_TOOL_DAMAGE, 0, 1_000,
            "damage it deals at least");
    public static final ConfigKey<Double> MEKA_TOOL_AMPLIFIED_DAMAGE = decimal("gear.tools.meka-tool-amplified-damage",
            GearSettings.DEFAULT_MEKA_TOOL_AMPLIFIED_DAMAGE, 0, 1_000, "the same with the attack amplification unit");
    public static final ConfigKey<Long> BOW_ENERGY = joules("gear.tools.bow-energy-per-shot", GearSettings.DEFAULT_BOW_ENERGY_PER_SHOT,
            "J per shot of the electric bow");
    public static final ConfigKey<Integer> TELEPORT_RANGE = integer("gear.tools.teleport-range", GearSettings.DEFAULT_TELEPORT_RANGE, 1, 256,
            "blocks the teleportation unit looks ahead (1-256); its cost is under teleporter");
    public static final ConfigKey<Long> FLAMETHROWER_HYDROGEN = whole("gear.tools.flamethrower-hydrogen", GearSettings.DEFAULT_FLAMETHROWER_HYDROGEN,
            0, MAX, "mB of hydrogen per burst");
    public static final ConfigKey<Double> FLAMETHROWER_DAMAGE = decimal("gear.tools.flamethrower-damage", GearSettings.DEFAULT_FLAMETHROWER_DAMAGE,
            0, 1_000, "damage of a burst");

    public static final ConfigKey<Long> JETPACK_HYDROGEN = whole("gear.jetpack.hydrogen-per-tick", GearSettings.DEFAULT_JETPACK_HYDROGEN_PER_TICK,
            0, MAX, "mB of hydrogen per tick of thrust");
    public static final ConfigKey<Double> JETPACK_THRUST = decimal("gear.jetpack.thrust", GearSettings.DEFAULT_JETPACK_THRUST, 0, 2,
            "upward speed added each tick");
    public static final ConfigKey<Double> JETPACK_MAX_SPEED = decimal("gear.jetpack.max-speed", GearSettings.DEFAULT_JETPACK_MAX_SPEED, 0, 2,
            "top upward speed");
    public static final ConfigKey<Double> JETPACK_WALK_SPEED = decimal("gear.jetpack.walk-speed", GearSettings.DEFAULT_JETPACK_WALK_SPEED, 0, 2,
            "horizontal speed steered with the movement keys");
    public static final ConfigKey<Double> JETPACK_SPRINT_SPEED = decimal("gear.jetpack.sprint-speed", GearSettings.DEFAULT_JETPACK_SPRINT_SPEED, 0, 2,
            "the same while sprinting");

    public static final ConfigKey<Long> FALL_ENERGY = joules("gear.modules.fall-energy-per-damage", GearSettings.DEFAULT_FALL_ENERGY_PER_DAMAGE,
            "J per point of fall damage absorbed (free runners, MekaSuit boots)");
    public static final ConfigKey<Long> MAGNET_ENERGY = joules("gear.modules.magnetic-attraction-energy", GearSettings.DEFAULT_MAGNET_ENERGY_PER_ITEM,
            "J per item pulled");
    public static final ConfigKey<Integer> MAGNET_RANGE = integer("gear.modules.magnetic-attraction-range", GearSettings.DEFAULT_MAGNET_RANGE, 1, 64,
            "blocks");
    public static final ConfigKey<Long> BREATHING_ENERGY = joules("gear.modules.electrolytic-breathing-energy", GearSettings.DEFAULT_BREATHING_ENERGY);
    public static final ConfigKey<Long> VISION_ENERGY = joules("gear.modules.vision-enhancement-energy", GearSettings.DEFAULT_VISION_ENERGY);
    public static final ConfigKey<Long> NUTRITION_ENERGY = joules("gear.modules.nutritional-injection-energy", GearSettings.DEFAULT_NUTRITION_ENERGY);
    public static final ConfigKey<Long> PURIFICATION_ENERGY = joules("gear.modules.inhalation-purification-energy", GearSettings.DEFAULT_PURIFICATION_ENERGY,
            "per harmful effect removed");
    public static final ConfigKey<Long> FLIGHT_ENERGY = joules("gear.modules.gravitational-modulating-energy", GearSettings.DEFAULT_FLIGHT_ENERGY,
            "while flying; the charge needed to start flying");
    public static final ConfigKey<Long> LOCOMOTIVE_ENERGY = joules("gear.modules.locomotive-boosting-energy", GearSettings.DEFAULT_LOCOMOTIVE_ENERGY);
    public static final ConfigKey<Long> HYDRAULIC_ENERGY = joules("gear.modules.hydraulic-propulsion-energy", GearSettings.DEFAULT_HYDRAULIC_ENERGY);
    public static final ConfigKey<Long> REPULSOR_ENERGY = joules("gear.modules.hydrostatic-repulsor-energy", GearSettings.DEFAULT_REPULSOR_ENERGY);
    public static final ConfigKey<Long> GYROSCOPIC_ENERGY = joules("gear.modules.gyroscopic-stabilization-energy", GearSettings.DEFAULT_GYROSCOPIC_ENERGY);
    public static final ConfigKey<Long> SERVO_MIN_ENERGY = joules("gear.modules.motorized-servo-min-energy", GearSettings.DEFAULT_SERVO_MIN_ENERGY,
            "the charge the legs need to step up blocks");
    public static final ConfigKey<Long> SOLAR_ENERGY = joules("gear.modules.solar-recharging-energy", GearSettings.DEFAULT_SOLAR_ENERGY,
            "J made per second in daylight");
    public static final ConfigKey<Long> GEOTHERMAL_ENERGY = joules("gear.modules.geothermal-generator-energy", GearSettings.DEFAULT_GEOTHERMAL_ENERGY,
            "J made per second in lava or on fire");
    public static final ConfigKey<Long> DISTRIBUTION_ENERGY = joules("gear.modules.charge-distribution-energy", GearSettings.DEFAULT_DISTRIBUTION_ENERGY,
            "J given to each other piece per second");

    public static final ConfigSpec SPEC = ConfigSpec.builder()
            .header("Mekanized settings. Read when the server starts: restart to apply changes.",
                    "Defaults are Mekanism's. Recipes (durations, energy per tick, outputs) are edited in",
                    "plugins/MachineEngine/definitions/mekanized/ instead, and reload with the server's recipes.",
                    "Missing settings are added with their defaults when the server starts.")
            .section("transport", "How much a network moves per tick, per tier: [basic, advanced, elite, ultimate].")
            .section("teleporter", "The teleporter and the portable teleporter. Costs are Joules; defaults are Mekanism's.")
            .section("chunk-loading", "The dimensional stabilizer. Anchor upgrades always load only the machine's own chunk, for free.")
            .section("radiation", "Radiation (Mekanism's, simplified). Dose and radiation are in Sieverts (Sv, Sv/h).")
            .section("digital-miner", "The digital miner.")
            .section("qio", "The QIO network. Lists are for the drives: [base, hyper-dense, time-dilating, supermassive].")
            .section("gear", "Gear: tools, suits, packs and modules. Energy is in Joules, chemicals in mB.")
            .section("gear.energy-capacity", "Joules each item holds when full. free-runners also covers the armored ones;"
                    + " mekasuit covers all four pieces.")
            .section("gear.chemical-capacity", "mB of chemical each item holds. jetpack also covers the armored jetpack and the MekaSuit body armor.")
            .section("gear.modules", "J per second unless said otherwise.")
            .keys(KEYS)
            .build();

    private MekanizedConfig() {
    }

    private static <T> ConfigKey<T> add(ConfigKey<T> key) {
        KEYS.add(key);
        return key;
    }

    private static ConfigKey<List<Long>> tiers(String path, long[] defaults, String unit) {
        return add(ConfigKey.wholes(path, longs(defaults), 4, 1, MAX).comment(unit));
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

    /** One clamped key per group ({@code name, items, name, items, …}), its default the first item's capacity. */
    private static Map<ConfigKey<Long>, List<String>> groups(String path, Map<String, Long> defaults, Object... groups) {
        Map<ConfigKey<Long>, List<String>> keys = new LinkedHashMap<>();
        for (int i = 0; i < groups.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) groups[i + 1];
            keys.put(whole(path + "." + groups[i], defaults.get(items.getFirst()), 1, MAX), items);
        }
        return Collections.unmodifiableMap(keys);
    }

    private static List<Long> longs(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }
}
