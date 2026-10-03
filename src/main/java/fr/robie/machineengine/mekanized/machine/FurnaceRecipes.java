package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.registry.ReloadContext;
import fr.robie.machineengine.api.resource.match.ResourceMatcher;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The energized smelter runs every furnace recipe of the server (vanilla, datapacks, and CraftEngine's,
 * which include Mekanized's dust smelting), like Mekanism's: same ingredient, output and duration,
 * 50 J per tick. Re-read on every recipe reload.
 */
final class FurnaceRecipes {
    private static final long JOULES_PER_TICK = 50;

    private FurnaceRecipes() {
    }

    static void provide(ReloadContext<fr.robie.machineengine.api.recipe.Recipe> reload) {
        if (Bukkit.getServer() == null) return; // headless tests
        int registered = 0;
        java.util.Map<String, Integer> skipped = new java.util.TreeMap<>();
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (!(recipes.next() instanceof FurnaceRecipe furnace)) continue;
            // One odd recipe must not cost the others.
            try {
                RecipeChoice choice = furnace.getInputChoice();
                ResourcePredicate input = predicate(choice);
                ItemStack result = furnace.getResult();
                if (input == null || result.getType().isAir()) {
                    skipped.merge(input == null ? "ingredient " + (choice == null ? "none" : choice.getClass().getSimpleName()) : "no result", 1, Integer::sum);
                    continue;
                }
                Key id = Key.key(MekanizedContent.NAMESPACE, "smelting/" + furnace.getKey().getNamespace() + "/" + furnace.getKey().getKey());
                reload.register(ProcessingRecipe.builder(id, MekanizedMachines.SMELTING)
                        .input(ResourceMatcher.of(input, 1))
                        .output(PaperItems.toResource(result))
                        .costPerTick(MekanizedContent.ENERGY.stack(JOULES_PER_TICK))
                        .duration(Math.max(1, furnace.getCookingTime()))
                        .build());
                registered++;
            } catch (RuntimeException | LinkageError e) {
                skipped.merge(e.getClass().getSimpleName() + ": " + e.getMessage(), 1, Integer::sum);
            }
        }
        var plugin = Bukkit.getPluginManager().getPlugin("Mekanized");
        java.util.logging.Logger logger = plugin != null ? plugin.getLogger() : Bukkit.getLogger();
        logger.info("Energized smelter: " + registered + " furnace recipes" + (skipped.isEmpty() ? "" : ", skipped " + skipped));
    }

    /**
     * Any other choice (26.x vanilla recipes use item-type choices, which 1.20.6 does not have): asks
     * the choice itself, once per item id.
     */
    private static ResourcePredicate tested(RecipeChoice choice) {
        java.util.Map<Key, Boolean> answers = new java.util.concurrent.ConcurrentHashMap<>();
        return (variant, tags) -> {
            fr.robie.machineengine.api.item.ItemData data = variant.type() == Items.ITEM ? variant.components().get(Items.DATA) : null;
            if (data == null) return false;
            return answers.computeIfAbsent(data.item(), id -> {
                ItemStack stack = PaperItems.toItemStack(variant, 1);
                return stack != null && choice.test(stack);
            });
        };
    }

    private static ResourcePredicate predicate(RecipeChoice choice) {
        List<ResourcePredicate> options = new ArrayList<>();
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            for (Material material : materials.getChoices()) options.add(Items.ofItem(material.getKey()));
        } else if (choice instanceof RecipeChoice.ExactChoice exact) {
            for (ItemStack stack : exact.getChoices()) options.add(Items.ofItem(PaperItems.idOf(stack)));
        }
        if (options.isEmpty()) return choice == null ? null : tested(choice);
        ResourcePredicate any = options.getFirst();
        for (int i = 1; i < options.size(); i++) any = any.or(options.get(i));
        return any;
    }
}
