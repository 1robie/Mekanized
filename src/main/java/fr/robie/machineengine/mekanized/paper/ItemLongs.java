package fr.robie.machineengine.mekanized.paper;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Numbers (energy, chemical amounts) kept in an item's persistent data; 0 when absent. */
public final class ItemLongs {
    private ItemLongs() {
    }

    public static long get(PersistentDataContainer data, NamespacedKey key) {
        Long value = data.get(key, PersistentDataType.LONG);
        return value == null ? 0 : value;
    }

    public static long get(ItemStack stack, NamespacedKey key) {
        return get(stack.getItemMeta().getPersistentDataContainer(), key);
    }

    public static void set(ItemMeta meta, NamespacedKey key, long value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
    }
}
