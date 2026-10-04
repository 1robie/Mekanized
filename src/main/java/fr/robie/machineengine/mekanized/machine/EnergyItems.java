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

import java.util.Map;

/**
 * Items that store energy (E4), such as the Energy Tablet: a charged one in a machine's energy slot
 * powers it, an energy cube's charge slot fills one. The energy lives in the item's own data, so the
 * item variant changes with its charge; how it is read and written is the platform's
 * ({@link #setAccess}, Paper: the item's persistent data).
 */
public final class EnergyItems {
    /** Joules each energy item holds when full. */
    public static final Map<Key, Long> CAPACITY = Map.ofEntries(
            Map.entry(MekanizedContent.key("energy_tablet"), 1_000_000L),
            // Phase 6 wave 5: tools and armour.
            Map.entry(MekanizedContent.key("atomic_disassembler"), 1_000_000L),
            Map.entry(MekanizedContent.key("portable_teleporter"), 1_000_000L),
            Map.entry(MekanizedContent.key("meka_tool"), 16_000_000L),
            Map.entry(MekanizedContent.key("electric_bow"), 120_000L),
            Map.entry(MekanizedContent.key("free_runners"), 64_000L),
            Map.entry(MekanizedContent.key("free_runners_armored"), 64_000L),
            Map.entry(MekanizedContent.key("mekasuit_helmet"), 16_000_000L),
            Map.entry(MekanizedContent.key("mekasuit_bodyarmor"), 16_000_000L),
            Map.entry(MekanizedContent.key("mekasuit_pants"), 16_000_000L),
            Map.entry(MekanizedContent.key("mekasuit_boots"), 16_000_000L));
    /** Matches energy items, whatever their charge. */
    public static final ResourcePredicate IS_ENERGY_ITEM = (variant, tags) -> capacity(variant) > 0;

    /** Reads and writes an energy item's charge. */
    public interface Access {
        long energy(@NotNull ResourceVariant item);

        /** The same item holding {@code energy} Joules. */
        @NotNull ResourceVariant withEnergy(@NotNull ResourceVariant item, long energy);
    }

    private static volatile @Nullable Access access;

    private EnergyItems() {
    }

    public static void setAccess(@Nullable Access value) {
        access = value;
    }

    /** The capacity of {@code variant} if it is an energy item, else 0. */
    public static long capacity(@NotNull ResourceVariant variant) {
        ItemData data = variant.type() == Items.ITEM ? variant.components().get(Items.DATA) : null;
        return data == null ? 0 : CAPACITY.getOrDefault(data.item(), 0L);
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
