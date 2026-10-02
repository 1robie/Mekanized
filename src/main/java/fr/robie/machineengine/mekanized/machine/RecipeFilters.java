package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.event.EngineEvents;
import fr.robie.machineengine.api.event.EventPhase;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.Recipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.registry.RegistryAccess;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.resource.match.ResourceMatcher;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.resource.tag.ResourceTags;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slot filters from recipes, like Mekanism's slots: a machine's input slot takes only items one of its
 * recipes uses, its chemical slot only items with a chemical conversion, its energy slot only items
 * with an energy conversion. Players and hoppers can no longer put redstone in an input, and a
 * shift-click finds the right slot. Cached per item, cleared on every recipe or tag reload.
 */
final class RecipeFilters {
    private static volatile RegistryAccess registries;
    private static final Map<Key, Map<ResourceVariant, Boolean>> CACHE = new ConcurrentHashMap<>();

    private RecipeFilters() {
    }

    /** Keeps the registries at hand once frozen, and forgets cached answers on every reload. */
    static void attach(RegistrationContext context) {
        context.events().subscribe(context.addon(), EngineEvents.REGISTRIES_FROZEN, EventPhase.POST, frozen -> {
            registries = frozen.registries();
            CACHE.clear();
        });
        context.events().subscribe(context.addon(), EngineEvents.REGISTRY_RELOADED, EventPhase.POST, reloaded -> CACHE.clear());
    }

    /** Items used as an input by some recipe of {@code type}. */
    static @NotNull ResourcePredicate inputOf(@NotNull RecipeType<ProcessingRecipe> type) {
        return Items.anyItem().and(usedBy(type));
    }

    /** Resources (items, chemicals, fluids) used as an input by some recipe of {@code type}. */
    static @NotNull ResourcePredicate usedBy(@NotNull RecipeType<ProcessingRecipe> type) {
        return cached(type.key(), type, (recipe, variant, tags) -> {
            for (ResourceMatcher input : recipe.inputs()) {
                if (input.matches(variant, tags)) return true;
            }
            return false;
        });
    }

    /** Resources some recipe of {@code type} pays per tick (e.g. the purification chamber's oxygen). */
    static @NotNull ResourcePredicate costOf(@NotNull RecipeType<ProcessingRecipe> type) {
        return cached(Key.key(type.key().namespace(), type.key().value() + "/cost"), type,
                (recipe, variant, tags) -> recipe.costPerTick().stream().anyMatch(cost -> cost.variant().equals(variant)));
    }

    private interface RecipeTest {
        boolean test(ProcessingRecipe recipe, ResourceVariant variant, ResourceTags tags);
    }

    private static ResourcePredicate cached(Key cacheKey, RecipeType<ProcessingRecipe> type, RecipeTest test) {
        return (variant, tags) -> {
            RegistryAccess access = registries;
            if (access == null) return true; // before the freeze: nothing to check against yet
            return CACHE.computeIfAbsent(cacheKey, key -> new ConcurrentHashMap<>()).computeIfAbsent(variant, candidate -> {
                for (Recipe recipe : access.registry(Registries.RECIPE)) {
                    if (recipe instanceof ProcessingRecipe processing && processing.type().equals(type)
                            && test.test(processing, candidate, tags)) return true;
                }
                return false;
            });
        };
    }
}
