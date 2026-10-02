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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mekanism's item slots that feed a tank or the energy buffer: an item put in {@code from} is turned
 * into the recipe's output in {@code to} (e.g. an osmium ingot into osmium for the compressor,
 * redstone into energy), using {@code chemical_conversion} / {@code energy_conversion} recipes.
 * Converts only when the whole output fits, then runs the wrapped behavior.
 */
public final class ConvertingBehavior implements MachineBehavior {

    /** One conversion slot. */
    public record Conversion(@NotNull String from, @NotNull String to, @NotNull RecipeType<ProcessingRecipe> recipes) {
    }

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
        }
        this.inner.tick(context);
    }

    private static boolean convertOne(MachineContext context, Conversion conversion) {
        ResourceHandler from = context.storage(conversion.from());
        ResourceHandler to = context.storage(conversion.to());
        for (StorageView view : from.views()) {
            if (view.isEmpty()) continue;
            for (ProcessingRecipe recipe : context.recipes().all(conversion.recipes())) {
                if (recipe.inputs().size() != 1 || recipe.outputs().isEmpty()) continue;
                ResourceMatcher input = recipe.inputs().getFirst();
                if (!input.matches(view.variant(), context.tags()) || view.amount() < input.amount()) continue;
                try (Transaction transaction = context.transactions().openOuter()) {
                    if (from.extract(view.variant(), input.amount(), transaction) != input.amount()) return false;
                    for (ResourceStack output : recipe.outputs()) {
                        if (to.insert(output.variant(), output.amount(), transaction) != output.amount()) return false;
                    }
                    transaction.commit();
                    return true;
                }
            }
        }
        return false;
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
