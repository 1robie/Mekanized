package fr.robie.machineengine.mekanized.machine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which module goes into which gear, and how the module list of a piece is written. Plain Java: the
 * platform ({@code GearModules}) finds the worn piece and edits the item; this decides what fits.
 * Capacities are {@link EnergyItems#capacity(net.kyori.adventure.key.Key, String)} and {@link GearSettings}.
 */
public final class GearRules {
    /** The target that means "any worn MekaSuit piece". */
    public static final String ANY_MEKASUIT = "mekasuit";
    private static final String MEKASUIT_PREFIX = "mekasuit_";

    /** Modules -> the gear they go in. */
    private static final Map<String, String> MODULE_TARGETS = Map.ofEntries(
            Map.entry("electrolytic_breathing_unit", "mekasuit_helmet"), Map.entry("vision_enhancement_unit", "mekasuit_helmet"),
            Map.entry("nutritional_injection_unit", "mekasuit_helmet"), Map.entry("inhalation_purification_unit", "mekasuit_helmet"),
            Map.entry("jetpack_unit", "mekasuit_bodyarmor"), Map.entry("gravitational_modulating_unit", "mekasuit_bodyarmor"),
            Map.entry("elytra_unit", "mekasuit_bodyarmor"), Map.entry("locomotive_boosting_unit", "mekasuit_pants"),
            Map.entry("hydraulic_propulsion_unit", "mekasuit_boots"), Map.entry("excavation_escalation_unit", "meka_tool"),
            Map.entry("attack_amplification_unit", "meka_tool"), Map.entry("silk_touch_unit", "meka_tool"),
            Map.entry("fortune_unit", "meka_tool"), Map.entry("vein_mining_unit", "meka_tool"), Map.entry("teleportation_unit", "meka_tool"),
            Map.entry("radiation_shielding_unit", ANY_MEKASUIT), Map.entry("geiger_unit", "mekasuit_helmet"),
            Map.entry("dosimeter_unit", "mekasuit_helmet"), Map.entry("energy_unit", ANY_MEKASUIT),
            Map.entry("charge_distribution_unit", "mekasuit_bodyarmor"), Map.entry("gyroscopic_stabilization_unit", "mekasuit_bodyarmor"),
            Map.entry("solar_recharging_unit", "mekasuit_helmet"), Map.entry("geothermal_generator_unit", "mekasuit_pants"),
            Map.entry("motorized_servo_unit", "mekasuit_pants"), Map.entry("hydrostatic_repulsor_unit", "mekasuit_pants"),
            Map.entry("magnetic_attraction_unit", "mekasuit_boots"), Map.entry("frost_walker_unit", "mekasuit_boots"),
            Map.entry("soul_surfer_unit", "mekasuit_boots"), Map.entry("blasting_unit", "meka_tool"),
            Map.entry("farming_unit", "meka_tool"), Map.entry("shearing_unit", "meka_tool"));

    private GearRules() {
    }

    /** Every module with a place to go. */
    public static @NotNull Set<String> modules() {
        return MODULE_TARGETS.keySet();
    }

    /** The gear id {@code module} goes in ({@link #ANY_MEKASUIT} for any worn piece), or null if it does nothing. */
    public static @Nullable String target(@NotNull String module) {
        return MODULE_TARGETS.get(module);
    }

    /** An item's energy {@code capacity}, times {@code factor} when {@code modules} has an energy unit (saturating). */
    public static long energyCapacity(long capacity, @NotNull Set<String> modules, long factor) {
        if (!modules.contains("energy_unit") || capacity <= 0) return capacity;
        return factor > 0 && capacity > Long.MAX_VALUE / factor ? Long.MAX_VALUE : capacity * factor;
    }

    /** Whether the gear {@code gearId} (null: none) is what {@code target} asks for. */
    public static boolean fits(@NotNull String target, @Nullable String gearId) {
        if (target.equals(ANY_MEKASUIT)) return gearId != null && gearId.startsWith(MEKASUIT_PREFIX);
        return target.equals(gearId);
    }

    /**
     * For {@link #ANY_MEKASUIT}: the index of the first of {@code count} worn pieces that is a MekaSuit piece
     * without {@code module} yet; 0 when there is none.
     */
    public static int pickMekasuitPiece(int count, @NotNull IntFunction<@Nullable String> idOf,
                                        @NotNull IntFunction<Set<String>> modulesOf, @NotNull String module) {
        for (int i = 0; i < count; i++) {
            String id = idOf.apply(i);
            if (id != null && id.startsWith(MEKASUIT_PREFIX) && !modulesOf.apply(i).contains(module)) return i;
        }
        return 0;
    }

    /** The modules of a piece from its stored text (comma separated, in install order). */
    public static @NotNull Set<String> parseModules(@Nullable String text) {
        return text == null || text.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(text.split(",")));
    }

    /** The stored text of a module list; null for none (nothing is stored). */
    public static @Nullable String formatModules(@NotNull Iterable<String> modules) {
        String text = String.join(",", modules);
        return text.isEmpty() ? null : text;
    }
}
