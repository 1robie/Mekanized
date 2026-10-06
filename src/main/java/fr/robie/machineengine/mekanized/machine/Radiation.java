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

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Radiation (Phase 6 wave 6), a simpler take on Mekanism's: radioactive chemicals released into the
 * world (a fission reactor reaching full damage) become a source, in Sv/h, that fades by half every
 * few minutes. The platform measures each player's exposure (background, plus each source over the
 * squared distance), adds up their dose, applies its effects and saves the sources.
 */
public final class Radiation {
    /** Sv/h everywhere, as in Mekanism. */
    public static final double BACKGROUND = 100e-9;
    /** Each source keeps this much of its strength every second (half-life about 2.3 minutes). */
    public static final double DECAY_PER_SECOND = 0.995;
    /** Sources weaker than this are forgotten. */
    public static final double MIN_SOURCE = 1e-6;
    /** Sv/h a released millibucket of each chemical adds (Mekanism's radioactivity). */
    public static final Map<String, Double> RADIOACTIVITY = Map.of(
            "nuclear_waste", 0.01, "spent_nuclear_waste", 0.01, "fissile_fuel", 0.01, "polonium", 0.05,
            "plutonium", 0.02, "uranium_oxide", 0.001, "uranium_hexafluoride", 0.001);
    /** Radioactive waste barrel: what it holds, and how many mB decay every 20 ticks. */
    public static final long BARREL_CAPACITY = 512_000;
    public static final long BARREL_DECAY = 1;

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
        double level = BACKGROUND;
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
        SOURCES.replaceAll((at, strength) -> strength * DECAY_PER_SECOND);
        SOURCES.values().removeIf(strength -> strength < MIN_SOURCE);
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
                .storage(StorageDefinition.tank("waste", BARREL_CAPACITY).filter(radioactive))
                .port(PortDefinition.builder("waste", "waste").direction(PortDirection.INPUT).filter(radioactive).group("chemical").build())
                .behavior(barrel())
                .build());
    }

    /** Lets {@link #BARREL_DECAY} mB of what it holds decay every second. */
    static @NotNull MachineBehavior.Factory barrel() {
        return type -> context -> {
            boolean decayed = false;
            try (Transaction transaction = context.transactions().openOuter()) {
                for (StorageView view : context.storage("waste").views()) {
                    if (view.isEmpty()) continue;
                    decayed = context.storage("waste").extract(view.variant(), BARREL_DECAY, transaction) > 0;
                    break;
                }
                transaction.commit();
            }
            context.setActive(decayed);
            context.wakeIn(20);
        };
    }
}
