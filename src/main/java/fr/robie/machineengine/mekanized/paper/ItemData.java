package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.MekanizedContent;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.libraries.nbt.ByteArrayTag;
import net.momirealms.craftengine.libraries.nbt.ListTag;
import net.momirealms.craftengine.libraries.nbt.LongTag;
import net.momirealms.craftengine.libraries.nbt.NumericTag;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What Mekanized keeps on its items, in CraftEngine item data (D32): {@code minecraft:custom_data} →
 * {@code mekanized.<field>}. Items from before kept it in Bukkit persistent data
 * ({@code PublicBukkitValues."mekanized:<field>"}); a value is moved the first time it is read.
 *
 * <p>Writes change the given stack itself, whether it is a server stack (from an inventory) or a plain
 * Bukkit one.</p>
 */
public final class ItemData {
    private static final String ROOT = MekanizedContent.NAMESPACE;
    private static final String OLD_PDC = "PublicBukkitValues";

    /** The fields Mekanized keeps on items. */
    public enum Field {
        /** Gear: the installed modules, a list of names. */
        MODULES("modules"),
        /** Configuration card: the copied configuration. */
        CONFIGURATION("configuration"),
        /** Gauge dropper: the resource type key and the amount it holds. */
        DROPPER_TYPE("dropper_type"),
        DROPPER_AMOUNT("dropper_amount"),
        /** Crafting formula: the recorded pattern. */
        FORMULA("formula"),
        /** Portable teleporter and QIO dashboard: their frequency. */
        FREQUENCY("frequency"),
        /** Cardboard box: the packed block, its contents and a spawner's mob. */
        BOX_BLOCK("box_block"),
        BOX_ITEMS("box_items"),
        BOX_SPAWNER("box_spawner");

        private final String name;

        Field(String name) {
            this.name = name;
        }
    }

    private ItemData() {
    }

    public static @Nullable String getString(@Nullable ItemStack stack, @NotNull Field field) {
        return read(stack, field) instanceof StringTag tag ? tag.value() : null;
    }

    public static long getLong(@Nullable ItemStack stack, @NotNull Field field) {
        return read(stack, field) instanceof NumericTag tag ? tag.getAsLong() : 0;
    }

    public static byte @Nullable [] getBytes(@Nullable ItemStack stack, @NotNull Field field) {
        return read(stack, field) instanceof ByteArrayTag tag ? tag.getAsByteArray() : null;
    }

    /** A list of strings; an old comma-separated string reads as its parts. */
    public static @NotNull List<String> getStrings(@Nullable ItemStack stack, @NotNull Field field) {
        return strings(read(stack, field));
    }

    /** Sets the string, or removes the field ({@code null}). */
    public static void setString(@NotNull ItemStack stack, @NotNull Field field, @Nullable String value) {
        write(stack, field, value == null ? null : new StringTag(value));
    }

    /** Sets the number, or removes the field (0). */
    public static void setLong(@NotNull ItemStack stack, @NotNull Field field, long value) {
        write(stack, field, value == 0 ? null : new LongTag(value));
    }

    public static void setBytes(@NotNull ItemStack stack, @NotNull Field field, byte @Nullable [] value) {
        write(stack, field, value == null ? null : new ByteArrayTag(value));
    }

    /** Sets the list, or removes the field (empty). */
    public static void setStrings(@NotNull ItemStack stack, @NotNull Field field, @NotNull List<String> values) {
        if (values.isEmpty()) {
            write(stack, field, null);
            return;
        }
        ListTag list = new ListTag();
        for (String value : values) list.add(new StringTag(value));
        write(stack, field, list);
    }

    // -- reading a CraftEngine item (an item being sent: never changed) ---------------------------

    /** The field's value on {@code item}, new or old place, without moving it. */
    public static @Nullable Tag peek(@NotNull Item item, @NotNull Field field) {
        Tag tag = item.getSparrowTag(ROOT, field.name);
        return tag != null ? tag : item.getSparrowTag(OLD_PDC, ROOT + ":" + field.name);
    }

    public static @Nullable String peekString(@NotNull Item item, @NotNull Field field) {
        return peek(item, field) instanceof StringTag tag ? tag.value() : null;
    }

    public static long peekLong(@NotNull Item item, @NotNull Field field) {
        return peek(item, field) instanceof NumericTag tag ? tag.getAsLong() : 0;
    }

    public static @NotNull List<String> peekStrings(@NotNull Item item, @NotNull Field field) {
        return strings(peek(item, field));
    }

    // -- internals ---------------------------------------------------------------------------------

    private static List<String> strings(@Nullable Tag tag) {
        List<String> values = new ArrayList<>();
        if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof StringTag string) values.add(string.value());
            }
        } else if (tag instanceof StringTag string && !string.value().isEmpty()) {
            for (String part : string.value().split(",")) if (!part.isBlank()) values.add(part.trim());
        }
        return values;
    }

    private static @Nullable Tag read(@Nullable ItemStack stack, Field field) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = BukkitAdaptor.adapt(stack);
        Tag tag = item.getSparrowTag(ROOT, field.name);
        if (tag != null) return tag;
        Tag old = item.getSparrowTag(OLD_PDC, ROOT + ":" + field.name);
        if (old != null) {
            // Moved on first read: on a server stack this sticks, on a copy it is done again next time.
            edit(stack, it -> {
                it.setSparrowTag(old, ROOT, field.name);
                it.removeTag(OLD_PDC, ROOT + ":" + field.name);
            });
        }
        return old;
    }

    private static void write(ItemStack stack, Field field, @Nullable Tag value) {
        edit(stack, item -> {
            item.removeTag(OLD_PDC, ROOT + ":" + field.name);
            if (value == null) item.removeTag(ROOT, field.name);
            else item.setSparrowTag(value, ROOT, field.name);
        });
    }

    private static void edit(ItemStack stack, Consumer<Item> change) {
        Item item = BukkitAdaptor.adapt(stack);
        change.accept(item);
        // A server stack was changed in place; a plain Bukkit stack was copied by CraftEngine: copy back.
        ItemStack result = (ItemStack) item.platformItem();
        if (result != stack) stack.setItemMeta(result.getItemMeta());
    }
}
