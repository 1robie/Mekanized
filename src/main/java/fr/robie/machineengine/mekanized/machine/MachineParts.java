package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.machine.MachineBehavior;
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
}
