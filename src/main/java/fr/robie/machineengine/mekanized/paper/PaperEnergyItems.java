package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.paper.gear.GearItems;
import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.mekanized.paper.ItemLongs;
import fr.robie.machineengine.paper.PaperItems;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Energy items on Paper: the charge in the item's persistent data, shown in its lore ({@link GearItems#refresh}). */
public final class PaperEnergyItems implements EnergyItems.Access {
    public static final NamespacedKey ENERGY = new NamespacedKey(MekanizedContent.NAMESPACE, "energy");

    @Override
    public long energy(@NotNull ResourceVariant item) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        if (stack == null || !stack.hasItemMeta()) return 0;
        return ItemLongs.get(stack, ENERGY);
    }

    @Override
    public long capacity(@NotNull ResourceVariant item, long base) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        return stack == null || !stack.hasItemMeta() ? base : Math.max(base, GearItems.capacity(stack));
    }

    @Override
    public @NotNull ResourceVariant withEnergy(@NotNull ResourceVariant item, long energy) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        ItemData data = item.components().get(Items.DATA);
        if (stack == null || data == null) return item;
        stack.editMeta(meta -> {
            ItemLongs.set(meta, ENERGY, energy);
            GearItems.refresh(meta, data.item());
        });
        return PaperItems.toResource(stack, data.item()).variant();
    }
}
