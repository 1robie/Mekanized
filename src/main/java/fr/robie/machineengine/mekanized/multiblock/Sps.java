package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.DataKeys;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.multiblock.ReactorMachines.*;

/**
 * The supercritical phase shifter: energy turns polonium into antimatter, 1,000 mB and 1 GJ per mB.
 */
public final class Sps {
    /** SPS: mB of polonium per mB of antimatter, Joules per mB of polonium. */
    public static final long SPS_POLONIUM_PER_ANTIMATTER = 1_000;
    public static final long SPS_ENERGY_PER_POLONIUM = 1_000_000;
    /** Polonium processed towards the next mB of antimatter. */
    public static final MachineDataKey<Long> SPS_PROGRESS = DataKeys.number("sps_progress");

    public static final Multiblocks.Type SPS = new Multiblocks.Type(key("sps"), key("sps_casing"),
            Set.of(key("sps_casing"), key("sps_port"), key("reactor_glass"), key("structural_glass")),
            AIR::contains,
            structure -> Map.of("polonium", 10_000L, "antimatter", 1_000L, "energy", 2_000_000_000L));

    private Sps() {
    }

    static void register(RegistrationContext context) {
        List<Multiblocks.Type> sps = List.of(SPS);
        context.machineTypes().register(MachineType.builder(key("sps_casing"))
                .storage(StorageDefinition.tank("polonium", 1).filter(IS_CHEMICAL.and(named("polonium"))))
                .storage(StorageDefinition.tank("antimatter", 1).filter(IS_CHEMICAL.and(named("antimatter"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .data(STATUS).data(SPS_PROGRESS)
                .behavior(MemberBehavior.frame(sps, Sps::shift, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("sps_port", sps, List.of("polonium", "energy"), Map.of("input", List.of(), "output", List.of("antimatter")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .build());
        inert(context, "supercharged_coil");
    }

    /** SPS: energy turns polonium into antimatter, 1,000 mB and 1 GJ per mB. */
    static void shift(MachineContext anchor, Multiblocks.Structure structure) {
        long polonium = Math.min(amount(anchor, "polonium"), amount(anchor, "energy") / SPS_ENERGY_PER_POLONIUM);
        long progress = anchor.get(SPS_PROGRESS);
        if (room(anchor, "antimatter") <= 0) polonium = 0;
        if (polonium <= 0) {
            idle(anchor, amount(anchor, "polonium") == 0 ? "Needs polonium" : "Needs energy");
            return;
        }
        progress += polonium;
        long made = Math.min(progress / SPS_POLONIUM_PER_ANTIMATTER, room(anchor, "antimatter"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("polonium").extract(chemical("polonium"), polonium, transaction);
            anchor.storage("energy").extract(MekanizedContent.ENERGY.variant(), polonium * SPS_ENERGY_PER_POLONIUM, transaction);
            if (made > 0) anchor.storage("antimatter").insert(chemical("antimatter"), made, transaction);
            transaction.commit();
        }
        anchor.set(SPS_PROGRESS, progress - made * SPS_POLONIUM_PER_ANTIMATTER);
        working(anchor, String.format(Locale.ROOT, "Shifting %,d mB/s of polonium (%d %% to the next mB)", polonium,
                anchor.get(SPS_PROGRESS) * 100 / SPS_POLONIUM_PER_ANTIMATTER));
    }
}
