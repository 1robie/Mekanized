package fr.robie.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.multiblock.MultiblockShape;
import fr.robie.machineengine.api.multiblock.MultiblockStructure;
import fr.robie.machineengine.api.multiblock.MultiblockType;
import fr.robie.machineengine.api.multiblock.Multiblocks;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.mekanized.MekanizedConfig;
import fr.robie.mekanized.MekanizedContent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.mekanized.MekanizedContent.key;
import static fr.robie.mekanized.multiblock.ReactorMachines.*;

/**
 * The thermoelectric boiler, as a box of frame blocks (Multiblocks): heat (from heaters, and from
 * superheated sodium) boils water into steam. Its anchor (lowest corner) holds the tanks and does the work
 * every second.
 *
 * <p>Boiling 1 mB of water (or heating 1 mB of sodium) takes {@link #HEAT_PER_MB} J of heat (50 by default).</p>
 */
public final class Boilers {
    // The casing's block config options (D34); the anchor is a casing.
    /** Heat to boil 1 mB of water into steam, or to heat 1 mB of sodium; what 1 mB of superheated sodium gives back. */
    public static final ConfigKey<Long> HEAT_PER_MB = ConfigKey.whole("heat-per-mb", 50, 1, MekanizedConfig.MAX).clamped();
    /** mB of water each superheating element boils per tick at most. */
    public static final ConfigKey<Long> BOIL_PER_ELEMENT = ConfigKey.whole("boil-per-element", 320, 1, MekanizedConfig.MAX).clamped();
    /** What the boiler holds per block inside it. */
    public static final ConfigKey<Long> WATER_PER_BLOCK = ConfigKey.whole("water-per-block", 16_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> STEAM_PER_BLOCK = ConfigKey.whole("steam-per-block", 160_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> HEAT_PER_BLOCK = ConfigKey.whole("heat-per-block", 1_000_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> COOLANT_PER_BLOCK = ConfigKey.whole("superheated-sodium-per-block", 16_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> COOLED_PER_BLOCK = ConfigKey.whole("sodium-per-block", 16_000, 1, MekanizedConfig.MAX).clamped();

    public static final MultiblockType BOILER = new MultiblockType(key("boiler"), new MultiblockShape.Box(key("boiler_casing"), Set.of(key("boiler_casing"), key("boiler_valve"), key("structural_glass")), block -> AIR.contains(block) || block.equals(key("pressure_disperser")) || block.equals(key("superheating_element"))), (structure, options) -> Map.of(
                    "water", structure.volume() * options.get(WATER_PER_BLOCK), "steam", structure.volume() * options.get(STEAM_PER_BLOCK),
                    "heat", structure.volume() * options.get(HEAT_PER_BLOCK), "coolant", structure.volume() * options.get(COOLANT_PER_BLOCK),
                    "cooled", structure.volume() * options.get(COOLED_PER_BLOCK)));

    private Boilers() {
    }

    static void register(RegistrationContext context) {
        List<MultiblockType> boiler = List.of(BOILER);
        context.machineTypes().register(MachineType.builder(key("boiler_casing"))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("heat", 1).filter(IS_HEAT))
                .storage(StorageDefinition.tank("coolant", 1).filter(IS_CHEMICAL.and(named("superheated_sodium"))))
                .storage(StorageDefinition.tank("cooled", 1).filter(IS_CHEMICAL.and(named("sodium"))))
                .data(STATUS)
                .behavior(MultiblockMachines.members(boiler).frame(Boilers::boil, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("boiler_valve", boiler, List.of("water", "coolant", "heat"),
                Map.of("input", List.of(), "output_steam", List.of("steam"), "output_coolant", List.of("cooled")))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("heat", 1).filter(IS_HEAT))
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("heat", "heat").filter(IS_HEAT).group("heat").build())
                .build());
        inert(context, "pressure_disperser", "superheating_element");
    }

    /** Boiler: heat (from heaters, and from superheated sodium) boils water into steam, up to the elements' rate. */
    static void boil(MachineContext anchor, MultiblockStructure structure) {
        long heatPerMb = anchor.options().get(HEAT_PER_MB);
        long boilPerElement = anchor.options().get(BOIL_PER_ELEMENT);
        long elements = structure.count(key("superheating_element"));
        if (structure.count(key("pressure_disperser")) == 0) {
            idle(anchor, "Needs a pressure disperser inside");
            return;
        }
        if (elements == 0) {
            idle(anchor, "Needs superheating elements inside");
            return;
        }
        // Superheated sodium gives its heat back and leaves as sodium.
        long cooled = Math.min(amount(anchor, "coolant"), room(anchor, "cooled"));
        if (cooled > 0) {
            try (Transaction transaction = anchor.transactions().openOuter()) {
                anchor.storage("coolant").extract(variant(anchor, "coolant"), cooled, transaction);
                anchor.storage("cooled").insert(chemical("sodium"), cooled, transaction);
                anchor.storage("heat").insert(MekanizedContent.HEAT.variant(), cooled * heatPerMb, transaction);
                transaction.commit();
            }
        }
        long boiled = Math.min(Math.min(amount(anchor, "water"), amount(anchor, "heat") / heatPerMb),
                Math.min(elements * boilPerElement * EVERY_TICKS, room(anchor, "steam")));
        if (boiled <= 0) {
            idle(anchor, amount(anchor, "water") == 0 ? "Needs water" : amount(anchor, "heat") < heatPerMb ? "Needs heat" : "Steam tank full");
            return;
        }
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("water").extract(variant(anchor, "water"), boiled, transaction);
            anchor.storage("heat").extract(MekanizedContent.HEAT.variant(), boiled * heatPerMb, transaction);
            anchor.storage("steam").insert(chemical("steam"), boiled, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Boiling %,d mB/t", boiled / EVERY_TICKS));
    }
}
