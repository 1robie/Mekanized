package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.match.ResourceMatcher;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mekanism's item slots that feed a tank or the energy buffer: an item put in {@code from} is turned
 * into the recipe's output in {@code to} (e.g. an osmium ingot into osmium for the compressor,
 * redstone into energy), using {@code chemical_conversion} / {@code energy_conversion} recipes.
 * Bucket slots work the same way: the recipe's first input is the item, further inputs come from
 * {@code to} (a bucket filled from the tank), and items it gives back go to {@code remainder} (the
 * empty or filled bucket). Converts only when everything fits, then runs the wrapped behavior.
 */
public final class ConvertingBehavior implements MachineBehavior {

    /**
     * One conversion slot.
     *
     * @param remainder where item outputs go (e.g. buckets), or {@code null} to put everything in {@code to}
     */
    public record Conversion(@NotNull String from, @NotNull String to, @NotNull RecipeType<ProcessingRecipe> recipes,
                             @Nullable String remainder) {
        public Conversion(@NotNull String from, @NotNull String to, @NotNull RecipeType<ProcessingRecipe> recipes) {
            this(from, to, recipes, null);
        }
    }

    /** Joules an energy item gives a machine per tick. */
    static final long DISCHARGE_PER_TICK = 10_000;

    private final MachineBehavior inner;
    private final List<Conversion> conversions;

    private ConvertingBehavior(MachineBehavior inner, List<Conversion> conversions) {
        this.inner = inner;
        this.conversions = List.copyOf(conversions);
    }

    public static @NotNull MachineBehavior.Factory of(@NotNull MachineBehavior.Factory inner, @NotNull Conversion... conversions) {
        return type -> new ConvertingBehavior(inner.create(type), List.of(conversions));
    }

    @Override
    public void tick(@NotNull MachineContext context) {
        for (Conversion conversion : this.conversions) {
            while (convertOne(context, conversion)) {
                // as many items as fit, in one tick
            }
            // An energy item (Energy Tablet) in the energy slot powers the machine.
            if (conversion.to().equals("energy")) EnergyItems.transfer(context, conversion.from(), "energy", DISCHARGE_PER_TICK, true);
        }
        this.inner.tick(context);
    }

    private static boolean convertOne(MachineContext context, Conversion conversion) {
        ResourceHandler from = context.storage(conversion.from());
        ResourceHandler to = context.storage(conversion.to());
        ResourceHandler remainder = conversion.remainder() == null ? to : context.storage(conversion.remainder());
        for (StorageView view : from.views()) {
            if (view.isEmpty()) continue;
            for (ProcessingRecipe recipe : context.recipes().all(conversion.recipes())) {
                if (recipe.inputs().isEmpty() || recipe.outputs().isEmpty()) continue;
                ResourceMatcher input = recipe.inputs().getFirst();
                if (!input.matches(view.variant(), context.tags()) || view.amount() < input.amount()) continue;
                try (Transaction transaction = context.transactions().openOuter()) {
                    if (from.extract(view.variant(), input.amount(), transaction) != input.amount()) return false;
                    for (ResourceMatcher extra : recipe.inputs().subList(1, recipe.inputs().size())) {
                        if (!extractMatching(context, to, extra, transaction)) return false;
                    }
                    for (ResourceStack output : recipe.outputs()) {
                        ResourceHandler target = output.variant().type().category().equals(Items.CATEGORY) ? remainder : to;
                        if (target.insert(output.variant(), output.amount(), transaction) != output.amount()) return false;
                    }
                    transaction.commit();
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean extractMatching(MachineContext context, ResourceHandler storage, ResourceMatcher matcher, Transaction transaction) {
        long missing = matcher.amount();
        for (StorageView view : storage.views()) {
            if (missing == 0) break;
            if (view.isEmpty() || !matcher.matches(view.variant(), context.tags())) continue;
            missing -= storage.extract(view.variant(), missing, transaction);
        }
        return missing == 0;
    }

    @Override
    public void onWake(@NotNull MachineContext context, @NotNull WakeReason reason) {
        this.inner.onWake(context, reason);
    }

    @Override
    public void onLoad(@NotNull MachineContext context) {
        this.inner.onLoad(context);
    }

    @Override
    public void onUnload(@NotNull MachineContext context) {
        this.inner.onUnload(context);
    }

    @Override
    public void catchUp(@NotNull MachineContext context, long ticks) {
        this.inner.catchUp(context, ticks);
    }

    @Override
    public void onRemove(@NotNull MachineContext context) {
        this.inner.onRemove(context);
    }
}
