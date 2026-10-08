package fr.robie.machineengine.mekanized.paper.gear;

import com.google.gson.JsonArray;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.paper.ResourceNames;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.mekanized.machine.ConfigurationCard;
import fr.robie.machineengine.mekanized.machine.GaugeDropper;
import fr.robie.machineengine.mekanized.paper.PaperTools;
import fr.robie.machineengine.mekanized.paper.ItemData;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.component.DataComponentKeys;
import net.momirealms.craftengine.core.item.network.ItemPacketSource;
import net.momirealms.craftengine.core.item.processor.ItemProcessorFactory;
import net.momirealms.craftengine.core.item.processor.SimpleNetworkItemProcessor;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The CraftEngine item processor {@code mekanized:gear_lore} (D32), for an item's {@code client-bound-data}:
 * each time the item is sent to a player, its lore shows what Mekanized keeps on it (installed modules, a
 * configuration card's machine, a gauge dropper's contents, a formula, a frequency, a boxed block),
 * translated on the player's client ({@code mekanized.lore.*} in Mekanized's {@code lang.yml}).
 *
 * <p>It replaces the item's lore: Mekanized items have none of their own, and items from before kept an
 * English one written by the server. List it before {@code machineengine:resource_lore}, which adds the
 * energy and chemical lines after it.</p>
 *
 * <pre>{@code
 * client-bound-data:
 *   mekanized:gear_lore: {}
 *   machineengine:resource_lore: {}
 * }</pre>
 */
public final class GearLoreProcessor implements SimpleNetworkItemProcessor {
    public static final GearLoreProcessor INSTANCE = new GearLoreProcessor();
    public static final ItemProcessorFactory<GearLoreProcessor> FACTORY = value -> INSTANCE;

    private GearLoreProcessor() {
    }

    @Override
    public void apply(ItemBuildContext context) {
        Item item = context.item();
        List<Component> lines = new ArrayList<>();
        for (String module : ItemData.peekStrings(item, ItemData.Field.MODULES)) {
            lines.add(Component.translatable("mekanized.lore.module", "§b+ %s",
                    Component.translatable("item.mekanized.module_" + module, humanize(module))));
        }
        String card = ItemData.peekString(item, ItemData.Field.CONFIGURATION);
        if (card != null) {
            Key machine = Key.key(ConfigurationCard.parse(card).type());
            lines.add(Component.translatable("mekanized.lore.card", "§7Holds: %s",
                    Component.translatable("block." + machine.namespace() + "." + machine.value(), humanize(machine.value()))));
        }
        String dropperType = ItemData.peekString(item, ItemData.Field.DROPPER_TYPE);
        long dropped = ItemData.peekLong(item, ItemData.Field.DROPPER_AMOUNT);
        if (dropperType != null && dropped > 0) {
            var type = MachineEngine.get().registries().registry(Registries.RESOURCE_TYPE).get(Key.key(dropperType)).orElse(null);
            Component name = type == null ? Component.text(dropperType) : MiniMessage.miniMessage().deserialize(ResourceNames.of(type.variant()));
            lines.add(Component.translatable("mekanized.lore.dropper", "§7%s: %s / %s mB", name,
                    Component.text(String.format(Locale.ROOT, "%,d", dropped)),
                    Component.text(String.format(Locale.ROOT, "%,d", dropperCapacity(item)))));
        }
        if (ItemData.peekString(item, ItemData.Field.FORMULA) != null) {
            lines.add(Component.translatable("mekanized.lore.formula", "§bEncoded"));
        }
        String frequency = ItemData.peekString(item, ItemData.Field.FREQUENCY);
        if (frequency != null && !frequency.isEmpty()) {
            lines.add(Component.translatable("mekanized.lore.frequency", "§7Frequency: %s", Component.text(frequency)));
        }
        String boxed = ItemData.peekString(item, ItemData.Field.BOX_BLOCK);
        if (boxed != null) {
            Material material = Material.matchMaterial(boxed.contains("[") ? boxed.substring(0, boxed.indexOf('[')) : boxed);
            Component block = material == null ? Component.text(boxed) : Component.translatable(material.translationKey());
            lines.add(Component.translatable("mekanized.lore.box", "§7Contains: %s", block));
        }
        JsonArray lore = new JsonArray();
        for (Component line : lines) {
            lore.add(GsonComponentSerializer.gson().serializeToTree(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        }
        item.loreJson(lore);
    }

    private static String humanize(String id) {
        String text = id.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    @Override
    public boolean shouldSkip(ItemPacketSource source) {
        return source.canSkipLore;
    }

    @Override
    public net.momirealms.craftengine.core.util.Key componentType(Item item, ItemBuildContext context) {
        return DataComponentKeys.LORE;
    }

    @Override
    public Object[] nbtPath(Item item, ItemBuildContext context) {
        return new Object[] {"display", "Lore"};
    }

    @Override
    public String nbtPathString(Item item, ItemBuildContext context) {
        return "display.Lore";
    }

    /** The dropper's {@code capacity} from its item config (D32), or Mekanism's. */
    private static long dropperCapacity(Item item) {
        return item.getBehavior()
                .map(found -> found.getFirst(PaperTools.DropperBehavior.class))
                .map(PaperTools.DropperBehavior::capacity)
                .orElse(GaugeDropper.DEFAULT_CAPACITY);
    }
}
