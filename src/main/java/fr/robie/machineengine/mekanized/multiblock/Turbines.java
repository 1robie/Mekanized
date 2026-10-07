package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.multiblock.MultiblockShape;
import fr.robie.machineengine.api.multiblock.MultiblockStructure;
import fr.robie.machineengine.api.multiblock.MultiblockType;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.multiblock.ReactorMachines.*;

/**
 * The industrial turbine: steam through the vents turns the blades, coils make energy, condensers give
 * water back. A turbine makes up to 10 J per mB of steam, with 28 blades and enough coils.
 */
public final class Turbines {
    /** A turbine's Joules per mB of steam with all its blades driven. */
    public static final double STEAM_ENERGY = 10;
    /** Blades for a turbine's full yield, blades a coil drives, steam a vent lets through per tick. */
    public static final int FULL_BLADES = 28;
    public static final int BLADES_PER_COIL = 4;
    public static final long VENT_FLOW = 32_000;
    public static final long CONDENSER_RATE = 64_000;

    public static final MultiblockType TURBINE = new MultiblockType(key("turbine"), new MultiblockShape.Box(key("turbine_casing"), Set.of(key("turbine_casing"), key("turbine_valve"), key("turbine_vent"), key("structural_glass")), block -> AIR.contains(block) || Set.of("turbine_rotor", "rotational_complex", "electromagnetic_coil", "pressure_disperser",
                    "saturating_condenser").contains(block.value()) && block.namespace().equals(MekanizedContent.NAMESPACE)), structure -> Map.of("steam", structure.volume() * 64_000L, "energy", structure.volume() * 16_000_000L,
                    "water", Math.max(1, structure.count(key("saturating_condenser")) * CONDENSER_RATE * EVERY_TICKS)));

    private Turbines() {
    }

    static void register(RegistrationContext context) {
        List<MultiblockType> turbine = List.of(TURBINE);
        context.machineTypes().register(MachineType.builder(key("turbine_casing"))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .data(STATUS)
                .behavior(MultiblockMachines.members(turbine).frame(Turbines::turn, EVERY_TICKS))
                .build());
        // Valves take steam and give energy and condensed water, like Mekanism's (no modes).
        context.machineTypes().register(valve("turbine_valve", turbine, List.of("steam"), Map.of("input", List.of("energy", "water")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .build());
        context.machineTypes().register(MachineType.builder(key("turbine_vent")).behavior(MultiblockMachines.members(turbine).frame()).build());
        // A rotor holds up to two blades (right-click it with turbine blades).
        context.machineTypes().register(MachineType.builder(key("turbine_rotor"))
                .storage(StorageDefinition.slots("blades", 1, 2).filter(Items.ofItem(key("turbine_blade"))))
                .behavior(sleeping())
                .build());
        inert(context, "rotational_complex", "electromagnetic_coil", "saturating_condenser");
    }

    /** Turbine: steam through the vents turns the blades; coils make energy; condensers give water back. */
    static void turn(MachineContext anchor, MultiblockStructure structure) {
        long vents = 0;
        long blades = 0;
        BlockPosition min = structure.min();
        BlockPosition max = structure.max();
        for (int x = min.x(); x <= max.x(); x++) for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) {
            BlockPosition at = new BlockPosition(x, y, z);
            Key block = anchor.world().blockAt(at);
            if (block == null) continue;
            if (block.equals(key("turbine_vent"))) vents++;
            else if (block.equals(key("turbine_rotor"))) {
                blades += anchor.machines().storage(new WorldPosition(structure.world(), at), "blades")
                        .map(handler -> handler.views().stream().mapToLong(StorageView::amount).sum()).orElse(0L);
            }
        }
        long coils = structure.count(key("electromagnetic_coil"));
        if (structure.count(key("rotational_complex")) == 0 || coils == 0 || vents == 0) {
            idle(anchor, "Needs a rotational complex, coils and vents");
            return;
        }
        if (blades == 0) {
            idle(anchor, "Needs blades on its rotors");
            return;
        }
        double perMb = STEAM_ENERGY * Math.min(1.0, Math.min(blades, coils * BLADES_PER_COIL) / (double) FULL_BLADES);
        long steam = Math.min(amount(anchor, "steam"), vents * VENT_FLOW * EVERY_TICKS);
        steam = Math.min(steam, (long) (room(anchor, "energy") / perMb));
        if (steam <= 0) {
            idle(anchor, amount(anchor, "steam") == 0 ? "Needs steam" : "Energy buffer full");
            return;
        }
        long energy = (long) (steam * perMb);
        long water = Math.min(Math.min(steam, structure.count(key("saturating_condenser")) * CONDENSER_RATE * EVERY_TICKS), room(anchor, "water"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("steam").extract(chemical("steam"), steam, transaction);
            anchor.storage("energy").insert(MekanizedContent.ENERGY.variant(), energy, transaction);
            if (water > 0) anchor.storage("water").insert(fluid("water"), water, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "%,d J/t from %,d mB/t of steam (%d blades)", energy / EVERY_TICKS, steam / EVERY_TICKS, blades));
    }
}
