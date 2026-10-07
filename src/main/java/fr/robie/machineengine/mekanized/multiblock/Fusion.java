package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
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
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.DataKeys;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.multiblock.ReactorMachines.*;

/**
 * The fusion reactor: lasers ignite it; then it burns deuterium and tritium (half each of the injection
 * rate) into energy.
 */
public final class Fusion {
    /** Fusion: laser energy to ignite, Joules per mB of fuel burned. */
    public static final long FUSION_IGNITION = 400_000_000;
    public static final long FUSION_ENERGY_PER_MB = 200_000;
    /** Fusion injection rate in mB/t. */
    public static final MachineDataKey<String> INJECTION_RATE = DataKeys.string("injection_rate", "2");
    public static final MachineDataKey<Boolean> IGNITED = MachineDataKey.of("ignited",
            VersionedCodec.builder(key("ignited"), Codecs.BOOL).build(), false);

    public static final MultiblockType FUSION = new MultiblockType(key("fusion_reactor"), new MultiblockShape.Box(key("fusion_reactor_frame"), Set.of(key("fusion_reactor_frame"), key("fusion_reactor_port"), key("fusion_reactor_logic_adapter"),
                    key("laser_focus_matrix"), key("reactor_glass"), key("fusion_reactor_controller")), AIR::contains,
                    MultiblockShape.Box.MIN_SIZE, MultiblockShape.Box.MAX_SIZE, 0, false, key("fusion_reactor_controller")), structure -> Map.of("deuterium", 10_000L, "tritium", 10_000L, "energy", 1_000_000_000L, "ignition", FUSION_IGNITION));

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
        if (!anchor.get(IGNITED)) {
            boolean fueled = amount(anchor, "deuterium") > 0 && amount(anchor, "tritium") > 0;
            if (amount(anchor, "ignition") >= FUSION_IGNITION && fueled) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("ignition").extract(MekanizedContent.ENERGY.variant(), FUSION_IGNITION, transaction);
                    transaction.commit();
                }
                anchor.set(IGNITED, true);
            } else {
                idle(anchor, String.format(Locale.ROOT, "Not ignited: %,d / %,d MJ of laser energy%s",
                        amount(anchor, "ignition") / 1_000_000, FUSION_IGNITION / 1_000_000, fueled ? "" : ", needs deuterium and tritium"));
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
        long energy = Math.min(2 * each * FUSION_ENERGY_PER_MB, room(anchor, "energy"));
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage("deuterium").extract(chemical("deuterium"), each, transaction);
            anchor.storage("tritium").extract(chemical("tritium"), each, transaction);
            anchor.storage("energy").insert(MekanizedContent.ENERGY.variant(), energy, transaction);
            transaction.commit();
        }
        working(anchor, String.format(Locale.ROOT, "Ignited: %,d J/t", energy / EVERY_TICKS));
    }
}
