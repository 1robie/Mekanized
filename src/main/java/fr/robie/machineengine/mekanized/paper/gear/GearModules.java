package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.GearRules;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.platform.Nms;
import fr.robie.machineengine.api.paper.PaperItems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import fr.robie.machineengine.mekanized.paper.ItemData;
import org.bukkit.inventory.PlayerInventory;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import static fr.robie.machineengine.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.installed;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.refresh;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.setInstalled;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useEnergy;

/**
 * Installing modules into gear, a level at a time (D40), switching them on and off, and taking them out;
 * after each change the gear's glider and enchantments follow its modules ({@link #apply}).
 */
public final class GearModules {
    private GearModules() {
    }

    /**
     * Puts one level of the module into the first worn piece (then the item in the off hand) that it
     * {@code fits} and that has it below its max level.
     */
    static void install(Player player, ItemStack moduleStack, ModuleHolderBehavior.Module module, Set<Key> fits) {
        if (fits.isEmpty()) {
            player.sendActionBar(Component.text("This module does nothing in Mekanized", NamedTextColor.RED));
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> candidates = java.util.Arrays.asList(inventory.getHelmet(), inventory.getChestplate(), inventory.getLeggings(),
                inventory.getBoots(), inventory.getItemInOffHand());
        List<Key> ids = candidates.stream().map(stack -> stack == null || stack.isEmpty() ? null : PaperItems.idOf(stack)).toList();
        int at = GearRules.pick(ids, i -> GearRules.installedLevel(installed(candidates.get(i)), module.module()), fits, module.maxLevel());
        if (at < 0) {
            String names = fits.stream().map(key -> key.value().replace('_', ' ')).sorted().reduce((x, y) -> x + ", " + y).orElse("");
            player.sendActionBar(Component.text("Wear or hold in the off hand: " + names, NamedTextColor.RED));
            return;
        }
        ItemStack gear = candidates.get(at);
        Integer level = install(gear, module);
        if (level == null) {
            player.sendActionBar(Component.text("Already at its max level (" + module.maxLevel() + ")", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) moduleStack.setAmount(moduleStack.getAmount() - 1);
        player.sendActionBar(Component.text("Installed: " + module.module().replace('_', ' ')
                + (module.maxLevel() > 1 ? " " + level + "/" + module.maxLevel() : ""), NamedTextColor.GREEN));
    }

    /**
     * One more level of {@code module} in {@code gear}.
     *
     * @return the module's level now, or {@code null} if it was at its max
     */
    public static @Nullable Integer install(ItemStack gear, ModuleHolderBehavior.Module module) {
        List<GearRules.Installed> modules = GearRules.install(installed(gear), module.module(), module.maxLevel());
        if (modules == null) return null;
        setInstalled(gear, modules);
        apply(gear);
        return GearRules.installedLevel(modules, module.module());
    }

    /**
     * Takes up to {@code levels} levels of {@code name} out of {@code gear}; energy over the capacity left
     * (an energy unit removed) is lost.
     *
     * @return how many levels came out
     */
    public static int remove(ItemStack gear, String name, int levels) {
        List<GearRules.Installed> before = installed(gear);
        int had = GearRules.installedLevel(before, name);
        if (had == 0 || levels <= 0) return 0;
        List<GearRules.Installed> after = GearRules.remove(before, name, levels);
        setInstalled(gear, after);
        apply(gear);
        long capacity = capacity(gear);
        if (energy(gear) > capacity) useEnergy(gear, GearRules.energyLost(energy(gear), capacity));
        return had - GearRules.installedLevel(after, name);
    }

    /**
     * Switches {@code name} on or off.
     *
     * @return whether it changed (not installed, or a module that cannot be switched off: no)
     */
    public static boolean toggle(ItemStack gear, String name) {
        List<GearRules.Installed> before = installed(gear);
        ModuleHolderBehavior.Module options = GearBehaviors.module(name);
        if (GearRules.installedLevel(before, name) == 0 || options != null && !options.toggleable()) return false;
        setInstalled(gear, GearRules.toggle(before, name));
        apply(gear);
        return true;
    }

    /** What the gear's modules give it, after a change: gliding, and enchantments (only what modules added). */
    public static void apply(ItemStack gear) {
        List<GearRules.Installed> modules = installed(gear);
        boolean glider = false;
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (GearRules.Installed module : modules) {
            ModuleHolderBehavior.Module options = GearBehaviors.module(module.name());
            if (options == null || !module.enabled()) continue;
            glider |= options.glider();
            Enchantment enchantment = options.enchantment();
            if (enchantment != null) wanted.merge(enchantment.getKey().toString(), options.enchantmentLevel() * module.level(), Math::max);
        }
        Map<String, GearRules.Enchanted> recorded = GearRules.Enchanted.parse(ItemData.getStrings(gear, ItemData.Field.MODULE_ENCHANTMENTS));
        Map<String, Integer> current = new LinkedHashMap<>();
        java.util.Set<String> all = new java.util.LinkedHashSet<>(recorded.keySet());
        all.addAll(wanted.keySet());
        for (String key : all) {
            Enchantment enchantment = enchantment(key);
            current.put(key, enchantment == null ? 0 : gear.getEnchantmentLevel(enchantment));
        }
        GearRules.EnchantmentSync sync = GearRules.syncEnchantments(current, recorded, wanted);
        boolean gliding = glider;
        gear.editMeta(meta -> {
            Nms.get().setGlider(meta, gliding); // false on an older server: no gliding
            sync.levels().forEach((key, level) -> {
                Enchantment enchantment = enchantment(key);
                if (enchantment == null) return;
                if (level > 0) meta.addEnchant(enchantment, level, true);
                else meta.removeEnchant(enchantment);
            });
        });
        ItemData.setStrings(gear, ItemData.Field.MODULE_ENCHANTMENTS, GearRules.Enchanted.format(sync.recorded()));
        refresh(gear);
    }

    private static @Nullable Enchantment enchantment(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        return parsed == null ? null : Registry.ENCHANTMENT.get(parsed);
    }

    static boolean isStation(Block block) {
        MachineInstance machine = PaperWorld.machine(block);
        return machine != null && machine.inspect().type().equals(MekanizedContent.key("modification_station"));
    }

    /** At a modification station: one level of the gear's last module comes back out as an item. */
    static void uninstall(Player player, ItemStack gear) {
        List<GearRules.Installed> modules = installed(gear);
        if (modules.isEmpty()) return;
        String module = modules.getLast().name();
        if (remove(gear, module, 1) == 0) return;
        ItemStack item = PaperItems.itemOf(MekanizedContent.key("module_" + module));
        if (item != null) PaperWorld.give(player, item);
        player.sendActionBar(Component.text("Removed: " + module.replace('_', ' '), NamedTextColor.YELLOW));
    }
}
