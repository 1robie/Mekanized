package fr.robie.machineengine.mekanized.machine;

import net.kyori.adventure.key.Key;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which piece a module goes into, and how an old module list is read. Plain Java: the platform
 * ({@code GearModules}) looks at the worn pieces and edits the item; this decides. Which items a module fits
 * is in its item config ({@code fits}).
 * What modules cost and do is in their item config ({@code mekanized:module}, D32).
 */
public final class GearRules {
    private GearRules() {
    }

    /** {@code capacity} times {@code factor}, saturating (a module's capacity factor). */
    public static long scaled(long capacity, long factor) {
        if (capacity <= 0 || factor <= 1) return capacity;
        return capacity > Long.MAX_VALUE / factor ? Long.MAX_VALUE : capacity * factor;
    }

    /**
     * Which of the {@code candidates} (worn armour, then the off hand; null: nothing there) a module goes
     * into: the first one it {@code fits} that does not have it yet; if every fitting one has it, the first
     * fitting one (to say "already installed"); -1 if none fits.
     */
    public static int pick(@NotNull List<@Nullable Key> candidates, @NotNull IntFunction<Set<String>> modulesOf,
                           @NotNull Set<Key> fits, @NotNull String module) {
        int first = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Key id = candidates.get(i);
            if (id == null || !fits.contains(id)) continue;
            if (!modulesOf.apply(i).contains(module)) return i;
            if (first < 0) first = i;
        }
        return first;
    }

    /** The modules of a piece from its stored text (comma separated, in install order). */
    public static @NotNull Set<String> parseModules(@Nullable String text) {
        return text == null || text.isEmpty() ? Set.of() : new LinkedHashSet<>(List.of(text.split(",")));
    }

    /** The stored text of a module list; null for none (nothing is stored). */
    public static @Nullable String formatModules(@NotNull Iterable<String> modules) {
        String text = String.join(",", modules);
        return text.isEmpty() ? null : text;
    }
}
