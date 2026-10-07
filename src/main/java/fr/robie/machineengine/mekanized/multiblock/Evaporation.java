package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.multiblock.MultiblockMember;
import fr.robie.machineengine.api.multiblock.MultiblockStructure;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.match.ResourceMatcher;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.mekanized.MekanizedSettings;
import org.jetbrains.annotations.NotNull;

/**
 * The Thermal Evaporation Plant's work, done by its anchor every {@link #EVERY_TICKS}: the fluid in
 * its {@code input} tank turns into the {@code output} fluid (water to brine, brine to liquid lithium),
 * by the {@code evaporating} recipes. Mekanism heats the tower with the biome, heaters and solar
 * panels; here, simpler: {@code evaporationPerLevel} mB of input (settings, 10 by default) per tick
 * per level inside the tower in daylight, half at
 * night, half again in rain.
 */
final class Evaporation implements MultiblockMember.AnchorTask {
    static final long EVERY_TICKS = 20;

    private final RecipeType<ProcessingRecipe> recipes;

    Evaporation(RecipeType<ProcessingRecipe> recipes) {
        this.recipes = recipes;
    }

    @Override
    public void run(@NotNull MachineContext anchor, @NotNull MultiblockStructure structure) {
        ResourceHandler input = anchor.storage("input");
        ResourceHandler output = anchor.storage("output");
        StorageView held = input.views().getFirst();
        if (held.isEmpty()) {
            anchor.setActive(false);
            return;
        }
        long budget = budget(anchor.environment(), structure);
        for (ProcessingRecipe recipe : anchor.recipes().all(this.recipes)) {
            if (recipe.inputs().size() != 1 || recipe.outputs().size() != 1) continue;
            ResourceMatcher in = recipe.inputs().getFirst();
            if (!in.matches(held.variant(), anchor.tags())) continue;
            ResourceStack out = recipe.outputs().getFirst();
            StorageView made = output.views().getFirst();
            long room = made.isEmpty() || made.variant().equals(out.variant()) ? made.capacity() - made.amount() : 0;
            long operations = Math.min(Math.min(held.amount(), budget) / in.amount(), room / out.amount());
            if (operations <= 0) break;
            try (Transaction transaction = anchor.transactions().openOuter()) {
                if (input.extract(held.variant(), operations * in.amount(), transaction) != operations * in.amount()) break;
                if (output.insert(out.variant(), operations * out.amount(), transaction) != operations * out.amount()) break;
                transaction.commit();
            }
            anchor.setActive(true);
            return;
        }
        anchor.setActive(false);
    }

    /** Input mB evaporated per {@link #EVERY_TICKS}: per level inside the tower, by the sun and the rain. */
    static long budget(MachineEnvironment environment, MultiblockStructure structure) {
        long levels = Math.max(1, structure.height() - 1); // the open top counts, the floor does not
        double heat = environment.daytime() ? 1.0 : 0.5;
        if (environment.raining()) heat /= 2;
        return (long) (MekanizedSettings.evaporationPerLevel * EVERY_TICKS * levels * heat);
    }
}
