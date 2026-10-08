package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.RelativeSide;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Mekanism's logistical sorter: every {@code everyTicks} it pulls up to a stack from the container or
 * machine behind it (its back) into its buffer, and pushes the buffer out of its front, into the
 * machine or container there; a transporter at the front pulls it from the {@code buffer} port. Only
 * items matching an item in its {@code filter} slots pass; with no filter, everything does.
 */
public final class SorterBehavior {
    private SorterBehavior() {
    }

    /** Every {@code every-ticks} it moves a stack of up to {@code stack-size} items: its block config's options (D34). */
    public static @NotNull MachineBehavior.Factory of(long defaultEveryTicks, int defaultStack) {
        ConfigKey<Long> everyKey = ConfigKey.whole("every-ticks", defaultEveryTicks, 1, 1_200).clamped();
        ConfigKey<Integer> stackKey = ConfigKey.integer("stack-size", defaultStack, 1, 64).clamped();
        return type -> context -> {
            long everyTicks = context.options().get(everyKey);
            int stack = context.options().get(stackKey);
            boolean moved = pull(context, stack);
            moved |= context.push(RelativeSide.FRONT, "buffer", variant -> true, stack).moved() > 0;
            context.setActive(moved);
            context.wakeIn(everyTicks); // containers changing do not wake machines: look again
        };
    }

    private static boolean pull(MachineContext context, int stack) {
        ResourceHandler buffer = context.storage("buffer");
        if (!buffer.views().getFirst().isEmpty()) return false;
        ResourceHandler behind = context.neighbor(RelativeSide.BACK, Capabilities.RESOURCE_HANDLER).orElse(null);
        if (behind == null || !behind.supportsExtraction()) return false;
        Set<Key> filters = filters(context);
        for (StorageView view : behind.views()) {
            if (view.isEmpty() || !passes(view.variant(), filters)) continue;
            try (Transaction transaction = context.transactions().openOuter()) {
                long taken = behind.extract(view.variant(), Math.min(view.amount(), stack), transaction);
                if (taken <= 0 || buffer.insert(view.variant(), taken, transaction) != taken) continue;
                transaction.commit();
                return true;
            }
        }
        return false;
    }

    private static Set<Key> filters(MachineContext context) {
        Set<Key> filters = new HashSet<>();
        for (StorageView view : context.storage("filter").views()) {
            ItemData data = view.isEmpty() ? null : view.variant().components().get(Items.DATA);
            if (data != null) filters.add(data.item());
        }
        return filters;
    }

    private static boolean passes(ResourceVariant variant, Set<Key> filters) {
        if (filters.isEmpty()) return true;
        ItemData data = variant.components().get(Items.DATA);
        return data != null && filters.contains(data.item());
    }
}
