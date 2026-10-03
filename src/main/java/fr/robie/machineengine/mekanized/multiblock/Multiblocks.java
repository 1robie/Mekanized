package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Mekanism's multiblocks (MEKANIZED_PLAN.md, Phase 5a): hollow boxes of frame blocks. The formed
 * structures, by anchor (their lowest corner, a casing that holds their storage).
 */
public final class Multiblocks {
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 18;
    private static final Map<WorldPosition, Structure> FORMED = new ConcurrentHashMap<>();

    private Multiblocks() {
    }

    /**
     * A kind of multiblock.
     *
     * @param casing     the block of its edges (and of its anchor)
     * @param faces      blocks allowed in its walls: the casing, valves or ports, glass
     * @param interior   blocks allowed inside (air for a tank; cells and providers for the matrix)
     * @param capacities the anchor's storage capacities for a structure (per slot), by storage id
     */
    public record Type(@NotNull Key key, @NotNull Key casing, @NotNull Set<Key> faces, @NotNull Predicate<Key> interior,
                       @NotNull Function<Structure, Map<String, Long>> capacities) {
    }

    /** A formed structure: its box (walls included) and what its interior holds, by block id. */
    public record Structure(@NotNull Type type, @NotNull Key world, @NotNull BlockPosition min, @NotNull BlockPosition max,
                            @NotNull Map<Key, Integer> interior) {
        public @NotNull WorldPosition anchor() {
            return new WorldPosition(this.world, this.min);
        }

        public boolean contains(@NotNull WorldPosition position) {
            BlockPosition at = position.position();
            return position.world().equals(this.world) && at.x() >= this.min.x() && at.x() <= this.max.x()
                    && at.y() >= this.min.y() && at.y() <= this.max.y() && at.z() >= this.min.z() && at.z() <= this.max.z();
        }

        /** Blocks inside: the volume. */
        public int volume() {
            return (this.max.x() - this.min.x() - 1) * (this.max.y() - this.min.y() - 1) * (this.max.z() - this.min.z() - 1);
        }

        public int count(@NotNull Key block) {
            return this.interior.getOrDefault(block, 0);
        }
    }

    /** The formed structure {@code position} belongs to, or {@code null}. */
    public static @Nullable Structure containing(@NotNull WorldPosition position) {
        for (Structure structure : FORMED.values()) {
            if (structure.contains(position)) return structure;
        }
        return null;
    }

    static void form(@NotNull Structure structure) {
        // A structure overlapping another (rebuilt bigger or smaller) replaces it.
        FORMED.values().removeIf(other -> other.world().equals(structure.world()) && overlaps(other, structure));
        FORMED.put(structure.anchor(), structure);
    }

    static void unform(@NotNull Structure structure) {
        FORMED.remove(structure.anchor(), structure);
    }

    /** Forgets every structure (tests). */
    public static void clear() {
        FORMED.clear();
    }

    private static boolean overlaps(Structure a, Structure b) {
        return a.min().x() <= b.max().x() && b.min().x() <= a.max().x() && a.min().y() <= b.max().y() && b.min().y() <= a.max().y()
                && a.min().z() <= b.max().z() && b.min().z() <= a.max().z();
    }

    /**
     * Finds the structure of {@code type} that the frame block at {@code start} belongs to: the frame
     * blocks connected to it must make exactly the walls of a box of 3 to 18 blocks per side, with
     * casings on every edge, and an interior the type allows. Every block must be loaded and owned by
     * this thread. {@code null} if there is no valid structure.
     */
    public static @Nullable Structure detect(@NotNull MachineContext context, @NotNull WorldPosition start, @NotNull Type type) {
        Function<BlockPosition, Key> blockAt = context::blockAt;
        Key first = blockAt.apply(start.position());
        if (first == null || !type.faces().contains(first)) return null;
        // The frame blocks connected to the start, within the largest box.
        Set<BlockPosition> frame = new HashSet<>();
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>();
        queue.add(start.position());
        frame.add(start.position());
        int minX = start.position().x(), minY = start.position().y(), minZ = start.position().z();
        int maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPosition at = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPosition next = at.relative(direction);
                if (frame.contains(next)) continue;
                Key block = blockAt.apply(next);
                if (block == null || !type.faces().contains(block)) continue;
                minX = Math.min(minX, next.x()); maxX = Math.max(maxX, next.x());
                minY = Math.min(minY, next.y()); maxY = Math.max(maxY, next.y());
                minZ = Math.min(minZ, next.z()); maxZ = Math.max(maxZ, next.z());
                if (maxX - minX >= MAX_SIZE || maxY - minY >= MAX_SIZE || maxZ - minZ >= MAX_SIZE) return null;
                frame.add(next);
                queue.add(next);
            }
        }
        if (maxX - minX + 1 < MIN_SIZE || maxY - minY + 1 < MIN_SIZE || maxZ - minZ + 1 < MIN_SIZE) return null;
        Map<Key, Integer> interior = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPosition at = new BlockPosition(x, y, z);
                    int onBounds = (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0) + (z == minZ || z == maxZ ? 1 : 0);
                    if (onBounds == 0) {
                        Key block = blockAt.apply(at);
                        if (block == null || !type.interior().test(block)) return null;
                        interior.merge(block, 1, Integer::sum);
                    } else if (!frame.contains(at)) {
                        return null; // a hole in the walls
                    } else if (onBounds >= 2 && !type.casing().equals(blockAt.apply(at))) {
                        return null; // edges and corners are casings
                    }
                }
            }
        }
        return new Structure(type, start.world(), new BlockPosition(minX, minY, minZ), new BlockPosition(maxX, maxY, maxZ), Map.copyOf(interior));
    }
}
