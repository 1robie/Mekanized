package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.world.WorldPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Where the teleporters and QIO drive arrays of each frequency are (positions stay known while unloaded). */
public final class Frequencies {
    /** Teleporter position -> its frequency and whether its frame is complete. */
    static final Map<WorldPosition, Teleporter> TELEPORTERS = new ConcurrentHashMap<>();
    /** Drive array position -> its frequency and what it holds (a snapshot, for dashboards in other regions). */
    static final Map<WorldPosition, Array> ARRAYS = new ConcurrentHashMap<>();
    private static volatile @Nullable Runnable onTeleportersChanged;

    public record Teleporter(@NotNull String frequency, boolean formed) {
    }

    public record Array(@NotNull String frequency, @NotNull Map<ResourceVariant, Long> contents, long capacity, long stored) {
    }

    private Frequencies() {
    }

    public static @NotNull Map<WorldPosition, Teleporter> teleporters() {
        return TELEPORTERS;
    }

    public static @NotNull Map<WorldPosition, Array> arrays() {
        return ARRAYS;
    }

    /** Called when a teleporter is added, changed or removed (the platform saves them). */
    public static void onTeleportersChanged(@Nullable Runnable listener) {
        onTeleportersChanged = listener;
    }

    static void teleporter(WorldPosition position, @Nullable Teleporter teleporter) {
        Teleporter old = teleporter == null ? TELEPORTERS.remove(position) : TELEPORTERS.put(position, teleporter);
        if (!Objects.equals(old, teleporter)) {
            Runnable listener = onTeleportersChanged;
            if (listener != null) listener.run();
        }
    }

    /** Mekanism's teleport cost by default: 1 kJ, plus 10 J per block; 100 kJ to another world ({@link TeleporterSettings}). */
    public static long cost(@NotNull WorldPosition from, @NotNull WorldPosition to) {
        if (!from.world().equals(to.world())) return TeleporterSettings.otherWorldCost;
        double dx = from.position().x() - to.position().x(), dy = from.position().y() - to.position().y(), dz = from.position().z() - to.position().z();
        return cost(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** The cost of a teleport over {@code blocks} blocks in one world: a base cost plus a cost per block ({@link TeleporterSettings}). */
    public static long cost(double blocks) {
        return TeleporterSettings.baseCost + (long) (blocks * TeleporterSettings.costPerBlock);
    }

    /** The other teleporter of {@code frequency} with a complete frame, or null. */
    public static @Nullable WorldPosition destination(@NotNull WorldPosition from, @NotNull String frequency) {
        for (Map.Entry<WorldPosition, Teleporter> entry : TELEPORTERS.entrySet()) {
            if (!entry.getKey().equals(from) && entry.getValue().formed() && entry.getValue().frequency().equals(frequency)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Drive arrays of {@code frequency}. */
    public static @NotNull List<WorldPosition> arrays(@NotNull String frequency) {
        List<WorldPosition> found = new ArrayList<>();
        ARRAYS.forEach((position, array) -> {
            if (array.frequency().equals(frequency)) found.add(position);
        });
        return found;
    }

    /** Forgets everything (tests). */
    public static void clear() {
        TELEPORTERS.clear();
        ARRAYS.clear();
    }
}
