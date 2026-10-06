package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Items that store energy (E4), such as the Energy Tablet: a charged one in a machine's energy slot
 * powers it, an energy cube's charge slot fills one. The energy lives in the item's own data, so the
 * item variant changes with its charge; how it is read and written is the platform's
 * ({@link #setAccess}, Paper: the item's persistent data).
 */
public final class EnergyItems {
    /** Matches energy items, whatever their charge. */
    public static final ResourcePredicate IS_ENERGY_ITEM = (variant, tags) -> capacity(variant) > 0;

    /** Reads and writes an energy item's charge. */
    public interface Access {
        long energy(@NotNull ResourceVariant item);

        /** The same item holding {@code energy} Joules. */
        @NotNull ResourceVariant withEnergy(@NotNull ResourceVariant item, long energy);

        /** The item's capacity, {@code base} unless something it carries changes it (the MekaSuit's energy unit). */
        default long capacity(@NotNull ResourceVariant item, long base) {
            return base;
        }
    }

    private static volatile @Nullable Access access;

    /** Joules {@code item} holds when full, without modules ({@link GearSettings#energyCapacity}); 0 if it holds no energy. */
    public static long baseCapacity(@NotNull Key item) {
        return item.namespace().equals(MekanizedContent.NAMESPACE) ? GearSettings.energyCapacity.getOrDefault(item.value(), 0L) : 0;
    }

    /** {@code item}'s capacity with the comma-separated {@code modules} it carries: an energy unit multiplies it. */
    public static long capacity(@NotNull Key item, @Nullable String modules) {
        long base = baseCapacity(item);
        return modules != null && java.util.List.of(modules.split(",")).contains("energy_unit") ? base * GearSettings.energyUnitFactor : base;
    }

    private EnergyItems() {
    }

    public static void setAccess(@Nullable Access value) {
        access = value;
    }

    /** The capacity of {@code variant} if it is an energy item, else 0. */
    public static long capacity(@NotNull ResourceVariant variant) {
        ItemData data = variant.type() == Items.ITEM ? variant.components().get(Items.DATA) : null;
        long base = data == null ? 0 : baseCapacity(data.item());
        Access current = access;
        return base <= 0 || current == null ? base : current.capacity(variant, base);
    }

    /**
     * Moves up to {@code maxRate} Joules between the energy item in {@code slot} and {@code energy}:
     * out of the item when {@code discharge}, into it otherwise. All or nothing per call.
     *
     * @return the Joules moved
     */
    public static long transfer(@NotNull MachineContext context, @NotNull String slot, @NotNull String energy, long maxRate, boolean discharge) {
        Access current = access;
        if (current == null) return 0;
        ResourceHandler items = context.storage(slot);
        ResourceHandler buffer = context.storage(energy);
        for (StorageView view : items.views()) {
            if (view.isEmpty() || view.amount() != 1) continue;
            long capacity = capacity(view.variant());
            if (capacity <= 0) continue;
            long stored = current.energy(view.variant());
            long room = discharge ? stored : capacity - stored;
            if (room <= 0) continue;
            try (Transaction transaction = context.transactions().openOuter()) {
                long amount;
                if (discharge) {
                    amount = buffer.insert(MekanizedContent.ENERGY.variant(), Math.min(room, maxRate), transaction);
                } else {
                    amount = buffer.extract(MekanizedContent.ENERGY.variant(), Math.min(room, maxRate), transaction);
                }
                if (amount <= 0) continue;
                ResourceVariant updated = current.withEnergy(view.variant(), discharge ? stored - amount : stored + amount);
                if (items.extract(view.variant(), 1, transaction) != 1 || items.insert(updated, 1, transaction) != 1) continue;
                transaction.commit();
                return amount;
            }
        }
        return 0;
    }
}
