package fr.robie.machineengine.mekanized.machine;

import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Module rules (D40), in plain Java: which piece a module goes into, levels and the on/off switch, how the
 * stored list reads and writes, and what a module's level does to numbers. The platform ({@code GearModules})
 * looks at the worn pieces and edits the item; this decides. What a module costs and does, which items it
 * fits and its {@code max-level} are in its item config ({@code mekanized:module}, D32).
 */
public final class GearRules {
    private GearRules() {
    }

    /**
     * One installed module: its level (1 or more) and whether it is switched on. Stored as {@code name},
     * {@code name:level} or {@code name:level:off}; a bare name (gear from before levels) is level 1, on.
     */
    public record Installed(@NotNull String name, int level, boolean enabled) {
        public Installed {
            if (level < 1) throw new IllegalArgumentException("level must be at least 1");
        }

        /** The stored entry, or {@code null} for an empty or broken one. */
        public static @Nullable Installed parse(@Nullable String entry) {
            if (entry == null || entry.isBlank()) return null;
            String[] parts = entry.trim().split(":");
            if (parts[0].isEmpty()) return null;
            int level = 1;
            if (parts.length > 1) {
                try {
                    level = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    level = 1;
                }
            }
            return new Installed(parts[0], level, parts.length < 3 || !parts[2].equals("off"));
        }

        public @NotNull String format() {
            return this.name + ":" + this.level + (this.enabled ? "" : ":off");
        }

        Installed withLevel(int level) {
            return new Installed(this.name, level, this.enabled);
        }
    }

    /** The modules of a piece from its stored entries, in install order; a name stored twice counts once. */
    public static @NotNull List<Installed> parse(@NotNull List<String> entries) {
        Map<String, Installed> modules = new LinkedHashMap<>();
        for (String entry : entries) {
            Installed module = Installed.parse(entry);
            if (module != null) modules.putIfAbsent(module.name(), module);
        }
        return List.copyOf(modules.values());
    }

    public static @NotNull List<String> format(@NotNull List<Installed> modules) {
        return modules.stream().map(Installed::format).toList();
    }

    /** The level of {@code name} if it is installed and on, else 0. */
    public static int level(@NotNull List<Installed> modules, @NotNull String name) {
        for (Installed module : modules) {
            if (module.name().equals(name)) return module.enabled() ? module.level() : 0;
        }
        return 0;
    }

    /** The level of {@code name} whether on or off, 0 if it is not installed. */
    public static int installedLevel(@NotNull List<Installed> modules, @NotNull String name) {
        for (Installed module : modules) {
            if (module.name().equals(name)) return module.level();
        }
        return 0;
    }

    /** The names of the modules that are on. */
    public static @NotNull Set<String> enabled(@NotNull List<Installed> modules) {
        Set<String> names = new LinkedHashSet<>();
        for (Installed module : modules) if (module.enabled()) names.add(module.name());
        return names;
    }

    /**
     * One more level of {@code name} (a new module goes last, on).
     *
     * @return the new list, or {@code null} if it is already at {@code maxLevel}
     */
    public static @Nullable List<Installed> install(@NotNull List<Installed> modules, @NotNull String name, int maxLevel) {
        List<Installed> result = new ArrayList<>(modules);
        for (int i = 0; i < result.size(); i++) {
            Installed module = result.get(i);
            if (!module.name().equals(name)) continue;
            if (module.level() >= Math.max(1, maxLevel)) return null;
            result.set(i, module.withLevel(module.level() + 1));
            return List.copyOf(result);
        }
        result.add(new Installed(name, 1, true));
        return List.copyOf(result);
    }

    /**
     * Takes up to {@code levels} levels of {@code name} out; the module is gone at level 0.
     *
     * @return the new list (unchanged if it is not installed)
     */
    public static @NotNull List<Installed> remove(@NotNull List<Installed> modules, @NotNull String name, int levels) {
        List<Installed> result = new ArrayList<>();
        for (Installed module : modules) {
            if (!module.name().equals(name)) {
                result.add(module);
            } else if (module.level() > levels) {
                result.add(module.withLevel(module.level() - Math.max(0, levels)));
            }
        }
        return List.copyOf(result);
    }

    /** Switches {@code name} on or off; unchanged if it is not installed. */
    public static @NotNull List<Installed> toggle(@NotNull List<Installed> modules, @NotNull String name) {
        return modules.stream().map(module -> module.name().equals(name)
                ? new Installed(module.name(), module.level(), !module.enabled()) : module).toList();
    }

    /**
     * Which of the {@code candidates} (worn armour, then the off hand; null: nothing there) a module goes
     * into: the first one it {@code fits} where it is below its {@code maxLevel}; if every fitting one is at
     * its max, the first fitting one (to say "already at its max"); -1 if none fits.
     */
    public static int pick(@NotNull List<@Nullable Key> candidates, @NotNull ToIntFunction<Integer> levelOf,
                           @NotNull Set<Key> fits, int maxLevel) {
        int first = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Key id = candidates.get(i);
            if (id == null || !fits.contains(id)) continue;
            if (levelOf.applyAsInt(i) < Math.max(1, maxLevel)) return i;
            if (first < 0) first = i;
        }
        return first;
    }

    /** {@code capacity} times {@code factor} once per level, saturating (the energy unit). */
    public static long scaled(long capacity, long factor, int level) {
        long result = capacity;
        for (int i = 0; i < level && factor > 1 && result > 0; i++) {
            result = result > Long.MAX_VALUE / factor ? Long.MAX_VALUE : result * factor;
        }
        return result;
    }

    /** From {@code base} (level 0) to {@code full} (level {@code maxLevel}) in even steps. */
    public static double byLevel(double base, double full, int level, int maxLevel) {
        if (level <= 0) return base;
        int max = Math.max(1, maxLevel);
        return base + (full - base) * Math.min(level, max) / max;
    }

    /** Energy over {@code capacity} that a change would lose (an energy unit taken out). */
    public static long energyLost(long energy, long capacity) {
        return Math.max(0, energy - capacity);
    }

    /**
     * What a module enchantment did to a piece: the level it {@code applied}, and the gear's {@code own}
     * level from before (0 if none), which comes back when the module goes. Stored as
     * {@code enchantment|applied|own}.
     */
    public record Enchanted(int applied, int own) {
        public static @NotNull Map<String, Enchanted> parse(@NotNull List<String> entries) {
            Map<String, Enchanted> result = new LinkedHashMap<>();
            for (String entry : entries) {
                String[] parts = entry.split("\\|");
                if (parts.length != 3) continue;
                try {
                    result.put(parts[0], new Enchanted(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                } catch (NumberFormatException ignored) {
                    // a broken entry is forgotten
                }
            }
            return result;
        }

        public static @NotNull List<String> format(@NotNull Map<String, Enchanted> records) {
            List<String> entries = new ArrayList<>();
            records.forEach((enchantment, record) -> entries.add(enchantment + "|" + record.applied() + "|" + record.own()));
            return entries;
        }
    }

    /**
     * Enchantments after a module change.
     *
     * @param levels   the levels to set on the gear (0: remove)
     * @param recorded what to store for next time
     */
    public record EnchantmentSync(@NotNull Map<String, Integer> levels, @NotNull Map<String, Enchanted> recorded) {
    }

    /**
     * Brings the gear's enchantments in line with its modules, without touching the gear's own: an
     * enchantment the gear had before a module raised it comes back when the module goes, and one changed
     * since (an anvil) is the gear's own from then on.
     *
     * @param current  the gear's levels now, for every enchantment in {@code recorded} or {@code wanted}
     * @param recorded what the last sync stored
     * @param wanted   the levels the modules give now (enchantment-level times module level)
     */
    public static @NotNull EnchantmentSync syncEnchantments(@NotNull Map<String, Integer> current, @NotNull Map<String, Enchanted> recorded,
                                                            @NotNull Map<String, Integer> wanted) {
        Set<String> all = new LinkedHashSet<>(recorded.keySet());
        all.addAll(wanted.keySet());
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Enchanted> records = new LinkedHashMap<>();
        for (String enchantment : all) {
            int now = current.getOrDefault(enchantment, 0);
            Enchanted before = recorded.get(enchantment);
            int own = before != null && now == before.applied() ? before.own() : now;
            int give = wanted.getOrDefault(enchantment, 0);
            int level = Math.max(own, give);
            levels.put(enchantment, level);
            if (give > 0) records.put(enchantment, new Enchanted(level, own));
        }
        return new EnchantmentSync(levels, records);
    }
}
