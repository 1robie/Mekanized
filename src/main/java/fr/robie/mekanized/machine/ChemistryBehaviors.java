package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.error.MachineError;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.PushResult;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.Fluids;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.mekanized.MekanizedConfig;
import fr.robie.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Tanks, the electric pump and the rotary condensentrator. */
public final class ChemistryBehaviors {
    /** The rotary condensentrator's mode: {@link #CONDENSING} (chemical to fluid) or {@link #DECONDENSING}. */
    public static final String CONDENSING = "condensing";
    public static final String DECONDENSING = "decondensing";
    public static final MachineDataKey<String> MODE = DataKeys.string("mode", CONDENSING);

    private ChemistryBehaviors() {
    }

    /**
     * Stores one resource and pushes it out of {@code side} at {@code outputPerTick} into the machine
     * there; sleeps while that moves nothing (tubes and pipes pull through the output port on their
     * own). A creative tank stays full of whatever was put in it.
     */
    public static @NotNull MachineBehavior.Factory tank(@NotNull String storage, @NotNull RelativeSide side, long outputPerTick,
                                                        boolean creative) {
        return type -> context -> {
            if (creative) refill(context, storage);
            // As fast as its output port: the block config's ports.out.rate replaces outputPerTick (D34).
            PushResult result = context.push(side, storage, variant -> true, context.options().portRate("out", outputPerTick));
            if (result.moved() == 0) context.sleep();
        };
    }

    private static void refill(MachineContext context, String storage) {
        ResourceHandler tank = context.storage(storage);
        for (StorageView view : tank.views()) {
            if (view.isEmpty() || view.amount() >= view.capacity()) continue;
            try (Transaction transaction = context.transactions().openOuter()) {
                tank.insert(view.variant(), view.capacity() - view.amount(), transaction);
                transaction.commit();
            }
        }
    }

    /** Heavy water a filter-upgraded pump gets from a block of water: its block config's option (D34). */
    public static final ConfigKey<Long> HEAVY_WATER_PER_BLOCK = ConfigKey.whole("heavy-water-per-block", 10, 1, 1_000_000).clamped();

    /**
     * Mekanism's electric pump: every {@code ticks} (shorter with speed upgrades) it takes one source
     * block of water or lava, below it or connected to the fluid below it, for {@code energy} Joules,
     * and stores a bucket of it (Fluids.bucket(), 1000 mB by default). Flowing fluid is followed to reach sources, up to {@code range} blocks
     * away; blocks of other regions (Folia) are not reached. The block config's options {@code energy}, {@code ticks} and
     * {@code range} replace these (D34).
     */
    public static @NotNull MachineBehavior.Factory pump(@NotNull String tank, @NotNull String energyStorage, long defaultEnergy,
                                                        long defaultTicks, int defaultRange) {
        ConfigKey<Long> energyKey = ConfigKey.whole("energy", defaultEnergy, 0, MekanizedConfig.MAX).clamped();
        ConfigKey<Long> ticksKey = ConfigKey.whole("ticks", defaultTicks, 1, 72_000).clamped();
        ConfigKey<Integer> rangeKey = ConfigKey.integer("range", defaultRange, 0, 128).clamped();
        return type -> context -> {
            int range = context.options().get(rangeKey);
            long cost = context.modifiedCost(MekanizedContent.ENERGY, context.options().get(energyKey));
            long delay = Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, context.options().get(ticksKey)));
            BlockPosition below = context.machine().position().position().relative(Direction.DOWN);
            WorldFluid start = context.world().fluidAt(below);
            ResourceType fluid = start == null ? null : Fluids.fromWorld(start.fluid());
            if (fluid == null) {
                context.setActive(false);
                context.wakeIn(40); // the world changing does not wake machines: look again later
                return;
            }
            ResourceHandler storage = context.storage(tank);
            ResourceHandler power = context.storage(energyStorage);
            // A filter upgrade separates heavy water out of each block of water (Mekanism's 10 mB).
            long amount = Fluids.bucket();
            ResourceType heavyWater = MekanizedContent.fluid("heavy_water");
            if (heavyWater != null && fluid == Fluids.water() && MekanizedRegistry.installed(context, MekanizedRegistry.FILTER_UPGRADE)) {
                fluid = heavyWater;
                amount = context.options().get(HEAVY_WATER_PER_BLOCK);
            }
            try (Transaction transaction = context.transactions().openOuter()) {
                if (storage.insert(fluid.variant(), amount, transaction) != amount) {
                    context.setActive(false);
                    context.block(new MachineError.TransferRejected("the tank is full", null), 20);
                    return;
                }
                if (!MachineParts.pay(power, cost, transaction)) {
                    context.setActive(false);
                    context.waitForInput();
                    return;
                }
                BlockPosition source = findSource(context, below, start.fluid(), range);
                if (source == null || !context.world().takeFluid(source)) {
                    context.setActive(false);
                    context.wakeIn(40);
                    return;
                }
                transaction.commit();
            }
            context.setActive(true);
            context.wakeIn(delay);
        };
    }

    /** The nearest source block of {@code fluid} reached through that fluid from {@code start}. */
    private static @Nullable BlockPosition findSource(MachineContext context, BlockPosition start, Key fluid, int range) {
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
        Set<BlockPosition> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        int budget = 4096;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPosition at = queue.poll();
            WorldFluid here = context.world().fluidAt(at);
            if (here == null || !here.fluid().equals(fluid)) continue;
            if (here.source()) return at;
            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP && at.y() >= start.y()) continue; // never above the pump
                BlockPosition next = at.relative(direction);
                if (Math.abs(next.x() - start.x()) > range || Math.abs(next.z() - start.z()) > range
                        || start.y() - next.y() > range) continue;
                if (seen.add(next)) queue.add(next);
            }
        }
        return null;
    }

    /**
     * The rotary condensentrator: {@link #MODE} picks the direction, chemical to fluid or back, and
     * turns the {@code chemical} and {@code fluid} ports into the matching input and output.
     */
    public static @NotNull MachineBehavior.Factory rotary(@NotNull MachineBehavior.Factory condensing,
                                                          @NotNull MachineBehavior.Factory decondensing) {
        return type -> new MachineBehavior() {
            private final MachineBehavior toFluid = condensing.create(type);
            private final MachineBehavior toChemical = decondensing.create(type);

            @Override
            public void tick(@NotNull MachineContext context) {
                (applyMode(context) ? this.toFluid : this.toChemical).tick(context);
            }

            @Override
            public void onLoad(@NotNull MachineContext context) {
                applyMode(context);
            }

            @Override
            public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
                applyMode(context);
            }

            @Override
            public void catchUp(@NotNull MachineContext context, long ticks) {
                (applyMode(context) ? this.toFluid : this.toChemical).catchUp(context, ticks);
            }
        };
    }

    /** @return whether the machine condenses (chemical to fluid) */
    private static boolean applyMode(MachineContext context) {
        boolean condensing = !DECONDENSING.equals(context.get(MODE));
        context.setPortDirection("chemical", condensing ? PortDirection.INPUT : PortDirection.OUTPUT);
        context.setPortDirection("fluid", condensing ? PortDirection.OUTPUT : PortDirection.INPUT);
        return condensing;
    }
}
