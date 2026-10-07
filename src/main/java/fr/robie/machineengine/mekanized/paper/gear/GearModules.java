package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.GearRules;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.platform.Nms;
import fr.robie.machineengine.api.paper.PaperItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import fr.robie.machineengine.mekanized.paper.ItemData;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.capacity;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.energy;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.id;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.modules;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.refresh;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.useEnergy;

/** Installing modules into worn gear or a Meka-Tool, and taking them out at a modification station. */
public final class GearModules {
    private GearModules() {
    }

    /** Puts the module into the gear it fits: the worn armour piece, or the Meka-Tool in the off hand. */
    static void install(Player player, ItemStack moduleStack, String module) {
        String target = GearRules.target(module);
        if (target == null) {
            player.sendActionBar(Component.text("This module does nothing in Mekanized", NamedTextColor.RED));
            return;
        }
        // "mekasuit": any worn piece that does not have it yet.
        List<ItemStack> worn = List.of(player.getInventory().getHelmet() == null ? new ItemStack(Material.AIR) : player.getInventory().getHelmet(),
                player.getInventory().getChestplate() == null ? new ItemStack(Material.AIR) : player.getInventory().getChestplate(),
                player.getInventory().getLeggings() == null ? new ItemStack(Material.AIR) : player.getInventory().getLeggings(),
                player.getInventory().getBoots() == null ? new ItemStack(Material.AIR) : player.getInventory().getBoots());
        ItemStack gear = switch (target) {
            case "mekasuit_helmet" -> worn.get(0);
            case "mekasuit_bodyarmor" -> worn.get(1);
            case "mekasuit_pants" -> worn.get(2);
            case "mekasuit_boots" -> worn.get(3);
            case GearRules.ANY_MEKASUIT -> worn.get(GearRules.pickMekasuitPiece(worn.size(), i -> id(worn.get(i)),
                    i -> modules(worn.get(i)), module));
            default -> player.getInventory().getItemInOffHand();
        };
        String gearId = id(gear);
        if (!GearRules.fits(target, gearId)) {
            player.sendActionBar(Component.text("Wear the " + target.replace('_', ' ') + " (a Meka-Tool: in the off hand) to install it",
                    NamedTextColor.RED));
            return;
        }
        Set<String> modules = new LinkedHashSet<>(modules(gear));
        if (!modules.add(module)) {
            player.sendActionBar(Component.text("Already installed", NamedTextColor.RED));
            return;
        }
        ItemData.setStrings(gear, ItemData.Field.MODULES, List.copyOf(modules));
        gear.editMeta(meta -> {
            // What the module gives the gear: its item config (glider, enchantment).
            ModuleHolderBehavior.Module options = GearBehaviors.module(module);
            if (options != null && options.glider()) setGlider(meta);
            Enchantment enchantment = options == null ? null : options.enchantment();
            if (enchantment != null) meta.addEnchant(enchantment, options.enchantmentLevel(), true);
        });
        refresh(gear);
        if (player.getGameMode() != GameMode.CREATIVE) moduleStack.setAmount(moduleStack.getAmount() - 1);
        player.sendActionBar(Component.text("Installed: " + module.replace('_', ' '), NamedTextColor.GREEN));
    }

    static boolean isStation(Block block) {
        MachineInstance machine = PaperWorld.machine(block);
        return machine != null && machine.inspect().type().equals(MekanizedContent.key("modification_station"));
    }

    /** At a modification station: the gear's last module comes back out as an item. */
    static void uninstall(Player player, ItemStack gear, String id) {
        List<String> modules = new ArrayList<>(modules(gear));
        String module = modules.removeLast();
        ItemData.setStrings(gear, ItemData.Field.MODULES, modules);
        gear.editMeta(meta -> {
            ModuleHolderBehavior.Module options = GearBehaviors.module(module);
            if (options != null && options.glider()) setGlider(meta, false);
            Enchantment enchantment = options == null ? null : options.enchantment();
            if (enchantment != null) meta.removeEnchant(enchantment);
        });
        // Energy above the capacity left (an energy unit removed) is lost.
        long capacity = capacity(gear);
        if (energy(gear) > capacity) useEnergy(gear, energy(gear) - capacity);
        refresh(gear);
        ItemStack item = PaperItems.itemOf(MekanizedContent.key("module_" + module));
        if (item != null) PaperWorld.give(player, item);
        player.sendActionBar(Component.text("Removed: " + module.replace('_', ' '), NamedTextColor.YELLOW));
    }

    /** The elytra unit: the chestplate glides (Paper 1.21.2+). */
    private static void setGlider(ItemMeta meta) {
        setGlider(meta, true);
    }

    private static void setGlider(ItemMeta meta, boolean glider) {
        Nms.get().setGlider(meta, glider); // false on an older server: no gliding
    }
}
