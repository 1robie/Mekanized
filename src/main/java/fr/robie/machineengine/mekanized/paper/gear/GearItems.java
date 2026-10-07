package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ResourceItem;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.paper.PaperItems;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.paper.Lore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.ToolComponent;
import org.jetbrains.annotations.Nullable;
import java.util.LinkedHashSet;
import fr.robie.machineengine.mekanized.paper.ItemData;

/**
 * What a piece of Mekanized gear carries: energy and a chemical (MachineEngine's
 * {@code energy_item} and {@code resource_item} behaviors and item data, D32), modules, and the lore and
 * tool speeds that show them.
 */
public final class GearItems {
    private GearItems() {
    }

    // -- item data ------------------------------------------------------------------------------

    public static @Nullable String id(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Key key = PaperItems.idOf(stack);
        return key.namespace().equals(MekanizedContent.NAMESPACE) ? key.value() : null;
    }

    static long energy(ItemStack stack) {
        return PaperItemResources.amount(stack, Energy.KEY);
    }

    /** Takes {@code joules} from the gear if it has them all. */
    public static boolean useEnergy(ItemStack stack, long joules) {
        if (energy(stack) < joules) return false;
        PaperItemResources.add(stack, Energy.KEY, -joules);
        return true;
    }

    /** The chemical (or fluid) the gear holds besides energy, or {@code null}. */
    static @Nullable Key chemicalType(ItemStack stack) {
        for (ResourceItem holder : PaperItemResources.behaviors(stack)) {
            if (!holder.resource().equals(Energy.KEY)) return holder.resource();
        }
        return null;
    }

    static long chemical(ItemStack stack) {
        Key type = chemicalType(stack);
        return type == null ? 0 : PaperItemResources.amount(stack, type);
    }

    static long chemicalCapacity(ItemStack stack) {
        Key type = chemicalType(stack);
        return type == null ? 0 : PaperItemResources.capacity(stack, type);
    }

    static boolean useChemical(ItemStack stack, long amount) {
        Key type = chemicalType(stack);
        if (type == null || PaperItemResources.amount(stack, type) < amount) return false;
        PaperItemResources.add(stack, type, -amount);
        return true;
    }

    public static Set<String> modules(@Nullable ItemStack stack) {
        return new LinkedHashSet<>(ItemData.getStrings(stack, ItemData.Field.MODULES));
    }

    /** The item's energy capacity, with its modules (an energy unit raises it). */
    public static long capacity(ItemStack stack) {
        return PaperItemResources.capacity(stack, Energy.KEY);
    }

    /** Adds up to {@code joules} (what fits); returns what was added. */
    static long addEnergy(ItemStack stack, long joules) {
        return joules <= 0 ? 0 : PaperItemResources.add(stack, Energy.KEY, joules);
    }

    /** Rewrites the lore (energy, chemical, modules) and the tool speeds of a Mekanized item. */
    public static void refresh(ItemStack stack) {
        String id = id(stack);
        if (id == null) return;
        long capacity = capacity(stack);
        long energy = energy(stack);
        Key chemical = chemicalType(stack);
        long chemicalAmount = chemical == null ? 0 : PaperItemResources.amount(stack, chemical);
        long chemicalCapacity = chemical == null ? 0 : PaperItemResources.capacity(stack, chemical);
        Set<String> modules = modules(stack);
        List<Component> lore = new ArrayList<>();
        if (capacity > 0) lore.add(Lore.line(String.format(Locale.ROOT, "Energy: %,d / %,d J", energy, capacity)));
        if (chemical != null) {
            String name = MekanizedContent.displayName(chemical.value());
            lore.add(Lore.line(String.format(Locale.ROOT, "%s: %,d / %,d mB", capitalize(name), chemicalAmount, chemicalCapacity)));
        }
        for (String module : modules) lore.add(Lore.line("+ " + capitalize(module.replace('_', ' '))).color(NamedTextColor.AQUA));
        GearBehaviors.MiningTool mining = GearBehaviors.options(stack, GearBehaviors.MiningTool.class);
        boolean tool = mining != null;
        if (lore.isEmpty() && !tool) return;
        stack.editMeta(meta -> {
            meta.lore(lore);
            if (!tool) return;
            // Mining tools: fast on what pickaxes, axes, shovels and hoes mine while charged.
            boolean charged = energy >= mining.energyPerBlock();
            float speed = !charged ? 1f : modules.contains("excavation_escalation_unit") ? mining.amplifiedSpeed() : mining.chargedSpeed();
            ToolComponent component = meta.getTool();
            component.setRules(new ArrayList<>());
            component.setDefaultMiningSpeed(charged ? 4f : 1f);
            component.setDamagePerBlock(0);
            for (Tag<Material> tag : List.of(Tag.MINEABLE_PICKAXE, Tag.MINEABLE_AXE, Tag.MINEABLE_SHOVEL, Tag.MINEABLE_HOE)) {
                component.addRule(tag, speed, charged);
            }
            meta.setTool(component);
        });
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
