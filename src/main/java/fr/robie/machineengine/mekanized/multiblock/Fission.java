package fr.robie.machineengine.mekanized.multiblock;

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
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedConfig;
import fr.robie.machineengine.mekanized.machine.DataKeys;
import fr.robie.machineengine.mekanized.machine.Radiation;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.multiblock.Fusion.FUSION;
import static fr.robie.machineengine.mekanized.multiblock.ReactorMachines.*;
import static fr.robie.machineengine.mekanized.multiblock.Sps.SPS;

/**
 * The fission reactor: burns fissile fuel into waste and heat; coolant carries the heat away; too hot, it
 * SCRAMs. 1 mB of fissile fuel gives 1 MJ, so 1 mB/t of fuel boils 20,000 mB/t.
 */
public final class Fission {
    // The casing's block config options (D34); the anchor is a casing.
    /** Joules of heat per mB of fissile fuel burned. */
    public static final ConfigKey<Long> FISSION_HEAT_PER_MB = ConfigKey.whole("heat-per-mb-burned", 1_000_000, 1, MekanizedConfig.MAX).clamped();
    /** Heat that turns 1 mB of coolant (water or sodium) into steam or superheated sodium. */
    public static final ConfigKey<Long> COOLANT_HEAT_PER_MB = ConfigKey.whole("coolant-heat-per-mb", 50, 1, MekanizedConfig.MAX).clamped();
    /** What the reactor holds per fuel assembly inside it. */
    public static final ConfigKey<Long> FUEL_PER_ASSEMBLY = ConfigKey.whole("fuel-per-assembly", 8_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> WASTE_PER_ASSEMBLY = ConfigKey.whole("waste-per-assembly", 8_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> WATER_PER_ASSEMBLY = ConfigKey.whole("water-per-assembly", 100_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> SODIUM_PER_ASSEMBLY = ConfigKey.whole("sodium-per-assembly", 100_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> STEAM_PER_ASSEMBLY = ConfigKey.whole("steam-per-assembly", 1_000_000, 1, MekanizedConfig.MAX).clamped();
    public static final ConfigKey<Long> HOT_PER_ASSEMBLY = ConfigKey.whole("superheated-sodium-per-assembly", 1_000_000, 1, MekanizedConfig.MAX).clamped();
    /** Fission burn rate in mB/t. */
    public static final MachineDataKey<String> BURN_RATE = DataKeys.string("burn_rate", "0.1");
    /** Heat the fission reactor holds (J) and its damage (0 to 100, a SCRAM at 100). */
    public static final MachineDataKey<Long> REACTOR_HEAT = DataKeys.number("reactor_heat");
    public static final MachineDataKey<Long> DAMAGE = DataKeys.number("damage");

    public static final MultiblockType FISSION = new MultiblockType(key("fission_reactor"), new MultiblockShape.Box(key("fission_reactor_casing"), Set.of(key("fission_reactor_casing"), key("fission_reactor_port"), key("fission_reactor_logic_adapter"), key("reactor_glass")), block -> AIR.contains(block) || block.equals(key("fission_fuel_assembly")) || block.equals(key("control_rod_assembly"))), (structure, options) -> {
                long assemblies = Math.max(1, structure.count(key("fission_fuel_assembly")));
                return Map.of("fuel", assemblies * options.get(FUEL_PER_ASSEMBLY), "waste", assemblies * options.get(WASTE_PER_ASSEMBLY),
                        "water", assemblies * options.get(WATER_PER_ASSEMBLY), "sodium", assemblies * options.get(SODIUM_PER_ASSEMBLY),
                        "steam", assemblies * options.get(STEAM_PER_ASSEMBLY), "hot", assemblies * options.get(HOT_PER_ASSEMBLY));
            });

    private Fission() {
    }

    static void register(RegistrationContext context) {
        List<MultiblockType> fission = List.of(FISSION);
        context.machineTypes().register(MachineType.builder(key("fission_reactor_casing"))
                .storage(StorageDefinition.tank("fuel", 1).filter(IS_CHEMICAL.and(named("fissile_fuel"))))
                .storage(StorageDefinition.tank("waste", 1).filter(IS_CHEMICAL.and(named("nuclear_waste"))))
                .storage(StorageDefinition.tank("water", 1).filter(IS_FLUID.and(named("water"))))
                .storage(StorageDefinition.tank("sodium", 1).filter(IS_CHEMICAL.and(named("sodium"))))
                .storage(StorageDefinition.tank("steam", 1).filter(IS_CHEMICAL.and(named("steam"))))
                .storage(StorageDefinition.tank("hot", 1).filter(IS_CHEMICAL.and(named("superheated_sodium"))))
                .data(STATUS).data(RUNNING).data(BURN_RATE).data(REACTOR_HEAT).data(DAMAGE)
                .behavior(MultiblockMachines.members(fission).frame(Fission::fission, EVERY_TICKS))
                .build());
        context.machineTypes().register(valve("fission_reactor_port", fission, List.of("fuel", "water", "sodium"),
                Map.of("input", List.of(), "output_coolant", List.of("steam", "hot"), "output_waste", List.of("waste")))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .build());
        context.machineTypes().register(MachineType.builder(key("fission_reactor_logic_adapter")).behavior(MultiblockMachines.members(fission).frame()).build());
        context.machineTypes().register(MachineType.builder(key("reactor_glass")).behavior(MultiblockMachines.members(List.of(FISSION, FUSION, SPS)).frame()).build());
        inert(context, "fission_fuel_assembly", "control_rod_assembly");
    }

    /** Fission: burns fuel into waste and heat; coolant carries the heat away; too hot, it SCRAMs. */
    static void fission(MachineContext anchor, MultiblockStructure structure) {
        long heatPerMb = anchor.options().get(FISSION_HEAT_PER_MB);
        long assemblies = structure.count(key("fission_fuel_assembly"));
        long heat = anchor.get(REACTOR_HEAT);
        long damage = anchor.get(DAMAGE);
        long burned = 0;
        if (anchor.get(RUNNING) && assemblies > 0) {
            double rate = DataKeys.parseDouble(anchor.get(BURN_RATE), 0.1, 0, Double.POSITIVE_INFINITY);
            // At most 1 mB/t per fuel assembly.
            burned = Math.min(Math.round(Math.min(rate, assemblies) * EVERY_TICKS), Math.min(amount(anchor, "fuel"), room(anchor, "waste")));
            if (burned > 0) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("fuel").extract(chemical("fissile_fuel"), burned, transaction);
                    anchor.storage("waste").insert(chemical("nuclear_waste"), burned, transaction);
                    transaction.commit();
                }
                heat += burned * heatPerMb;
            }
        }
        // Coolant: water into steam, sodium into superheated sodium.
        heat -= cool(anchor, "water", "steam", chemical("steam"), heat);
        heat -= cool(anchor, "sodium", "hot", chemical("superheated_sodium"), heat);
        heat -= heat / 100; // the casing loses a little
        long safe = Math.max(1, assemblies) * 5L * heatPerMb;
        if (heat > safe) damage = Math.min(100, damage + Math.max(1, (heat - safe) * 10 / safe));
        else if (damage > 0) damage--;
        if (damage >= 100 && anchor.get(RUNNING)) {
            // SCRAM; the breach lets the waste out as radiation (Mekanism's meltdown, without the explosion).
            anchor.set(RUNNING, false);
            long waste = amount(anchor, "waste");
            if (waste > 0) {
                try (Transaction transaction = anchor.transactions().openOuter()) {
                    anchor.storage("waste").extract(chemical("nuclear_waste"), waste, transaction);
                    transaction.commit();
                }
                Radiation.release(anchor.machine().position(),
                        waste * Radiation.RADIOACTIVITY.get("nuclear_waste"));
            }
        }
        anchor.set(REACTOR_HEAT, heat);
        anchor.set(DAMAGE, damage);
        String state = !anchor.get(RUNNING) ? (damage >= 100 ? "SCRAM: too hot" : "Stopped")
                : assemblies == 0 ? "Needs fuel assemblies inside" : burned == 0 ? "Needs fissile fuel" : "Burning";
        anchor.setActive(burned > 0);
        anchor.set(STATUS, String.format(Locale.ROOT, "%s - %,d mB/s burned, heat %,d kJ, damage %d %%", state, burned, heat / 1000, damage));
    }

    /** Moves up to {@code heat} of the reactor's heat into coolant; returns the heat taken. */
    private static long cool(MachineContext anchor, String from, String to, ResourceVariant made, long heat) {
        long heatPerMb = anchor.options().get(COOLANT_HEAT_PER_MB);
        long amount = Math.min(Math.min(amount(anchor, from), room(anchor, to)), heat / heatPerMb);
        if (amount <= 0) return 0;
        try (Transaction transaction = anchor.transactions().openOuter()) {
            anchor.storage(from).extract(variant(anchor, from), amount, transaction);
            anchor.storage(to).insert(made, amount, transaction);
            transaction.commit();
        }
        return amount * heatPerMb;
    }
}
