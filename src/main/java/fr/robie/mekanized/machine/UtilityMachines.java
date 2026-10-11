package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.List;

import static fr.robie.mekanized.MekanizedContent.key;
import static fr.robie.mekanized.machine.MekanizedRegistry.ENERGY_CONVERSION;
import static fr.robie.mekanized.machine.MekanizedRegistry.IS_ENERGY;

/**
 * Mekanism's utility blocks. Bins (one item, many of it), personal
 * chest and barrel (their owner's), the chargepad, the Fluidic Plenisher, the Formulaic Assemblicator,
 * the lasers, the Oredictionificator and the Industrial Alarm. Players use bins, personal storage and
 * chargepads through {@code PaperConfigurator}.
 */
public final class UtilityMachines {
    /**
     * Items each bin tier holds (Mekanism's); a block config's {@code storages.items.capacity} replaces it
     * (D34). The creative bin is refilled forever.
     */
    public static final long[] BIN_CAPACITY = {4_096, 8_192, 32_768, 262_144, Long.MAX_VALUE / 4};
    public static final String[] BIN_TIERS = {"basic", "advanced", "elite", "ultimate", "creative"};
    /** The player (UUID) a personal chest or barrel belongs to; empty until someone opens it. */
    public static final MachineDataKey<String> OWNER = DataKeys.string("owner", "");
    /** Redstone control of the lasers: ignored, high (works while powered) or low (while not powered). */
    public static final MachineDataKey<String> REDSTONE = DataKeys.string("redstone", "ignored");
    /** Joules the chargepad gives each player standing on it, per tick: its block config's option (D34). */
    public static final ConfigKey<Long> CHARGE_PER_TICK = ConfigKey.whole("charge-per-tick", 1_024_000 / 20, 0, 1_000_000_000_000L).clamped();
    private static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);

    private UtilityMachines() {
    }

    static void register(RegistrationContext context) {
        machines().forEach(context.machineTypes()::register);
    }

    /** Whether {@code type} is a bin (left-click takes, right-click stores). */
    public static boolean isBin(Key type) {
        return type.namespace().equals(MekanizedContent.NAMESPACE) && type.value().endsWith("_bin");
    }

    /** Whether {@code type} is a personal chest or barrel (only its owner opens it). */
    public static boolean isPersonal(Key type) {
        return type.equals(key("personal_chest")) || type.equals(key("personal_barrel"));
    }

    static List<MachineType> machines() {
        List<MachineType> machines = new ArrayList<>();
        for (int i = 0; i < BIN_TIERS.length; i++) {
            machines.add(MachineType.builder(key(BIN_TIERS[i] + "_bin"))
                    .storage(StorageDefinition.slots("items", 1, BIN_CAPACITY[i]).filter(Items.anyItem()))
                    .port(PortDefinition.builder("items", "items").direction(PortDirection.INPUT_OUTPUT).group("items").build())
                    .behavior(UtilityBehaviors.storage("items", i == BIN_TIERS.length - 1))
                    .build());
        }
        for (String name : List.of("personal_chest", "personal_barrel")) {
            machines.add(MachineType.builder(key(name))
                    .data(OWNER)
                    .storage(StorageDefinition.slots("items", 54, 64).filter(Items.anyItem()))
                    .behavior(UtilityBehaviors.storage("items", false))
                    .build());
        }
        // Players standing on it are charged by PaperConfigurator (a player task).
        machines.add(MachineType.builder(key("chargepad"))
                .storage(StorageDefinition.tank("energy", 2_400_000).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.BACK, RelativeSide.BOTTOM, RelativeSide.LEFT, RelativeSide.RIGHT).group("energy").build())
                .behavior(UtilityBehaviors.storage("energy", false))
                .build());
        machines.add(BasicMachines.base("fluidic_plenisher", 40_000)
                .storage(StorageDefinition.tank("fluid", 10_000).filter(IS_FLUID.and((variant, tags) -> UtilityBehaviors.worldFluid(variant) != null)))
                .storage(StorageDefinition.slots("bucket_in", 1, 16).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()))
                .port(PortDefinition.builder("fluid", "fluid").direction(PortDirection.INPUT).filter(IS_FLUID)
                        .sides(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP).group("fluid").build())
                .upgradeSlots(2, UtilityMachines::upgrade)
                .behavior(MachineParts.powered(MachineParts.withBuckets(UtilityBehaviors.plenisher("fluid", 2_000, 20, 32), "fluid")))
                .build());
        machines.add(BasicMachines.base("formulaic_assemblicator", 40_000)
                .storage(StorageDefinition.slots("pattern", 9, 1).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("input", 18, 64).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("output", 3, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .upgradeSlots(2, UtilityMachines::upgrade)
                .behavior(MachineParts.powered(UtilityBehaviors.assemblicator(4_000, 40)))
                .build());
        // Lasers fire out of their front; they take energy from the other sides.
        machines.add(MachineType.builder(key("laser"))
                .data(REDSTONE)
                .storage(StorageDefinition.tank("energy", 2_000_000).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM).group("energy").build())
                .behavior(UtilityBehaviors.laser(5_000, false, false))
                .build());
        machines.add(MachineType.builder(key("laser_amplifier"))
                .data(REDSTONE)
                .storage(StorageDefinition.tank("energy", 5_000_000_000L).filter(IS_ENERGY))
                .behavior(UtilityBehaviors.laser(5_000_000, true, false))
                .build());
        machines.add(MachineType.builder(key("laser_tractor_beam"))
                .data(REDSTONE)
                .storage(StorageDefinition.tank("energy", 5_000_000_000L).filter(IS_ENERGY))
                .storage(StorageDefinition.slots("items", 27, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("items", "items").direction(PortDirection.OUTPUT).group("items").build())
                .behavior(UtilityBehaviors.laser(5_000_000, true, true))
                .build());
        machines.add(MachineType.builder(key("oredictionificator"))
                .storage(StorageDefinition.slots("input", 1, 64).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .data(EjectBehavior.EJECT)
                .behavior(EjectBehavior.of(UtilityBehaviors.oredictionificator()))
                .build());
        machines.add(MachineType.builder(key("industrial_alarm"))
                .behavior(UtilityBehaviors.alarm(Key.key("minecraft", "block.bell.use")))
                .build());
        return machines;
    }

    private static boolean upgrade(UpgradeType upgrade) {
        return MekanizedRegistry.standardUpgrade(upgrade);
    }

    /** The energy item slot, auto-eject. */
}
