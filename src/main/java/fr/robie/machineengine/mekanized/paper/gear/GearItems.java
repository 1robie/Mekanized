package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.mekanized.machine.GearRules;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import fr.robie.machineengine.mekanized.paper.ItemLongs;
import fr.robie.machineengine.mekanized.paper.Lore;
import fr.robie.machineengine.mekanized.paper.PaperEnergyItems;
import fr.robie.machineengine.api.paper.PaperItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * What a piece of Mekanized gear carries, read and written through its item data: energy, chemical and
 * modules, and the lore and tool speeds that show them.
 */
public final class GearItems {
    private GearItems() {
    }

    public static final NamespacedKey CHEMICAL = new NamespacedKey(MekanizedContent.NAMESPACE, "gear_chemical");
    public static final NamespacedKey MODULES = new NamespacedKey(MekanizedContent.NAMESPACE, "modules");

    /** Gear holding a chemical: item -> the chemical (the capacity in mB is {@link GearSettings#chemicalCapacity}). */
    static final Map<String, String> CHEMICALS = Map.of(
            "jetpack", "hydrogen", "jetpack_armored", "hydrogen", "flamethrower", "hydrogen", "scuba_tank", "oxygen",
            "mekasuit_bodyarmor", "hydrogen", "canteen", "fluid/nutritional_paste");

    // -- item data ------------------------------------------------------------------------------

    public static @Nullable String id(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Key key = PaperItems.idOf(stack);
        return key.namespace().equals(MekanizedContent.NAMESPACE) ? key.value() : null;
    }

    static long energy(ItemStack stack) {
        return ItemLongs.get(stack, PaperEnergyItems.ENERGY);
    }

    /** Takes {@code joules} from the gear if it has them all. */
    public static boolean useEnergy(ItemStack stack, long joules) {
        long energy = energy(stack);
        if (energy < joules) return false;
        String id = id(stack);
        stack.editMeta(meta -> {
            ItemLongs.set(meta, PaperEnergyItems.ENERGY, energy - joules);
            refresh(meta, MekanizedContent.key(id));
        });
        return true;
    }

    static long chemical(ItemStack stack) {
        return ItemLongs.get(stack, CHEMICAL);
    }

    static boolean useChemical(ItemStack stack, long amount) {
        long held = chemical(stack);
        if (held < amount) return false;
        String id = id(stack);
        stack.editMeta(meta -> {
            ItemLongs.set(meta, CHEMICAL, held - amount);
            refresh(meta, MekanizedContent.key(id));
        });
        return true;
    }

    public static Set<String> modules(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) return Set.of();
        String list = stack.getItemMeta().getPersistentDataContainer().get(MODULES, PersistentDataType.STRING);
        return GearRules.parseModules(list);
    }

    /** The item's energy capacity: its base one, times {@link GearSettings#energyUnitFactor} with an energy unit. */
    public static long capacity(Key item, @Nullable String modules) {
        return EnergyItems.capacity(item, modules);
    }

    public static long capacity(ItemStack stack) {
        String id = id(stack);
        return id == null ? 0 : capacity(MekanizedContent.key(id), stack.getItemMeta().getPersistentDataContainer().get(MODULES, PersistentDataType.STRING));
    }

    /** Adds up to {@code joules} (what fits); returns what was added. */
    static long addEnergy(ItemStack stack, long joules) {
        long energy = energy(stack);
        long added = Math.max(0, Math.min(joules, capacity(stack) - energy));
        if (added <= 0) return 0;
        String id = id(stack);
        stack.editMeta(meta -> {
            ItemLongs.set(meta, PaperEnergyItems.ENERGY, energy + added);
            refresh(meta, MekanizedContent.key(id));
        });
        return added;
    }

    /** Rewrites the lore (energy, chemical, modules) and the tool speeds of a gear item. */
    public static void refresh(ItemMeta meta, Key item) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<Component> lore = new ArrayList<>();
        long capacity = capacity(item, pdc.get(MODULES, PersistentDataType.STRING));
        long energy = ItemLongs.get(pdc, PaperEnergyItems.ENERGY);
        if (capacity > 0) lore.add(Lore.line(String.format(Locale.ROOT, "Energy: %,d / %,d J", energy, capacity)));
        String chemical = CHEMICALS.get(item.value());
        if (chemical != null) {
            String name = MekanizedContent.displayName(chemical);
            lore.add(Lore.line(String.format(Locale.ROOT, "%s: %,d / %,d mB", capitalize(name), ItemLongs.get(pdc, CHEMICAL),
                    GearSettings.chemicalCapacity(item.value()))));
        }
        String modules = pdc.get(MODULES, PersistentDataType.STRING);
        if (modules != null && !modules.isEmpty()) {
            for (String module : modules.split(",")) lore.add(Lore.line("+ " + capitalize(module.replace('_', ' '))).color(NamedTextColor.AQUA));
        }
        meta.lore(lore);
        // Disassembler and Meka-Tool: fast on what pickaxes, axes, shovels and hoes mine while charged.
        if (item.value().equals("atomic_disassembler") || item.value().equals("meka_tool")) {
            boolean charged = energy >= GearSettings.toolEnergyPerBlock;
            float speed = !charged ? 1f : modules != null && modules.contains("excavation_escalation_unit") ? 32f : 16f;
            ToolComponent tool = meta.getTool();
            tool.setRules(new ArrayList<>());
            tool.setDefaultMiningSpeed(charged ? 4f : 1f);
            tool.setDamagePerBlock(0);
            for (Tag<Material> tag : List.of(Tag.MINEABLE_PICKAXE, Tag.MINEABLE_AXE, Tag.MINEABLE_SHOVEL, Tag.MINEABLE_HOE)) {
                tool.addRule(tag, speed, charged);
            }
            meta.setTool(tool);
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
