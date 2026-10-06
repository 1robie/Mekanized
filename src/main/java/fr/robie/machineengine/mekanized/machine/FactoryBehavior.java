package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.recipe.ChanceOutput;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourceMatcher;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.mekanized.MekanizedContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Mekanism's factories (E7): one machine running several operations at once, one per lane. Lane 1
 * works on the {@code input} storage (so a machine upgraded to a factory keeps its input), lane
 * {@code i} on {@code input_<i>}; outputs go to the {@code output} storage. Items spread over empty
 * lanes (Mekanism's auto-sort). As in Mekanism, a lane only counts progress (paying the per-tick costs) and takes its
 * inputs when the operation completes, so nothing is ever in flight: changing the item in a lane
 * restarts it, and an output that does not fit holds the lane at 100 %.
 *
 * <p>Second inputs (the combiner's extra item, the infuser's chemical) come from the
 * {@code secondary} storages; per-tick costs are paid from the {@code costs} storages in order.</p>
 */
public final class FactoryBehavior implements MachineBehavior {
    /** Progress of each lane, in ticks. */
    public static final MachineDataKey<List<Long>> PROGRESS = MachineDataKey.of("lanes",
            VersionedCodec.builder(MekanizedContent.key("lanes"), Codecs.list(Codecs.LONG)).build(), List.of());

    /** Percent done of lane {@code i} (1-based), for menus ({@code %zmenu_machineengine_data_lane_<i>%}). */
    public static MachineDataKey<Long> lanePercent(int lane) {
        return LANE_PERCENTS.get(lane - 1);
    }

    /** One key per lane, shared: a machine finds its data by the key its type declared. */
    private static final List<MachineDataKey<Long>> LANE_PERCENTS = IntStream.rangeClosed(1, 9)
            .mapToObj(lane -> MachineDataKey.of("lane_" + lane, VersionedCodec.builder(MekanizedContent.key("lane_" + lane), Codecs.LONG).build(), 0L))
            .toList();

    private final RecipeType<ProcessingRecipe> recipes;
    private final int lanes;
    private final List<String> secondary;
    private final List<String> costs;
    private final List<MachineDataKey<Long>> percents = new ArrayList<>();
    /** The recipe each lane input runs, or {@link Optional#empty()} if none; cleared on recipe reload. */
    private final Map<ResourceVariant, Optional<LaneRecipe>> recipeOf = new HashMap<>();

    /**
     * A recipe for a lane's item: {@code lane} is the input the item fills (not always the first:
     * Mekanism lists the infuse type before the item), {@code others} come from the secondary storages.
     */
    private record LaneRecipe(ProcessingRecipe recipe, ResourceMatcher lane, List<ResourceMatcher> others) {
    }

    private FactoryBehavior(RecipeType<ProcessingRecipe> recipes, int lanes, List<String> secondary, List<String> costs) {
        this.recipes = recipes;
        this.lanes = lanes;
        this.secondary = List.copyOf(secondary);
        this.costs = List.copyOf(costs);
        for (int i = 1; i <= lanes; i++) this.percents.add(lanePercent(i));
    }

    /** The input storage of lane {@code lane} (0-based). */
    public static @NotNull String laneStorage(int lane) {
        return lane == 0 ? "input" : "input_" + (lane + 1);
    }

    public static @NotNull MachineBehavior.Factory of(@NotNull RecipeType<ProcessingRecipe> recipes, int lanes,
                                                      @NotNull List<String> secondary, @NotNull List<String> costs) {
        return type -> new FactoryBehavior(recipes, lanes, secondary, costs);
    }

    @Override
    public void tick(@NotNull MachineContext context) {
        spread(context);
        List<Long> progress = new ArrayList<>(context.get(PROGRESS));
        while (progress.size() < this.lanes) progress.add(0L);
        boolean working = false;
        for (int lane = 0; lane < this.lanes; lane++) {
            StorageView view = context.storage(laneStorage(lane)).views().getFirst();
            LaneRecipe found = view.isEmpty() ? null : recipe(context, view);
            if (found == null || view.amount() < found.lane().amount() || !secondaryAvailable(context, found)) {
                setProgress(context, progress, lane, 0, 1);
                continue;
            }
            ProcessingRecipe recipe = found.recipe();
            long duration = Math.max(1, context.modified(StandardModifiers.PROCESS_DURATION, recipe.duration()));
            long done = Math.min(progress.get(lane), duration);
            if (done < duration) {
                if (!pay(context, recipe)) {
                    setProgress(context, progress, lane, done, duration);
                    continue; // waits for energy (or its chemical)
                }
                done++;
                working = true;
            }
            if (done >= duration && complete(context, laneStorage(lane), view.variant(), found)) {
                done = 0;
                working = true;
            }
            setProgress(context, progress, lane, done, duration);
        }
        context.set(PROGRESS, List.copyOf(progress));
        context.setActive(working);
        if (!working) context.waitForInput(); // inserting, taking outputs or adding energy wakes it
    }

    private void setProgress(MachineContext context, List<Long> progress, int lane, long done, long duration) {
        progress.set(lane, done);
        long percent = Math.min(100, done * 100 / Math.max(1, duration));
        if (context.get(this.percents.get(lane)) != percent) context.set(this.percents.get(lane), percent);
    }

    private @Nullable LaneRecipe recipe(MachineContext context, StorageView view) {
        return this.recipeOf.computeIfAbsent(view.variant(), variant -> {
            for (ProcessingRecipe recipe : context.recipes().all(this.recipes)) {
                for (ResourceMatcher input : recipe.inputs()) {
                    if (!input.matches(variant, context.tags())) continue;
                    List<ResourceMatcher> others = new ArrayList<>(recipe.inputs());
                    others.remove(input);
                    return Optional.of(new LaneRecipe(recipe, input, List.copyOf(others)));
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** Moves half of the fullest lane's items into each empty lane, so every lane works. */
    private void spread(MachineContext context) {
        for (int empty = 0; empty < this.lanes; empty++) {
            ResourceHandler target = context.storage(laneStorage(empty));
            if (!target.views().getFirst().isEmpty()) continue;
            ResourceHandler fullest = null;
            StorageView most = null;
            for (int lane = 0; lane < this.lanes; lane++) {
                StorageView view = context.storage(laneStorage(lane)).views().getFirst();
                if (view.amount() >= 2 && (most == null || view.amount() > most.amount())) {
                    most = view;
                    fullest = context.storage(laneStorage(lane));
                }
            }
            if (fullest == null) return;
            try (Transaction transaction = context.transactions().openOuter()) {
                long moved = fullest.extract(most.variant(), most.amount() / 2, transaction);
                if (target.insert(most.variant(), moved, transaction) == moved) transaction.commit();
            }
        }
    }

    /** Whether the second inputs (combiner extra, infuse chemical) are there, for one operation. */
    private boolean secondaryAvailable(MachineContext context, LaneRecipe recipe) {
        for (ResourceMatcher extra : recipe.others()) {
            long found = 0;
            for (String id : this.secondary) {
                for (StorageView view : context.storage(id).views()) {
                    if (!view.isEmpty() && extra.matches(view.variant(), context.tags())) found += view.amount();
                }
            }
            if (found < extra.amount()) return false;
        }
        return true;
    }

    /** Pays one tick of the recipe's costs (energy, a chemical), each from the cost storages in order. */
    private boolean pay(MachineContext context, ProcessingRecipe recipe) {
        try (Transaction transaction = context.transactions().openOuter()) {
            for (ResourceStack cost : recipe.costPerTick()) {
                long missing = context.modifiedCost(cost.type(), cost.amount());
                for (String id : this.costs) {
                    if (missing <= 0) break;
                    missing -= context.storage(id).extract(cost.variant(), missing, transaction);
                }
                if (missing > 0) return false;
            }
            transaction.commit();
            return true;
        }
    }

    /** Takes the inputs and delivers the outputs, all or nothing. */
    private boolean complete(MachineContext context, String lane, ResourceVariant laneItem, LaneRecipe found) {
        ProcessingRecipe recipe = found.recipe();
        try (Transaction transaction = context.transactions().openOuter()) {
            long first = found.lane().amount();
            if (context.storage(lane).extract(laneItem, first, transaction) != first) return false;
            for (ResourceMatcher extra : found.others()) {
                long missing = extra.amount();
                for (String id : this.secondary) {
                    ResourceHandler storage = context.storage(id);
                    for (StorageView view : storage.views()) {
                        if (missing == 0) break;
                        if (!view.isEmpty() && extra.matches(view.variant(), context.tags())) {
                            missing -= storage.extract(view.variant(), missing, transaction);
                        }
                    }
                }
                if (missing > 0) return false;
            }
            List<ResourceStack> outputs = new ArrayList<>(recipe.outputs());
            for (ChanceOutput chance : recipe.chanceOutputs()) {
                if (context.random().nextDouble() < chance.chance()) outputs.add(chance.stack());
            }
            ResourceHandler output = context.storage("output");
            for (ResourceStack stack : outputs) {
                if (output.insert(stack.variant(), stack.amount(), transaction) != stack.amount()) return false;
            }
            transaction.commit();
            return true;
        }
    }

    @Override
    public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
        if (reason.equals(WakeReason.RECIPES_RELOADED)) this.recipeOf.clear();
    }
}
