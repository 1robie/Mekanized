package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.paper.PaperItems;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** Energy items on Paper: the charge in the item's persistent data, shown in its lore ({@link PaperGear#refresh}). */
final class PaperEnergyItems implements EnergyItems.Access {
    static final NamespacedKey ENERGY = new NamespacedKey(MekanizedContent.NAMESPACE, "energy");

    @Override
    public long energy(@NotNull ResourceVariant item) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        if (stack == null || !stack.hasItemMeta()) return 0;
        Long energy = stack.getItemMeta().getPersistentDataContainer().get(ENERGY, PersistentDataType.LONG);
        return energy == null ? 0 : energy;
    }

    @Override
    public @NotNull ResourceVariant withEnergy(@NotNull ResourceVariant item, long energy) {
        ItemStack stack = PaperItems.toItemStack(item, 1);
        ItemData data = item.components().get(Items.DATA);
        if (stack == null || data == null) return item;
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(ENERGY, PersistentDataType.LONG, energy);
            PaperGear.refresh(meta, data.item());
        });
        return PaperItems.toResource(stack, data.item()).variant();
    }
}
