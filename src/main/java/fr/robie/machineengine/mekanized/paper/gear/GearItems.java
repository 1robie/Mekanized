package fr.robie.machineengine.mekanized.paper.gear;

import fr.robie.machineengine.api.item.ResourceItem;
import fr.robie.machineengine.api.paper.PaperItemResources;
import fr.robie.machineengine.api.paper.PaperItems;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.mekanized.MekanizedContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.key.Key;
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

    /**
     * After a change: a mining tool's speed follows its charge. The lore is drawn for each player when the
     * item is sent ({@code mekanized:gear_lore}, {@code machineengine:resource_lore}); the English lore the
     * server wrote before is removed.
     */
    public static void refresh(ItemStack stack) {
        if (id(stack) == null) return;
        GearBehaviors.MiningTool mining = GearBehaviors.options(stack, GearBehaviors.MiningTool.class);
        boolean oldLore = stack.hasItemMeta() && stack.getItemMeta().hasLore();
        if (mining == null && !oldLore) return;
        long energy = mining == null ? 0 : energy(stack);
        Set<String> modules = mining == null ? Set.of() : modules(stack);
        stack.editMeta(meta -> {
            if (oldLore) meta.lore(null);
            if (mining == null) return;
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

}
