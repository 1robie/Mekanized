package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.error.MachineError;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.machine.PushResult;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

/** Generators and energy storage (MEKANIZED_PLAN.md, Phase 2). Energy is {@link MekanizedContent#ENERGY}, in Joules. */
public final class EnergyBehaviors {
    public static final String ENERGY = "energy";
    private static final Predicate<ResourceVariant> IS_ENERGY = variant -> variant.type() == MekanizedContent.ENERGY;
    /** Ticks the current fuel item still burns; declared by fuel generators. */
    public static final MachineDataKey<Long> BURN = MachineDataKey.of("burn",
            VersionedCodec.builder(MekanizedContent.key("burn"), Codecs.LONG).build(), 0L);

    private EnergyBehaviors() {
    }

    /** A fuel: items matching {@code predicate} burn for {@code ticks}. */
    public record Fuel(@NotNull ResourcePredicate predicate, long ticks) {
        public static Fuel item(String id, long ticks) {
            return new Fuel(Items.ofItem(Key.key(id)), ticks);
        }

        public static Fuel tag(String tag, long ticks) {
            return new Fuel(Items.inTag(Key.key(tag)), ticks);
        }
    }

    /** Vanilla furnace fuels the heat generator burns (lava buckets are left out: the bucket would be lost). */
    public static final List<Fuel> FURNACE_FUELS = List.of(
            Fuel.item("minecraft:coal", 1600), Fuel.item("minecraft:charcoal", 1600), Fuel.item("minecraft:coal_block", 16000),
            Fuel.item("minecraft:blaze_rod", 2400), Fuel.item("minecraft:dried_kelp_block", 4000), Fuel.tag("minecraft:logs", 300),
            Fuel.tag("minecraft:planks", 300), Fuel.tag("minecraft:wooden_slabs", 150), Fuel.item("minecraft:stick", 100),
            Fuel.item("minecraft:bamboo", 50), Fuel.tag("c:dusts/charcoal", 1600), Fuel.tag("c:dusts/coal", 1600));

    /**
     * Burns one fuel item at a time from {@code fuel}, producing {@code joulesPerTick} while it burns,
     * and pushes energy to every neighbor. Pauses (keeping the burn) while the buffer is full.
     */
    public static @NotNull MachineBehavior.Factory fuelGenerator(@NotNull String fuel, long joulesPerTick, @NotNull List<Fuel> fuels) {
        return type -> context -> {
            pushAround(context, joulesPerTick * 2);
            long burn = context.get(BURN);
            if (burn <= 0) {
                burn = consumeFuel(context, fuel, fuels);
                if (burn <= 0) {
                    context.setActive(false);
                    context.waitForInput();
                    return;
                }
            }
            if (!insertEnergy(context, joulesPerTick)) {
                context.set(BURN, burn);
                context.setActive(false);
                context.block(new MachineError.TransferRejected("the energy buffer is full", null), 20);
                return;
            }
            context.set(BURN, burn - 1);
            context.setActive(true);
        };
    }

    /**
     * Produces {@code joulesPerTick} in daylight under open sky (a fifth while it rains), in batches
     * of 20 ticks, and pushes energy to every neighbor.
     */
    public static @NotNull MachineBehavior.Factory solar(long joulesPerTick) {
        return type -> context -> {
            MachineEnvironment environment = context.environment();
            long rate = !environment.skyVisible() || !environment.daytime() ? 0 : environment.raining() ? joulesPerTick / 5 : joulesPerTick;
            boolean produced = rate > 0 && insertEnergy(context, rate * 20);
            context.setActive(produced);
            pushAround(context, joulesPerTick * 40);
            context.wakeIn(20);
        };
    }

    /**
     * Stores energy and pushes it out of its front at {@code outputPerTick}, and charges the energy item
     * in its {@code charge} slot at the same rate; sleeps while that moves nothing.
     */
    public static @NotNull MachineBehavior.Factory cube(long outputPerTick, boolean creative) {
        return type -> context -> {
            if (creative) insertEnergy(context, Long.MAX_VALUE / 4);
            PushResult result = context.push(RelativeSide.FRONT, ENERGY, IS_ENERGY, outputPerTick);
            long charged = EnergyItems.transfer(context, "charge", ENERGY, outputPerTick, false);
            if (result.moved() == 0 && charged == 0) context.sleep();
        };
    }

    private static long consumeFuel(MachineContext context, String storage, List<Fuel> fuels) {
        ResourceHandler slot = context.storage(storage);
        for (StorageView view : slot.views()) {
            if (view.isEmpty()) continue;
            for (Fuel fuel : fuels) {
                if (!fuel.predicate().test(view.variant(), context.tags())) continue;
                try (Transaction transaction = context.transactions().openOuter()) {
                    if (slot.extract(view.variant(), 1, transaction) != 1) return 0;
                    transaction.commit();
                    return fuel.ticks();
                }
            }
        }
        return 0;
    }

    /** Inserts exactly {@code joules} (or as much as fits for very large amounts); false if nothing fits. */
    private static boolean insertEnergy(MachineContext context, long joules) {
        try (Transaction transaction = context.transactions().openOuter()) {
            long inserted = context.storage(ENERGY).insert(MekanizedContent.ENERGY.variant(), joules, transaction);
            if (inserted <= 0) return false;
            transaction.commit();
            return true;
        }
    }

    private static void pushAround(MachineContext context, long limit) {
        long left = limit;
        for (RelativeSide side : RelativeSide.values()) {
            if (left <= 0) return;
            left -= context.push(side, ENERGY, IS_ENERGY, left).moved();
        }
    }

    /** Burn ticks per bio fuel item (Mekanism's bio generator, simplified to items). */
    public static final List<Fuel> BIO_FUELS = List.of(Fuel.tag("c:fuels/bio", 200), Fuel.tag("c:fuels/block/bio", 1800));
}
