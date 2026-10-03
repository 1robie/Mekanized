package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.TransactionContext;
import fr.robie.machineengine.api.transfer.Transfers;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * A multiblock's frame block (casing, valve, port, glass). Each finds its structure when it loads
 * (or is placed), and unforms it when removed. The anchor (lowest corner) sizes its storages for the
 * structure and checks it again every few seconds (blocks placed inside, cells added...). A member
 * that passes through ({@code storages} not empty) shows the anchor's storages on all its sides.
 */
public final class MemberBehavior implements MachineBehavior {
    /** An induction port's mode: {@code input} (default) or {@code output}, pushing to its neighbors. */
    public static final MachineDataKey<String> PORT_MODE = MachineDataKey.of("port_mode",
            VersionedCodec.builder(MekanizedContent.key("port_mode"), Codecs.STRING).build(), "input");
    private static final long RECHECK_TICKS = 100;

    /** Work an anchor does while its structure is formed (e.g. evaporating), every {@code everyTicks}. */
    public interface AnchorTask {
        void run(@NotNull MachineContext anchor, @NotNull Multiblocks.Structure structure);
    }

    private final List<Multiblocks.Type> types;
    private final List<String> storages;
    private final List<String> insertInto;
    private final List<String> extractFrom;
    private final boolean exclusive;
    private final @Nullable ToLongFunction<Multiblocks.Structure> outputRate;
    private final @Nullable AnchorTask task;
    private final long taskTicks;

    private MemberBehavior(List<Multiblocks.Type> types, List<String> insertInto, List<String> extractFrom, boolean exclusive,
                           @Nullable ToLongFunction<Multiblocks.Structure> outputRate, @Nullable AnchorTask task, long taskTicks) {
        this.types = List.copyOf(types);
        this.insertInto = List.copyOf(insertInto);
        this.extractFrom = List.copyOf(extractFrom);
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(insertInto);
        all.addAll(extractFrom);
        this.storages = List.copyOf(all);
        this.exclusive = exclusive;
        this.outputRate = outputRate;
        this.task = task;
        this.taskTicks = taskTicks;
    }

    /** A casing or glass: finds the structure; anchors it when it is the lowest corner. */
    public static @NotNull MachineBehavior.Factory frame(@NotNull List<Multiblocks.Type> types) {
        return type -> new MemberBehavior(types, List.of(), List.of(), false, null, null, RECHECK_TICKS);
    }

    /** A casing whose anchor also runs {@code task} every {@code everyTicks} while the structure is formed. */
    public static @NotNull MachineBehavior.Factory frame(@NotNull List<Multiblocks.Type> types, @NotNull AnchorTask task, long everyTicks) {
        return type -> new MemberBehavior(types, List.of(), List.of(), false, null, task, everyTicks);
    }

    /**
     * A valve: passes through to the anchor's {@code storages}. With {@code exclusive}, the structure
     * holds one of them at a time (a dynamic tank holds a fluid or a chemical).
     */
    public static @NotNull MachineBehavior.Factory valve(@NotNull List<Multiblocks.Type> types, @NotNull List<String> storages, boolean exclusive) {
        return type -> new MemberBehavior(types, storages, storages, exclusive, null, null, RECHECK_TICKS);
    }

    /** A valve filling the anchor's {@code insertInto} storages and emptying its {@code extractFrom} ones. */
    public static @NotNull MachineBehavior.Factory valve(@NotNull List<Multiblocks.Type> types, @NotNull List<String> insertInto,
                                                         @NotNull List<String> extractFrom) {
        return type -> new MemberBehavior(types, insertInto, extractFrom, false, null, null, RECHECK_TICKS);
    }

    /** An induction port: passes energy in, or (in output mode) pushes it out at {@code outputRate} per tick. */
    public static @NotNull MachineBehavior.Factory port(@NotNull List<Multiblocks.Type> types, @NotNull String storage,
                                                        @NotNull ToLongFunction<Multiblocks.Structure> outputRate) {
        return type -> new MemberBehavior(types, List.of(storage), List.of(storage), false, outputRate, null, RECHECK_TICKS);
    }

    @Override
    public void onLoad(@NotNull MachineContext context) {
        if (!this.storages.isEmpty()) context.exposeHandler(new PassThrough(context));
        context.wakeIn(1); // find the structure once the neighbors' chunks are there
    }

    @Override
    public void tick(@NotNull MachineContext context) {
        WorldPosition here = context.machine().position();
        Multiblocks.Structure structure = Multiblocks.containing(here);
        if (structure == null) structure = detect(context, here);
        if (structure != null && structure.anchor().equals(here)) {
            // The anchor: still valid? Then size the storages for it.
            Multiblocks.Structure again = detect(context, here);
            if (again == null) {
                Multiblocks.unform(structure);
                structure = null;
            } else {
                structure = again;
                Multiblocks.form(again);
                again.type().capacities().apply(again).forEach(context::setStorageCapacity);
                if (this.task != null) this.task.run(context, again);
            }
            context.wakeIn(this.taskTicks);
        }
        boolean output = this.outputRate != null && "output".equals(context.get(PORT_MODE));
        context.setActive(structure != null && (this.outputRate == null || output));
        if (output && structure != null) {
            push(context, structure);
            context.wakeIn(1);
        } else if (structure == null || !structure.anchor().equals(here)) {
            context.sleep();
        }
    }

    private @Nullable Multiblocks.Structure detect(MachineContext context, WorldPosition here) {
        Multiblocks.Structure found = null;
        for (Multiblocks.Type type : this.types) {
            found = Multiblocks.detect(context, here, type);
            if (found != null) break;
        }
        if (found == null) return null;
        Multiblocks.form(found);
        if (!found.anchor().equals(here)) context.wakeMachine(found.anchor()); // it sizes its storages
        return found;
    }

    /** Output port: the structure's energy into every neighbor, up to the providers' rate. */
    private void push(MachineContext context, Multiblocks.Structure structure) {
        ResourceHandler source = context.storageAt(structure.anchor(), this.storages.getFirst()).orElse(null);
        if (source == null) return;
        long budget = this.outputRate.applyAsLong(structure);
        for (RelativeSide side : RelativeSide.values()) {
            if (budget <= 0) return;
            ResourceHandler target = context.neighbor(side, Capabilities.RESOURCE_HANDLER).orElse(null);
            if (target == null || Multiblocks.containing(neighborPosition(context, side)) == structure) continue;
            try (Transaction transaction = context.transactions().openOuter()) {
                budget -= Transfers.move(source, target, variant -> true, budget, transaction).moved();
                transaction.commit();
            }
        }
    }

    private static WorldPosition neighborPosition(MachineContext context, RelativeSide side) {
        return context.machine().position().relative(side.toAbsolute(context.machine().facing()));
    }

    @Override
    public void onRemove(@NotNull MachineContext context) {
        Multiblocks.Structure structure = Multiblocks.containing(context.machine().position());
        if (structure == null) return;
        Multiblocks.unform(structure);
        context.wakeMachine(structure.anchor()); // keeps its contents, unformed
    }

    @Override
    public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
    }

    /** What a valve or port shows: the anchor's storages while the structure is formed, nothing otherwise. */
    private final class PassThrough implements ResourceHandler {
        private final MachineContext context;

        private PassThrough(MachineContext context) {
            this.context = context;
        }

        private List<ResourceHandler> targets() {
            return targets(MemberBehavior.this.storages);
        }

        private List<ResourceHandler> targets(List<String> storages) {
            Multiblocks.Structure structure = Multiblocks.containing(this.context.machine().position());
            if (structure == null) return List.of();
            List<ResourceHandler> targets = new ArrayList<>();
            for (String storage : storages) {
                this.context.storageAt(structure.anchor(), storage).ifPresent(targets::add);
            }
            return targets;
        }

        private boolean output() {
            return MemberBehavior.this.outputRate != null && "output".equals(this.context.get(PORT_MODE));
        }

        @Override
        public long insert(@NotNull ResourceVariant variant, long maxAmount, @NotNull TransactionContext transaction) {
            if (output()) return 0;
            List<ResourceHandler> targets = targets(MemberBehavior.this.insertInto);
            for (ResourceHandler target : targets) {
                if (MemberBehavior.this.exclusive && othersHold(targets, target)) continue;
                long inserted = target.insert(variant, maxAmount, transaction);
                if (inserted > 0) return inserted;
            }
            return 0;
        }

        /** A dynamic tank holds a fluid or a chemical: not both. */
        private boolean othersHold(List<ResourceHandler> targets, ResourceHandler target) {
            for (ResourceHandler other : targets) {
                if (other != target && other.views().stream().anyMatch(view -> !view.isEmpty())) return true;
            }
            return false;
        }

        @Override
        public long extract(@NotNull ResourceVariant variant, long maxAmount, @NotNull TransactionContext transaction) {
            if (MemberBehavior.this.outputRate != null && !output()) return 0; // an input port gives nothing
            long extracted = 0;
            for (ResourceHandler target : targets(MemberBehavior.this.extractFrom)) {
                extracted += target.extract(variant, maxAmount - extracted, transaction);
                if (extracted >= maxAmount) break;
            }
            return extracted;
        }

        @Override
        public @NotNull List<StorageView> views() {
            List<StorageView> views = new ArrayList<>();
            for (ResourceHandler target : targets()) views.addAll(target.views());
            return views;
        }
    }
}
