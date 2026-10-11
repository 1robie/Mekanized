package fr.robie.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.multiblock.MultiblockShape;
import fr.robie.machineengine.api.multiblock.MultiblockStructure;
import fr.robie.machineengine.api.multiblock.MultiblockType;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.mekanized.MekanizedConfig;
import fr.robie.mekanized.MekanizedContent;
import fr.robie.mekanized.machine.DataKeys;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.mekanized.MekanizedContent.key;
import static fr.robie.mekanized.multiblock.ReactorMachines.*;

/**
 * The supercritical phase shifter: energy turns polonium into antimatter, 1,000 mB and 1 GJ per mB.
 */
public final class Sps {
    // The casing's block config options (D34); the anchor is a casing.
    /** SPS: mB of polonium per mB of antimatter, Joules per mB of polonium. */
    public static final ConfigKey<Long> SPS_POLONIUM_PER_ANTIMATTER = ConfigKey.whole("polonium-per-antimatter", 1_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> SPS_ENERGY_PER_POLONIUM = ConfigKey.whole("energy-per-polonium", 1_000_000, 1, MekanizedConfig.MAX).clamped();
    /** What it holds: polonium, antimatter, energy. */
    public static final ConfigKey<Long> POLONIUM_TANK = ConfigKey.whole("polonium-tank", 10_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> ANTIMATTER_TANK = ConfigKey.whole("antimatter-tank", 1_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> ENERGY_BUFFER = ConfigKey.whole("energy-buffer", 2_000_000_000, 1, MekanizedConfig.MAX).clamped();
    /** Polonium processed towards the next mB of antimatter. */
    public static final MachineDataKey<Long> SPS_PROGRESS = DataKeys.number("sps_progress");

    public static final MultiblockType SPS = new MultiblockType(key("sps"), new MultiblockShape.Box(key("sps_casing"), Set.of(key("sps_casing"), key("sps_port"), key("reactor_glass"), key("structural_glass")), AIR::contains), (structure, options) -> Map.of("polonium", options.get(POLONIUM_TANK),
            "antimatter", options.get(ANTIMATTER_TANK), "energy", options.get(ENERGY_BUFFER)));

    private Sps() {
    }

    static void register(RegistrationContext context) {
        List<MultiblockType> sps = List.of(SPS);
        context.machineTypes().register(MachineType.builder(key("sps_casing"))
                .storage(StorageDefinition.tank("polonium", 1).filter(IS_CHEMICAL.and(named("polonium"))))
                .storage(StorageDefinition.tank("antimatter", 1).filter(IS_CHEMICAL.and(named("antimatter"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .data(STATUS).data(SPS_PROGRESS)
                .behavior(MultiblockMachines.members(sps).frame(Sps::shift, EVERY_TICKS))
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
    static void shift(MachineContext anchor, MultiblockStructure structure) {
        long poloniumPerAntimatter = anchor.options().get(SPS_POLONIUM_PER_ANTIMATTER);
        long energyPerPolonium = anchor.options().get(SPS_ENERGY_PER_POLONIUM);
        long polonium = Math.min(amount(anchor, "polonium"), amount(anchor, "energy") / energyPerPolonium);
        long progress = anchor.get(SPS_PROGRESS);
        if (room(anchor, "antimatter") <= 0) polonium = 0;
        if (polonium <= 0) {
            idle(anchor, amount(anchor, "polonium") == 0 ? "Needs polonium" : "Needs energy");
            return;
        }
        progress += polonium;
        long made = Math.min(progress / poloniumPerAntimatter, room(anchor, "antimatter"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("polonium").extract(chemical("polonium"), polonium, transaction);
            anchor.storage("energy").extract(MekanizedContent.ENERGY.variant(), polonium * energyPerPolonium, transaction);
            if (made > 0) anchor.storage("antimatter").insert(chemical("antimatter"), made, transaction);
            transaction.commit();
        }
        anchor.set(SPS_PROGRESS, progress - made * poloniumPerAntimatter);
        working(anchor, String.format(Locale.ROOT, "Shifting %,d mB/s of polonium (%d %% to the next mB)", polonium,
                anchor.get(SPS_PROGRESS) * 100 / poloniumPerAntimatter));
    }
}
