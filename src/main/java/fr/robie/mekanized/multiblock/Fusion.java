package fr.robie.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
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
 * The fusion reactor: lasers ignite it; then it burns deuterium and tritium (half each of the injection
 * rate) into energy.
 */
public final class Fusion {
    // The frame's block config options (D34); the anchor is a frame.
    /** Fusion: laser energy to ignite, Joules per mB of fuel burned. */
    public static final ConfigKey<Long> FUSION_IGNITION = ConfigKey.whole("ignition-energy", 400_000_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> FUSION_ENERGY_PER_MB = ConfigKey.whole("energy-per-mb", 200_000, 1, MekanizedConfig.MAX).clamped();
    /** What the reactor holds: each fuel, and its energy buffer. */
    public static final ConfigKey<Long> FUEL_TANK = ConfigKey.whole("fuel-tank", 10_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> ENERGY_BUFFER = ConfigKey.whole("energy-buffer", 1_000_000_000, 1, MekanizedConfig.MAX).clamped();
    /** Fusion injection rate in mB/t. */
    public static final MachineDataKey<String> INJECTION_RATE = DataKeys.string("injection_rate", "2");
    public static final MachineDataKey<Boolean> IGNITED = MachineDataKey.of("ignited",
            VersionedCodec.builder(key("ignited"), Codecs.BOOL).build(), false);

    public static final MultiblockType FUSION = new MultiblockType(key("fusion_reactor"), new MultiblockShape.Box(key("fusion_reactor_frame"), Set.of(key("fusion_reactor_frame"), key("fusion_reactor_port"), key("fusion_reactor_logic_adapter"),
                    key("laser_focus_matrix"), key("reactor_glass"), key("fusion_reactor_controller")), AIR::contains,
                    MultiblockShape.Box.MIN_SIZE, MultiblockShape.Box.MAX_SIZE, 0, false, key("fusion_reactor_controller")), (structure, options) -> Map.of("deuterium", options.get(FUEL_TANK), "tritium", options.get(FUEL_TANK),
                    "energy", options.get(ENERGY_BUFFER), "ignition", options.get(FUSION_IGNITION)));

    private Fusion() {
    }

    static void register(RegistrationContext context) {
        List<MultiblockType> fusion = List.of(FUSION);
        context.machineTypes().register(MachineType.builder(key("fusion_reactor_frame"))
                .storage(StorageDefinition.tank("deuterium", 1).filter(IS_CHEMICAL.and(named("deuterium"))))
                .storage(StorageDefinition.tank("tritium", 1).filter(IS_CHEMICAL.and(named("tritium"))))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .storage(StorageDefinition.tank("ignition", 1).filter(IS_ENERGY))
                .data(STATUS).data(INJECTION_RATE).data(IGNITED)
                .behavior(MultiblockMachines.members(fusion).frame(Fusion::fuse, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("fusion_reactor_port", fusion, List.of("deuterium", "tritium"),
                Map.of("input", List.of(), "output", List.of("energy")))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .build());
        for (String name : List.of("fusion_reactor_controller", "fusion_reactor_logic_adapter", "laser_focus_matrix")) {
            context.machineTypes().register(MachineType.builder(key(name)).behavior(MultiblockMachines.members(fusion).frame()).build());
        }
    }

    /** Fusion: lasers ignite it; then it burns deuterium and tritium (half each of the injection rate) into energy. */
    static void fuse(MachineContext anchor, MultiblockStructure structure) {
        long ignition = anchor.options().get(FUSION_IGNITION);
        long energyPerMb = anchor.options().get(FUSION_ENERGY_PER_MB);
        if (!anchor.get(IGNITED)) {
            boolean fueled = amount(anchor, "deuterium") > 0 && amount(anchor, "tritium") > 0;
            if (amount(anchor, "ignition") >= ignition && fueled) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("ignition").extract(MekanizedContent.ENERGY.variant(), ignition, transaction);
                    transaction.commit();
                }
                anchor.set(IGNITED, true);
            } else {
                idle(anchor, String.format(Locale.ROOT, "Not ignited: %,d / %,d MJ of laser energy%s",
                        amount(anchor, "ignition") / 1_000_000, ignition / 1_000_000, fueled ? "" : ", needs deuterium and tritium"));
                return;
            }
        }
        long rate = DataKeys.parseLong(anchor.get(INJECTION_RATE), 2, 2, Long.MAX_VALUE);
        long each = rate / 2 * EVERY_TICKS;
        if (amount(anchor, "deuterium") < each || amount(anchor, "tritium") < each) {
            anchor.set(IGNITED, false); // the plasma goes out
            idle(anchor, "Out of fuel: needs igniting again");
            return;
        }
        long energy = Math.min(2 * each * energyPerMb, room(anchor, "energy"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("deuterium").extract(chemical("deuterium"), each, transaction);
            anchor.storage("tritium").extract(chemical("tritium"), each, transaction);
            anchor.storage("energy").insert(MekanizedContent.ENERGY.variant(), energy, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Ignited: %,d J/t", energy / EVERY_TICKS));
    }
}
