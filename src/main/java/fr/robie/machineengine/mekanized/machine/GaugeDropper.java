package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gauge dropper: carries up to its capacity (its item config's {@code capacity}, D32; Mekanism's
 * {@link #DEFAULT_CAPACITY} mB) of one fluid or chemical between machines. This moves the liquid between a
 * machine's storages and a dropper's contents; the platform keeps the contents in the item and shows them.
 */
public final class GaugeDropper {
    public static final long DEFAULT_CAPACITY = 16_000;

    /** What was taken: the type and how many mB. */
    public record Taken(@NotNull ResourceType type, long amount) {
    }

    private GaugeDropper() {
    }

    public static boolean isLiquid(@NotNull ResourceVariant variant) {
        return variant.category() == MekanizedContent.FLUID || variant.category() == MekanizedContent.CHEMICAL;
    }

    /** The line under the dropper's name: what it holds. */
    public static @NotNull String lore(@NotNull ResourceType type, long amount, long capacity) {
        return String.format(Locale.ROOT, "%s: %,d / %,d mB", MekanizedContent.displayName(type), amount, capacity);
    }

    /**
     * Takes up to the dropper's free room from the first storage with a liquid: of {@code held} if the dropper
     * already holds one, else of whichever comes first. {@code capacity} is what the dropper holds when full.
     *
     * @return what was taken, or null for nothing (the dropper is full, or the machine has none to give)
     */
    public static @Nullable Taken take(@NotNull MachineContext context, @NotNull Map<String, List<StorageView>> storages,
                                       @Nullable ResourceType held, long heldAmount, long capacity) {
        long room = capacity - heldAmount;
        for (Map.Entry<String, List<StorageView>> storage : storages.entrySet()) {
            for (StorageView view : storage.getValue()) {
                if (view.isEmpty() || !isLiquid(view.variant()) || held != null && view.variant().type() != held) continue;
                long taken;
                try (Transaction transaction = context.transactions().openOuter()) {
                    taken = context.storage(storage.getKey()).extract(view.variant(), room, transaction);
                    transaction.commit();
                }
                if (taken <= 0) continue;
                return new Taken(view.variant().type(), taken);
            }
        }
        return null;
    }

    /** Puts {@code amount} mB of {@code held} into the first storage that takes any; returns how much went in (0: none). */
    public static long putBack(@NotNull MachineContext context, @NotNull Iterable<String> storages, @NotNull ResourceType held, long amount) {
        for (String storage : storages) {
            long given;
            try (Transaction transaction = context.transactions().openOuter()) {
                given = context.storage(storage).insert(held.variant(), amount, transaction);
                transaction.commit();
            }
            if (given > 0) return given;
        }
        return 0;
    }
}
