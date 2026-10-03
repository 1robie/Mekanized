package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** Energy items on Paper: the charge in the item's persistent data, shown in its lore. */
final class PaperEnergyItems implements EnergyItems.Access {
    private static final NamespacedKey ENERGY = new NamespacedKey(MekanizedContent.NAMESPACE, "energy");

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
        long capacity = EnergyItems.capacity(item);
        stack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(ENERGY, PersistentDataType.LONG, energy);
            meta.lore(List.of(Component.text("Energy: " + format(energy) + " / " + format(capacity) + " J", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        });
        return PaperItems.toResource(stack, data.item()).variant();
    }

    private static String format(long joules) {
        return String.format(Locale.ROOT, "%,d", joules);
    }
}
