package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;

/** Building blocks shared by Mekanized's machine families. */
public final class MachineParts {
    private MachineParts() {
    }

    /**
     * {@code inner}, plus bucket slots on {@code tank}: a full bucket in {@code bucket_in} empties into
     * the tank, an empty one is filled from it; buckets come out in {@code bucket_out}.
     */
    public static @NotNull MachineBehavior.Factory withBuckets(@NotNull MachineBehavior.Factory inner, @NotNull String tank) {
        return ConvertingBehavior.of(inner,
                new ConvertingBehavior.Conversion("bucket_in", tank, ChemistryMachines.BUCKET_FILLING, "bucket_out"),
                new ConvertingBehavior.Conversion("bucket_in", tank, ChemistryMachines.BUCKET_DRAINING, "bucket_out"));
    }

    /** {@code inner}, plus the energy item slot (charged items and redstone become energy) and auto-eject. */
    public static @NotNull MachineBehavior.Factory powered(@NotNull MachineBehavior.Factory inner) {
        return EjectBehavior.of(ConvertingBehavior.of(inner,
                new ConvertingBehavior.Conversion("energy_slot", "energy", MekanizedRegistry.ENERGY_CONVERSION)));
    }

    /** Takes {@code joules} from {@code energy} in {@code transaction}; true only if all of it was there. */
    public static boolean pay(@NotNull ResourceHandler energy, long joules, @NotNull Transaction transaction) {
        return energy.extract(MekanizedContent.ENERGY.variant(), joules, transaction) == joules;
    }
}
