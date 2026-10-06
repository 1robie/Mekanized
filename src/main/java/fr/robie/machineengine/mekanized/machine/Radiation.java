package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Radiation, a simpler take on Mekanism's: radioactive chemicals released into the
 * world (a fission reactor reaching full damage) become a source, in Sv/h, that fades by half every
 * few minutes ({@link RadiationSettings} has the numbers). The platform measures each player's exposure (background, plus each source over the
 * squared distance), adds up their dose, applies its effects and saves the sources.
 */
public final class Radiation {
    /** Sv/h a released millibucket of each chemical adds (Mekanism's radioactivity). */
    public static final Map<String, Double> RADIOACTIVITY = Map.of(
            "nuclear_waste", 0.01, "spent_nuclear_waste", 0.01, "fissile_fuel", 0.01, "polonium", 0.05,
            "plutonium", 0.02, "uranium_oxide", 0.001, "uranium_hexafluoride", 0.001);

    /** Source position -> its strength (Sv/h at one block). */
    private static final Map<WorldPosition, Double> SOURCES = new ConcurrentHashMap<>();

    private Radiation() {
    }

    public static @NotNull Map<WorldPosition, Double> sources() {
        return SOURCES;
    }

    /** Sv/h one millibucket of {@code variant} gives off; 0 if it is not radioactive. */
    public static double radioactivity(@NotNull ResourceVariant variant) {
        if (variant.category() != MekanizedContent.CHEMICAL) return 0;
        return RADIOACTIVITY.getOrDefault(variant.type().key().value(), 0.0);
    }

    /** Adds {@code strength} Sv/h to the source at {@code at}. */
    public static void release(@NotNull WorldPosition at, double strength) {
        if (strength > 0) SOURCES.merge(at, strength, Double::sum);
    }

    /** Radiation at a point: background plus every source of the world, over the squared distance. */
    public static double levelAt(@NotNull Key world, double x, double y, double z) {
        double level = RadiationSettings.background;
        for (Map.Entry<WorldPosition, Double> source : SOURCES.entrySet()) {
            WorldPosition at = source.getKey();
            if (!at.world().equals(world)) continue;
            double dx = at.position().x() + 0.5 - x, dy = at.position().y() + 0.5 - y, dz = at.position().z() + 0.5 - z;
            level += source.getValue() / Math.max(1, dx * dx + dy * dy + dz * dz);
        }
        return level;
    }

    /** Called once a second: sources fade. */
    public static void decay() {
        SOURCES.replaceAll((at, strength) -> strength * RadiationSettings.sourceDecayPerSecond);
        SOURCES.values().removeIf(strength -> strength < RadiationSettings.minSource);
    }

    private static final List<String> HAZMAT = List.of("hazmat_mask", "hazmat_gown", "hazmat_pants", "hazmat_boots");
    private static final List<String> MEKASUIT = List.of("mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots");

    /** How worrying a level (an hour's worth of it) or a dose is, in Sv. */
    public enum Severity {
        LOW, MEDIUM, HIGH
    }

    /** Below 0.1 Sv low, below 1 medium, above high. */
    public static @NotNull Severity severity(double sieverts) {
        return sieverts < 0.1 ? Severity.LOW : sieverts < 1 ? Severity.MEDIUM : Severity.HIGH;
    }

    /**
     * Whether the armour piece in {@code slot} (0 helmet, 1 chestplate, 2 leggings, 3 boots) wearing the gear
     * {@code id} (null: none) stops radiation: a hazmat piece, or a MekaSuit piece with a radiation shielding unit.
     */
    public static boolean shields(int slot, @Nullable String id, @NotNull Supplier<Set<String>> modules) {
        return HAZMAT.get(slot).equals(id) || MEKASUIT.get(slot).equals(id) && modules.get().contains("radiation_shielding_unit");
    }

    /** The share of radiation {@code pieces} shielding pieces stop ({@link RadiationSettings#shieldingPerPiece} each). */
    public static double shielding(int pieces) {
        double shielded = 0;
        for (int i = 0; i < pieces; i++) shielded += RadiationSettings.shieldingPerPiece;
        return shielded;
    }

    /** A player's dose one second later: the old one fades, and the radiation they took in (less what is shielded) adds up per hour of seconds. */
    public static double nextDose(double dose, double level, double shielding) {
        return dose * RadiationSettings.doseDecayPerSecond + level * (1 - shielding) / 3600;
    }

    /** From this dose a player gets hungry. */
    public static boolean causesSickness(double dose) {
        return dose >= RadiationSettings.sicknessDose;
    }

    /** From this dose a player gets nauseous and weak and hurt. */
    public static boolean causesDamage(double dose) {
        return dose >= RadiationSettings.damageDose;
    }

    /** From this dose every second hurts badly. */
    public static boolean isLethal(double dose) {
        return dose >= RadiationSettings.lethalDose;
    }

    /** "1.23 mSv/h" style: the unit that keeps the number readable. */
    public static @NotNull String format(double sieverts, @NotNull String suffix) {
        String[] units = {"Sv", "mSv", "μSv", "nSv"};
        double value = sieverts;
        int unit = 0;
        while (unit < units.length - 1 && Math.abs(value) < 1) {
            value *= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, "%.2f %s%s", value, units[unit], suffix);
    }

    static void register(RegistrationContext context) {
        ResourcePredicate radioactive = MekanizedRegistry.IS_CHEMICAL.and((variant, tags) -> radioactivity(variant) > 0);
        context.machineTypes().register(MachineType.builder(key("radioactive_waste_barrel"))
                .storage(StorageDefinition.tank("waste", RadiationSettings.barrelCapacity).filter(radioactive))
                .port(PortDefinition.builder("waste", "waste").direction(PortDirection.INPUT).filter(radioactive).group("chemical").build())
                .behavior(barrel())
                .build());
    }

    /** Lets {@link RadiationSettings#barrelDecay} mB of what it holds decay every second. */
    static @NotNull MachineBehavior.Factory barrel() {
        return type -> context -> {
            boolean decayed = false;
            try (Transaction transaction = context.transactions().openOuter()) {
                for (StorageView view : context.storage("waste").views()) {
                    if (view.isEmpty()) continue;
                    decayed = context.storage("waste").extract(view.variant(), RadiationSettings.barrelDecay, transaction) > 0;
                    break;
                }
                transaction.commit();
            }
            context.setActive(decayed);
            context.wakeIn(20);
        };
    }
}
