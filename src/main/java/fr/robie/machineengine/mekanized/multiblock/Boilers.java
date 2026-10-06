package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedContent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.multiblock.ReactorMachines.*;

/**
 * The thermoelectric boiler, as a box of frame blocks (Multiblocks): heat (from heaters, and from
 * superheated sodium) boils water into steam. Its anchor (lowest corner) holds the tanks and does the work
 * every second.
 *
 * <p>Boiling 1 mB of water (or heating 1 mB of sodium) takes {@link #HEAT_PER_MB} J of heat.</p>
 */
public final class Boilers {
    /** Heat to boil 1 mB of water into steam, or to heat 1 mB of sodium; what 1 mB of superheated sodium gives back. */
    public static final long HEAT_PER_MB = 50;
    /** mB of water each superheating element boils per tick at most. */
    public static final long BOIL_PER_ELEMENT = 320;

    public static final Multiblocks.Type BOILER = new Multiblocks.Type(key("boiler"), key("boiler_casing"),
            Set.of(key("boiler_casing"), key("boiler_valve"), key("structural_glass")),
            block -> AIR.contains(block) || block.equals(key("pressure_disperser")) || block.equals(key("superheating_element")),
            structure -> Map.of("water", structure.volume() * 16_000L, "steam", structure.volume() * 160_000L,
                    "heat", structure.volume() * 1_000_000L, "coolant", structure.volume() * 16_000L, "cooled", structure.volume() * 16_000L));

    private Boilers() {
    }

    static void register(RegistrationContext context) {
        List<Multiblocks.Type> boiler = List.of(BOILER);
        context.machineTypes().register(MachineType.builder(key("boiler_casing"))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("heat", 1).filter(IS_HEAT))
                .storage(StorageDefinition.tank("coolant", 1).filter(IS_CHEMICAL.and(named("superheated_sodium"))))
                .storage(StorageDefinition.tank("cooled", 1).filter(IS_CHEMICAL.and(named("sodium"))))
                .data(STATUS)
                .behavior(MemberBehavior.frame(boiler, Boilers::boil, EVERY_TICKS))
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
    static void boil(MachineContext anchor, Multiblocks.Structure structure) {
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
                anchor.storage("heat").insert(MekanizedContent.HEAT.variant(), cooled * HEAT_PER_MB, transaction);
                transaction.commit();
            }
        }
        long boiled = Math.min(Math.min(amount(anchor, "water"), amount(anchor, "heat") / HEAT_PER_MB),
                Math.min(elements * BOIL_PER_ELEMENT * EVERY_TICKS, room(anchor, "steam")));
        if (boiled <= 0) {
            idle(anchor, amount(anchor, "water") == 0 ? "Needs water" : amount(anchor, "heat") < HEAT_PER_MB ? "Needs heat" : "Steam tank full");
            return;
        }
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("water").extract(variant(anchor, "water"), boiled, transaction);
            anchor.storage("heat").extract(MekanizedContent.HEAT.variant(), boiled * HEAT_PER_MB, transaction);
            anchor.storage("steam").insert(chemical("steam"), boiled, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Boiling %,d mB/t", boiled / EVERY_TICKS));
    }
}
