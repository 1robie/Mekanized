package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.resource.Fluids;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.tag.ResourceTags;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.api.world.BlockPosition;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.api.multiblock.Multiblocks;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Behaviors of Mekanism's utility blocks: bins, plenisher, assemblicator, lasers, alarm. */
public final class UtilityBehaviors {
    private static final Set<Key> AIR = Set.of(Key.key("minecraft", "air"), Key.key("minecraft", "cave_air"), Key.key("minecraft", "void_air"));

    private UtilityBehaviors() {
    }

    /** Nothing to do on its own (bins, personal storage, chargepads): a creative bin stays full of its item. */
    public static @NotNull MachineBehavior.Factory storage(@NotNull String storage, boolean creative) {
        return type -> context -> {
            if (creative) {
                ResourceHandler items = context.storage(storage);
                StorageView held = items.views().getFirst();
                if (!held.isEmpty() && held.amount() < held.capacity()) {
                    try (Transaction transaction = context.transactions().openOuter()) {
                        items.insert(held.variant(), held.capacity() - held.amount(), transaction);
                        transaction.commit();
                    }
                }
            }
            context.sleep();
        };
    }

    // -- Fluidic Plenisher --------------------------------------------------------------------

    /**
     * The pump the other way round: every {@code ticks} (shorter with speed upgrades) it places a
     * water or lava source block (a bucket, 1000 mB by default) in the nearest air or flowing fluid below it, up to
     * {@code range} blocks away, for {@code energy} Joules.
     */
    public static @NotNull MachineBehavior.Factory plenisher(@NotNull String tank, long energy, long ticks, int range) {
        return type -> context -> {
            long delay = Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, ticks));
            long cost = context.modifiedCost(MekanizedContent.ENERGY, energy);
            ResourceHandler fluid = context.storage(tank);
            StorageView held = fluid.views().getFirst();
            long bucket = Fluids.bucket();
            Key world = held.isEmpty() || held.amount() < bucket ? null : worldFluid(held.variant());
            if (world == null) {
                context.setActive(false);
                context.waitForInput();
                return;
            }
            BlockPosition below = context.machine().position().position().relative(Direction.DOWN);
            BlockPosition target = findSpace(context, below, world, range);
            try (Transaction transaction = context.transactions().openOuter()) {
                if (target == null
                        || !MachineParts.pay(context.storage("energy"), cost, transaction)
                        || fluid.extract(held.variant(), bucket, transaction) != bucket
                        || !context.world().placeFluid(target, world)) {
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

    /** Water or lava as the world's {@code minecraft:water} or {@code minecraft:lava}; null for fluids with no block. */
    static @Nullable Key worldFluid(ResourceVariant variant) {
        return Fluids.worldFluid(variant.type());
    }

    private static @Nullable BlockPosition findSpace(MachineContext context, BlockPosition start, Key fluid, int range) {
        ArrayDeque<BlockPosition> queue = new ArrayDeque<>(List.of(start));
        Set<BlockPosition> seen = new HashSet<>(List.of(start));
        int budget = 4096;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPosition at = queue.poll();
            Key block = context.world().blockAt(at);
            WorldFluid there = context.world().fluidAt(at);
            boolean air = there == null && block != null && AIR.contains(block);
            boolean flowing = there != null && there.fluid().equals(fluid) && !there.source();
            boolean source = there != null && there.fluid().equals(fluid) && there.source();
            if (air || flowing) return at;
            if (!source) continue; // a solid block: not through it
            for (Direction direction : Direction.values()) {
                if (direction == Direction.UP) continue;
                BlockPosition next = at.relative(direction);
                if (Math.abs(next.x() - start.x()) > range || Math.abs(next.z() - start.z()) > range || start.y() - next.y() > range) continue;
                if (seen.add(next)) queue.add(next);
            }
        }
        return null;
    }

    // -- Formulaic Assemblicator -------------------------------------------------------------

    /** What a 3x3 crafting grid makes (the server's crafting recipes), given by the platform. */
    public interface Crafting {
        /**
         * @param world the machine's world (Folia: the recipe is looked up there, on its thread)
         * @param grid  9 cells, row by row, {@code null} for empty ones
         * @return the result, or null
         */
        @Nullable ResourceStack craft(@NotNull Key world, @NotNull List<@Nullable ResourceVariant> grid);
    }

    private static volatile @Nullable Crafting crafting;

    public static void setCrafting(@Nullable Crafting value) {
        crafting = value;
    }

    /**
     * Crafts its {@code pattern} (9 slots, one item each, a recipe laid out like a crafting table) from
     * the items in {@code input}, every {@code ticks}, for {@code energy} Joules per craft.
     */
    public static @NotNull MachineBehavior.Factory assemblicator(long energy, long ticks) {
        return type -> context -> {
            Crafting current = crafting;
            List<StorageView> pattern = context.storage("pattern").views();
            List<ResourceVariant> grid = new ArrayList<>();
            for (StorageView cell : pattern) grid.add(cell.isEmpty() ? null : cell.variant());
            ResourceStack result = current == null || grid.stream().allMatch(Objects::isNull) ? null : current.craft(context.machine().position().world(), grid);
            if (result == null) {
                context.setActive(false);
                context.waitForInput();
                return;
            }
            Map<ResourceVariant, Long> needed = new HashMap<>();
            for (ResourceVariant cell : grid) if (cell != null) needed.merge(cell, 1L, Long::sum);
            long cost = context.modifiedCost(MekanizedContent.ENERGY, energy);
            boolean crafted;
            try (Transaction transaction = context.transactions().openOuter()) {
                crafted = MachineParts.pay(context.storage("energy"), cost, transaction);
                for (Map.Entry<ResourceVariant, Long> entry : needed.entrySet()) {
                    crafted &= context.storage("input").extract(entry.getKey(), entry.getValue(), transaction) == entry.getValue();
                }
                crafted &= context.storage("output").insert(result.variant(), result.amount(), transaction) == result.amount();
                if (crafted) transaction.commit();
            }
            context.setActive(crafted);
            if (crafted) context.wakeIn(Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, ticks)));
            else context.waitForInput();
        };
    }

    // -- Lasers ------------------------------------------------------------------------------

    /**
     * Mekanism's redstone control ({@link UtilityMachines#REDSTONE}): {@code ignored} works whatever the
     * signal, {@code high} only while powered, {@code low} only while not powered.
     */
    static boolean redstoneAllows(MachineContext context) {
        return switch (context.get(UtilityMachines.REDSTONE)) {
            case "high" -> context.world().redstonePower() > 0;
            case "low" -> context.world().redstonePower() == 0;
            default -> true;
        };
    }

    /** Laser receivers: the beam's energy goes into their {@code energy} storage. */
    static final Set<String> LASER_RECEIVERS = Set.of("laser_amplifier", "laser_tractor_beam");
    private static final int BEAM_RANGE = 64;
    /** Joules of beam energy per point of block hardness to break it. */
    private static final long ENERGY_PER_HARDNESS = 100_000;
    private static final int BEAM_COLOR = 0xFF2020;

    /**
     * A laser, amplifier or tractor beam firing out of its front, up to 64 blocks: into a laser
     * receiver (its energy), or into a block, which breaks once it took its hardness x 100 kJ. A
     * laser fires {@code perTick} Joules from its energy; an amplifier or tractor beam fires what it
     * stores, up to {@code perTick}. Each follows its redstone control. A tractor beam keeps the drops
     * of the blocks its beam breaks.
     */
    public static @NotNull MachineBehavior.Factory laser(long perTick, boolean fromStorage, boolean collects) {
        return type -> new MachineBehavior() {
            private BlockPosition target;
            private long heat;
            private long lastParticles;

            @Override
            public void tick(@NotNull MachineContext context) {
                if (!redstoneAllows(context)) {
                    idle(context, 20);
                    return;
                }
                long fired;
                try (Transaction transaction = context.transactions().openOuter()) {
                    fired = context.storage("energy").extract(MekanizedContent.ENERGY.variant(), perTick, transaction);
                    if (!fromStorage && fired < perTick) {
                        idle(context, 20);
                        return; // a laser fires at full power or not at all
                    }
                    if (fired <= 0) {
                        idle(context, 20);
                        return;
                    }
                    transaction.commit();
                }
                context.setActive(true);
                BlockPosition end = fire(context, fired);
                if (end != null && context.gameTime() - this.lastParticles >= 10) {
                    context.world().particleLine(end, BEAM_COLOR);
                    this.lastParticles = context.gameTime();
                }
                context.wakeIn(1);
            }

            private void idle(MachineContext context, long ticks) {
                context.setActive(false);
                this.target = null;
                this.heat = 0;
                context.wakeIn(ticks);
            }

            /** @return where the beam ends */
            private @Nullable BlockPosition fire(MachineContext context, long joules) {
                WorldPosition origin = context.machine().position();
                Direction facing = context.machine().facing();
                BlockPosition at = origin.position();
                for (int step = 1; step <= BEAM_RANGE; step++) {
                    at = at.relative(facing);
                    Key block = context.world().blockAt(at);
                    if (block == null) return at; // not loaded: the beam stops
                    if (block.equals(MekanizedContent.key("laser_focus_matrix"))) {
                        // A fusion reactor's window: the beam heats its plasma (ignition).
                        var structure = Multiblocks.containing(new WorldPosition(origin.world(), at));
                        if (structure != null) {
                            context.machines().storage(structure.anchor(), "ignition").ifPresent(ignition -> {
                                try (Transaction transaction = context.transactions().openOuter()) {
                                    ignition.insert(MekanizedContent.ENERGY.variant(), joules, transaction);
                                    transaction.commit();
                                }
                            });
                        }
                        return at;
                    }
                    if (LASER_RECEIVERS.contains(block.value()) && block.namespace().equals(MekanizedContent.NAMESPACE)) {
                        var receiver = context.machines().storage(new WorldPosition(origin.world(), at), "energy");
                        if (receiver.isPresent()) {
                            try (Transaction transaction = context.transactions().openOuter()) {
                                receiver.get().insert(MekanizedContent.ENERGY.variant(), joules, transaction);
                                transaction.commit();
                            }
                            context.machines().wake(new WorldPosition(origin.world(), at));
                        }
                        return at;
                    }
                    if (AIR.contains(block) || context.world().fluidAt(at) != null) continue;
                    if (UtilityMachines.isPersonal(block)) return at; // nobody's beam opens someone's storage
                    float hardness = context.world().blockHardness(at);
                    if (hardness < 0) return at; // unbreakable
                    if (!at.equals(this.target)) {
                        this.target = at;
                        this.heat = 0;
                    }
                    this.heat += joules;
                    if (this.heat >= (long) Math.max(1, hardness * ENERGY_PER_HARDNESS) && context.world().breakBlock(at)) {
                        this.target = null;
                        this.heat = 0;
                        if (collects) collect(context, at);
                    }
                    return at;
                }
                return at;
            }

            private void collect(MachineContext context, BlockPosition at) {
                ResourceHandler items = context.storage("items");
                for (ResourceStack stack : context.world().collectItems(at)) {
                    try (Transaction transaction = context.transactions().openOuter()) {
                        items.insert(stack.variant(), stack.amount(), transaction); // what does not fit is lost, like Mekanism's
                        transaction.commit();
                    }
                }
            }
        };
    }

    // -- Oredictionificator -----------------------------------------------------------------

    /** Makes the platform's item {@code id} (a plain one), or null if there is none. */
    private static volatile @Nullable Function<Key, ResourceVariant> itemFactory;

    public static void setItemFactory(@Nullable Function<Key, ResourceVariant> value) {
        itemFactory = value;
    }

    /** Mekanism's materials, as their {@code c:} tags name them. */
    private static final List<String> MATERIALS = List.of("osmium", "tin", "lead", "uranium", "bronze", "steel", "fluorite",
            "refined_obsidian", "refined_glowstone", "raw_osmium", "raw_tin", "raw_lead", "raw_uranium", "iron", "gold", "copper",
            "diamond", "emerald", "lapis", "quartz", "coal", "charcoal", "netherite", "obsidian", "sulfur", "lithium", "salt", "wood");

    /**
     * The Mekanized item that stands for {@code item}'s {@code c:} tag (an osmium ingot from another
     * plugin becomes {@code mekanized:ingot_osmium}); null if {@code item} is already it or in none.
     */
    static @Nullable Key standardItem(ResourceTags tags, Key item) {
        for (String material : MATERIALS) {
            String[][] families = {{"ingots", "ingot_" + material}, {"dusts", "dust_" + material}, {"nuggets", "nugget_" + material},
                    {"storage_blocks", "block_" + material}, {"raw_materials", "raw_" + material}, {"ores", material + "_ore"}};
            for (String[] family : families) {
                Key tag = Key.key("c", family[0] + "/" + material);
                if (!tags.containsItem(tag, item)) continue;
                Key target = MekanizedContent.key(family[1]);
                return target.equals(item) ? null : target;
            }
        }
        return null;
    }

    /** Turns the items of its {@code input} into their standard Mekanized item (Mekanism's default filters). */
    public static @NotNull MachineBehavior.Factory oredictionificator() {
        return type -> new MachineBehavior() {
            private final Map<ResourceVariant, Optional<ResourceVariant>> known = new HashMap<>();

            @Override
            public void tick(@NotNull MachineContext context) {
                var factory = itemFactory;
                ResourceHandler input = context.storage("input");
                for (StorageView view : input.views()) {
                    if (view.isEmpty() || factory == null) continue;
                    ResourceVariant from = view.variant();
                    var data = from.components().get(Items.DATA);
                    if (data == null) continue;
                    ResourceVariant to = this.known.computeIfAbsent(from, variant -> {
                        Key target = standardItem(context.tags(), data.item());
                        return Optional.ofNullable(target == null ? null : factory.apply(target));
                    }).orElse(null);
                    try (Transaction transaction = context.transactions().openOuter()) {
                        long moved = context.storage("output").insert(to == null ? from : to, view.amount(), transaction);
                        if (moved <= 0 || input.extract(from, moved, transaction) != moved) continue;
                        transaction.commit();
                    }
                }
                context.waitForInput();
            }
        };
    }

    // -- Industrial Alarm --------------------------------------------------------------------

    /** Sounds every two seconds while its block is powered by redstone (and shows it). */
    public static @NotNull MachineBehavior.Factory alarm(@NotNull Key sound) {
        return type -> context -> {
            boolean powered = context.world().redstonePower() > 0;
            context.setActive(powered);
            if (powered) context.world().playSound(sound, 2.0f, 1.0f);
            context.wakeIn(40);
        };
    }
}
