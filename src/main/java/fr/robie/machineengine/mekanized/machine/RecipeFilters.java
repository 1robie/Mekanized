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
        ResourcePredicate used = (variant, tags) -> {
            RegistryAccess access = registries;
            if (access == null) return true; // before the freeze: nothing to check against yet
            return CACHE.computeIfAbsent(type.key(), key -> new ConcurrentHashMap<>()).computeIfAbsent(variant, candidate -> {
                for (Recipe recipe : access.registry(Registries.RECIPE)) {
                    if (!(recipe instanceof ProcessingRecipe processing) || !processing.type().equals(type)) continue;
                    for (ResourceMatcher input : processing.inputs()) {
                        if (input.matches(candidate, tags)) return true;
                    }
                }
                return false;
            });
        };
        return Items.anyItem().and(used);
    }
}
