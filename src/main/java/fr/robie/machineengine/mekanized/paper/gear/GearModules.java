package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.paper.PaperItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.bukkit.persistence.PersistentDataType;
import static fr.robie.machineengine.mekanized.paper.gear.GearItems.MODULES;
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

    /** Modules -> the gear they go in. */
    private static final Map<String, String> MODULE_TARGETS = Map.ofEntries(
            Map.entry("electrolytic_breathing_unit", "mekasuit_helmet"), Map.entry("vision_enhancement_unit", "mekasuit_helmet"),
            Map.entry("nutritional_injection_unit", "mekasuit_helmet"), Map.entry("inhalation_purification_unit", "mekasuit_helmet"),
            Map.entry("jetpack_unit", "mekasuit_bodyarmor"), Map.entry("gravitational_modulating_unit", "mekasuit_bodyarmor"),
            Map.entry("elytra_unit", "mekasuit_bodyarmor"), Map.entry("locomotive_boosting_unit", "mekasuit_pants"),
            Map.entry("hydraulic_propulsion_unit", "mekasuit_boots"), Map.entry("excavation_escalation_unit", "meka_tool"),
            Map.entry("attack_amplification_unit", "meka_tool"), Map.entry("silk_touch_unit", "meka_tool"),
            Map.entry("fortune_unit", "meka_tool"), Map.entry("vein_mining_unit", "meka_tool"), Map.entry("teleportation_unit", "meka_tool"),
            Map.entry("radiation_shielding_unit", "mekasuit"), Map.entry("geiger_unit", "mekasuit_helmet"),
            Map.entry("dosimeter_unit", "mekasuit_helmet"), Map.entry("energy_unit", "mekasuit"),
            Map.entry("charge_distribution_unit", "mekasuit_bodyarmor"), Map.entry("gyroscopic_stabilization_unit", "mekasuit_bodyarmor"),
            Map.entry("solar_recharging_unit", "mekasuit_helmet"), Map.entry("geothermal_generator_unit", "mekasuit_pants"),
            Map.entry("motorized_servo_unit", "mekasuit_pants"), Map.entry("hydrostatic_repulsor_unit", "mekasuit_pants"),
            Map.entry("magnetic_attraction_unit", "mekasuit_boots"), Map.entry("frost_walker_unit", "mekasuit_boots"),
            Map.entry("soul_surfer_unit", "mekasuit_boots"), Map.entry("blasting_unit", "meka_tool"),
            Map.entry("farming_unit", "meka_tool"), Map.entry("shearing_unit", "meka_tool"));

    /** Puts the module into the gear it fits: the worn armour piece, or the Meka-Tool in the off hand. */
    static void install(Player player, ItemStack moduleStack, String module) {
        String target = MODULE_TARGETS.get(module);
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
            case "mekasuit" -> worn.stream().filter(piece -> id(piece) != null && id(piece).startsWith("mekasuit_")
                    && !modules(piece).contains(module)).findFirst().orElse(worn.get(0));
            default -> player.getInventory().getItemInOffHand();
        };
        String gearId = id(gear);
        if (target.equals("mekasuit") ? gearId == null || !gearId.startsWith("mekasuit_") : !target.equals(gearId)) {
            player.sendActionBar(Component.text("Wear the " + target.replace('_', ' ') + " (a Meka-Tool: in the off hand) to install it",
                    NamedTextColor.RED));
            return;
        }
        Set<String> modules = new LinkedHashSet<>(modules(gear));
        if (!modules.add(module)) {
            player.sendActionBar(Component.text("Already installed", NamedTextColor.RED));
            return;
        }
        gear.editMeta(meta -> {
            meta.getPersistentDataContainer().set(MODULES, PersistentDataType.STRING, String.join(",", modules));
            switch (module) {
                case "elytra_unit" -> setGlider(meta);
                case "silk_touch_unit" -> meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
                case "fortune_unit" -> meta.addEnchant(Enchantment.FORTUNE, 3, true);
                case "frost_walker_unit" -> meta.addEnchant(Enchantment.FROST_WALKER, 2, true);
                case "soul_surfer_unit" -> meta.addEnchant(Enchantment.SOUL_SPEED, 3, true);
                default -> {
                }
            }
            refresh(meta, MekanizedContent.key(gearId));
        });
        if (player.getGameMode() != GameMode.CREATIVE) moduleStack.setAmount(moduleStack.getAmount() - 1);
        player.sendActionBar(Component.text("Installed: " + module.replace('_', ' '), NamedTextColor.GREEN));
    }

    static boolean isStation(Block block) {
        LoadedMachine machine = PaperWorld.machine(block);
        return machine != null && machine.inspect().type().equals(MekanizedContent.key("modification_station"));
    }

    /** At a modification station: the gear's last module comes back out as an item. */
    static void uninstall(Player player, ItemStack gear, String id) {
        List<String> modules = new ArrayList<>(modules(gear));
        String module = modules.removeLast();
        gear.editMeta(meta -> {
            if (modules.isEmpty()) meta.getPersistentDataContainer().remove(MODULES);
            else meta.getPersistentDataContainer().set(MODULES, PersistentDataType.STRING, String.join(",", modules));
            switch (module) {
                case "elytra_unit" -> setGlider(meta, false);
                case "silk_touch_unit" -> meta.removeEnchant(Enchantment.SILK_TOUCH);
                case "fortune_unit" -> meta.removeEnchant(Enchantment.FORTUNE);
                case "frost_walker_unit" -> meta.removeEnchant(Enchantment.FROST_WALKER);
                case "soul_surfer_unit" -> meta.removeEnchant(Enchantment.SOUL_SPEED);
                default -> {
                }
            }
            refresh(meta, MekanizedContent.key(id));
        });
        // Energy above the capacity left (an energy unit removed) is lost.
        long capacity = capacity(gear);
        if (energy(gear) > capacity) useEnergy(gear, energy(gear) - capacity);
        ItemStack item = PaperItems.itemOf(MekanizedContent.key("module_" + module));
        if (item != null) PaperWorld.give(player, item);
        player.sendActionBar(Component.text("Removed: " + module.replace('_', ' '), NamedTextColor.YELLOW));
    }

    /** The elytra unit: the chestplate glides (Paper 1.21.2+). */
    private static void setGlider(ItemMeta meta) {
        setGlider(meta, true);
    }

    private static void setGlider(ItemMeta meta, boolean glider) {
        try {
            meta.getClass().getMethod("setGlider", boolean.class).invoke(meta, glider);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // an older server: no gliding
        }
    }
}
