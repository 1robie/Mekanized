package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.NetworkBehavior;
import fr.robie.machineengine.api.network.NetworkType;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Phase 6 wave 3 (MEKANIZED_PLAN.md): heat. Heat is a resource ({@link MekanizedContent#HEAT}, in
 * Joules): the resistive heater turns energy into it, the fuelwood heater burns furnace fuels for it,
 * thermodynamic conductors carry it, and a boiler's valves take it in.
 */
public final class HeatMachines {
    public static final ResourcePredicate IS_HEAT = ResourcePredicate.type(MekanizedContent.HEAT);
    private static final Predicate<ResourceVariant> HEAT = variant -> variant.type() == MekanizedContent.HEAT;
    public static final NetworkType HEAT_NETWORK = NetworkType.of(key("heat"), NetworkBehavior.distributing());
    /** Joules of heat each conductor tier carries per tick. */
    private static final long[] CONDUCTOR_RATES = {10_000, 40_000, 160_000, 640_000};
    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    /** Joules of energy per tick the resistive heater turns into heat (a setting in its menu). */
    public static final MachineDataKey<String> HEATER_RATE = DataKeys.string("heater_rate", "100");
    /** Heat per tick of a burning fuelwood heater (Mekanism's 400). */
    static final long FUELWOOD_HEAT = 400;

    private HeatMachines() {
    }

    static void register(RegistrationContext context) {
        context.networkTypes().register(HEAT_NETWORK);
        for (int i = 0; i < TIERS.length; i++) {
            context.transportTypes().register(TransportType.of(key(TIERS[i] + "_thermodynamic_conductor"), HEAT_NETWORK, CONDUCTOR_RATES[i], IS_HEAT));
        }
        context.machineTypes().register(heater("resistive_heater")
                .storage(StorageDefinition.tank("energy", 100_000).filter(MekanizedRegistry.IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(MekanizedRegistry.IS_ENERGY)
                        .group("energy").build())
                .data(HEATER_RATE)
                .behavior(resistive())
                .build());
        context.machineTypes().register(heater("fuelwood_heater")
                .storage(StorageDefinition.slots("fuel", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("fuel", "fuel").direction(PortDirection.INPUT).group("items").build())
                .data(EnergyBehaviors.BURN)
                .behavior(fuelwood())
                .build());
    }

    /** A heat buffer, given out on every side. */
    private static MachineType.Builder heater(String name) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("heat", 100_000).filter(IS_HEAT))
                .port(PortDefinition.builder("heat", "heat").direction(PortDirection.OUTPUT).filter(IS_HEAT).group("heat").build());
    }

    /** Turns its setting's Joules of energy per tick into as much heat, and gives it out. */
    static @NotNull MachineBehavior.Factory resistive() {
        return type -> context -> {
            long rate = DataKeys.parseLong(context.get(HEATER_RATE), 100, 0, Long.MAX_VALUE);
            boolean heated = false;
            var buffer = context.storage("heat").views().getFirst();
            long used = Math.min(buffer.capacity() - buffer.amount(), rate * 20);
            if (used > 0) {
                try (Transaction transaction = context.transactions().openOuter()) {
                    long taken = context.storage("energy").extract(MekanizedContent.ENERGY.variant(), used, transaction);
                    if (taken > 0 && context.storage("heat").insert(MekanizedContent.HEAT.variant(), taken, transaction) == taken) {
                        transaction.commit();
                        heated = true;
                    }
                }
            }
            context.setActive(heated);
            giveHeat(context);
            context.wakeIn(20);
        };
    }

    /** Burns furnace fuels, {@link #FUELWOOD_HEAT} heat per tick while one burns, and gives it out. */
    static @NotNull MachineBehavior.Factory fuelwood() {
        return type -> context -> {
            giveHeat(context);
            long burn = context.get(EnergyBehaviors.BURN);
            if (burn <= 0) burn = EnergyBehaviors.consumeFuel(context, "fuel", EnergyBehaviors.FURNACE_FUELS);
            if (burn <= 0) {
                context.setActive(false);
                context.wakeIn(20);
                return;
            }
            long ticks = Math.min(20, burn);
            try (Transaction transaction = context.transactions().openOuter()) {
                long inserted = context.storage("heat").insert(MekanizedContent.HEAT.variant(), FUELWOOD_HEAT * ticks, transaction);
                if (inserted < FUELWOOD_HEAT * ticks) {
                    context.setActive(false); // full: keep the fuel burning later
                    context.wakeIn(20);
                    return;
                }
                transaction.commit();
            }
            context.set(EnergyBehaviors.BURN, burn - ticks);
            context.setActive(true);
            context.wakeIn(ticks);
        };
    }

    /** Pushes the heat buffer into every neighbor that takes heat (a boiler valve, a conductor). */
    static void giveHeat(MachineContext context) {
        for (RelativeSide side : RelativeSide.values()) {
            context.push(side, "heat", HEAT, Long.MAX_VALUE);
        }
    }
}
