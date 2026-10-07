package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.mekanized.machine.ConfigurationCard;
import fr.robie.machineengine.mekanized.machine.EjectBehavior;
import fr.robie.machineengine.mekanized.machine.GaugeDropper;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.mekanized.paper.Lore;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.item.MachineTool;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mekanism's small tools, on Paper, as CraftEngine item behaviors (D32):
 * <ul>
 *   <li>configuration card: sneak-right-click a machine to copy its side configuration, auto-eject and
 *       redstone control; right-click another machine of the same kind to paste them;</li>
 *   <li>dictionary: right-click a block (or the air, for the item in the off hand) to list its tags;</li>
 *   <li>gauge dropper: right-click a machine to take up to 16,000 mB of a fluid or chemical it holds,
 *       sneak-right-click to put it back in, sneak-right-click the air to empty it;</li>
 *   <li>crafting formula: sneak-right-click a Formulaic Assemblicator to record its pattern,
 *       right-click one with an empty pattern to set it.</li>
 * </ul>
 */
public final class PaperTools {
    private PaperTools() {
    }


    /** {@code mekanized:configuration_card}: sneak-right-click a machine to copy, right-click to paste. */
    public static final class CardBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<CardBehavior> FACTORY = (pack, path, id, section) -> new CardBehavior();

        @Override
        protected boolean use(Use use) {
            MachineInstance machine = machine(use.block());
            if (machine == null) return false;
            if (use.sneaking()) copy(use.player(), use.item(), machine);
            else paste(use.player(), use.item(), machine);
            return true;
        }
    }

    /** {@code mekanized:dictionary}: the tags of the block clicked, or of the item in the off hand. */
    public static final class DictionaryBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<DictionaryBehavior> FACTORY = (pack, path, id, section) -> new DictionaryBehavior();

        @Override
        protected boolean use(Use use) {
            dictionary(use.player(), use.block());
            return true;
        }
    }

    /** {@code mekanized:gauge_dropper}: takes from a machine, sneak to put back, sneak in the air to empty. */
    public static final class DropperBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<DropperBehavior> FACTORY = (pack, path, id, section) -> new DropperBehavior();

        @Override
        protected boolean use(Use use) {
            Player player = use.player();
            MachineInstance machine = machine(use.block());
            if (machine == null) {
                if (use.sneaking()) setDropper(use.item(), null, 0);
                player.sendActionBar(Component.text(use.sneaking() ? "Emptied" : "Right-click a machine", NamedTextColor.GRAY));
            } else if (use.sneaking()) {
                putBack(player, use.item(), machine);
            } else {
                take(player, use.item(), machine);
            }
            return true;
        }
    }

    /** {@code mekanized:crafting_formula}: records or sets a Formulaic Assemblicator's pattern. */
    public static final class FormulaBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<FormulaBehavior> FACTORY = (pack, path, id, section) -> new FormulaBehavior();

        @Override
        protected boolean use(Use use) {
            MachineInstance machine = machine(use.block());
            if (machine == null || !machine.inspect().type().equals(MekanizedContent.key("formulaic_assemblicator"))) return false;
            formula(use.player(), use.item(), machine);
            return true;
        }
    }

    private static @Nullable MachineInstance machine(@Nullable Block block) {
        if (block == null) return null;
        MachineInstance machine = PaperWorld.machine(block);
        return machine == null ? null : machine;
    }

    // -- configuration card ---------------------------------------------------------------------

    /** The card's text: machine type; then per port {@code port=DIRECTION:SIDES}; eject; redstone. */
    private static void copy(Player player, ItemStack card, MachineInstance machine) {
        MachineContext context = machine.context();
        List<ConfigurationCard.PortSetting> ports = new ArrayList<>();
        for (PortDefinition port : machine.type().ports()) {
            ports.add(new ConfigurationCard.PortSetting(port.id(), context.portDirection(port.id()), context.portSides(port.id())));
        }
        String text = ConfigurationCard.encode(machine.inspect().type().asString(), ports,
                has(machine, EjectBehavior.EJECT) ? context.get(EjectBehavior.EJECT) : null,
                has(machine, UtilityMachines.REDSTONE) ? context.get(UtilityMachines.REDSTONE) : null);
        ItemData.setString(card, ItemData.Field.CONFIGURATION, text);
        card.editMeta(meta -> meta.lore(List.of(Lore.line("Holds: " + machine.inspect().type().value().replace('_', ' ')))));
        player.sendActionBar(Component.text("Configuration copied", NamedTextColor.GREEN));
    }

    private static void paste(Player player, ItemStack card, MachineInstance machine) {
        String text = ItemData.getString(card, ItemData.Field.CONFIGURATION);
        if (text == null) {
            player.sendActionBar(Component.text("Empty: sneak-right-click a machine to copy it", NamedTextColor.RED));
            return;
        }
        ConfigurationCard.Card parsed = ConfigurationCard.parse(text);
        if (!parsed.type().equals(machine.inspect().type().asString())) {
            player.sendActionBar(Component.text("This card holds a " + Key.key(parsed.type()).value().replace('_', ' '), NamedTextColor.RED));
            return;
        }
        MachineContext context = machine.context();
        Set<String> ports = new HashSet<>();
        machine.type().ports().forEach(port -> ports.add(port.id()));
        for (ConfigurationCard.PortSetting setting : parsed.ports()) {
            if (!ports.contains(setting.port())) continue;
            try {
                context.setPortDirection(setting.port(), setting.direction());
                if (setting.sides() != null) context.setPortSides(setting.port(), setting.sides());
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // an old or edited card: skip the part
            }
        }
        if (parsed.eject() != null && has(machine, EjectBehavior.EJECT)) context.set(EjectBehavior.EJECT, parsed.eject());
        if (parsed.redstone() != null && has(machine, UtilityMachines.REDSTONE)) context.set(UtilityMachines.REDSTONE, parsed.redstone());
        machine.wake(WakeReason.CONFIG_CHANGED);
        player.sendActionBar(Component.text("Configuration pasted", NamedTextColor.GREEN));
    }

    private static boolean has(MachineInstance machine, MachineDataKey<?> key) {
        return machine.type().data().stream().anyMatch(data -> data.id().equals(key.id()));
    }

    // -- dictionary --------------------------------------------------------------------------------

    private static void dictionary(Player player, @Nullable Block block) {
        Set<String> tags = new TreeSet<>();
        String what;
        if (block != null) {
            what = blockId(block);
            for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class)) {
                if (tag.isTagged(block.getType())) tags.add("#" + tag.getKey().asString());
            }
        } else {
            ItemStack item = player.getInventory().getItemInOffHand();
            if (item.getType().isAir()) {
                player.sendMessage(Component.text("Right-click a block, or hold an item in your off hand", NamedTextColor.GRAY));
                return;
            }
            what = PaperItems.idOf(item).asString();
            for (Tag<Material> tag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
                if (tag.isTagged(item.getType())) tags.add("#" + tag.getKey().asString());
            }
        }
        player.sendMessage(Component.text(what, NamedTextColor.YELLOW));
        player.sendMessage(Component.text(tags.isEmpty() ? "No tags" : String.join(", ", tags), NamedTextColor.GRAY));
    }

    private static String blockId(Block block) {
        MachineInstance machine = PaperWorld.machine(block);
        if (machine != null) return machine.inspect().type().asString();
        return block.getType().getKey().asString();
    }

    // -- gauge dropper -----------------------------------------------------------------------------

    private static @Nullable ResourceType dropperType(ItemStack dropper) {
        String key = ItemData.getString(dropper, ItemData.Field.DROPPER_TYPE);
        if (key == null) return null;
        // Through the registry, so keys saved before a rename (mekanized:fluid/water) follow its aliases.
        return MachineEngine.get().registries().registry(Registries.RESOURCE_TYPE).get(Key.key(key)).orElse(null);
    }

    private static long dropperAmount(ItemStack dropper) {
        return ItemData.getLong(dropper, ItemData.Field.DROPPER_AMOUNT);
    }

    private static void setDropper(ItemStack dropper, @Nullable ResourceType type, long amount) {
        boolean empty = type == null || amount <= 0;
        ItemData.setString(dropper, ItemData.Field.DROPPER_TYPE, empty ? null : type.key().asString());
        ItemData.setLong(dropper, ItemData.Field.DROPPER_AMOUNT, empty ? 0 : amount);
        dropper.editMeta(meta -> meta.lore(empty ? List.of() : List.of(Lore.line(GaugeDropper.lore(type, amount)))));
    }

    /** Takes a fluid or chemical (the one it holds, or the first the machine has) into the dropper. */
    private static void take(Player player, ItemStack dropper, MachineInstance machine) {
        ResourceType held = dropperType(dropper);
        long room = GaugeDropper.CAPACITY - dropperAmount(dropper);
        GaugeDropper.Taken taken = GaugeDropper.take(machine.context(), machine.inspect().storages(), held, dropperAmount(dropper));
        if (taken != null) {
            setDropper(dropper, taken.type(), dropperAmount(dropper) + taken.amount());
            machine.wake(WakeReason.RESOURCE_EXTRACTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Took %,d mB", taken.amount()), NamedTextColor.GREEN));
            return;
        }
        player.sendActionBar(Component.text(room <= 0 ? "The dropper is full" : "Nothing to take", NamedTextColor.RED));
    }

    /** Puts what the dropper holds into the first storage of the machine that takes it. */
    private static void putBack(Player player, ItemStack dropper, MachineInstance machine) {
        ResourceType held = dropperType(dropper);
        long amount = dropperAmount(dropper);
        if (held == null || amount <= 0) {
            player.sendActionBar(Component.text("The dropper is empty", NamedTextColor.RED));
            return;
        }
        long given = GaugeDropper.putBack(machine.context(), machine.inspect().storages().keySet(), held, amount);
        if (given > 0) {
            setDropper(dropper, held, amount - given);
            machine.wake(WakeReason.RESOURCE_INSERTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Put in %,d mB", given), NamedTextColor.GREEN));
            return;
        }
        player.sendActionBar(Component.text("The machine takes none of it", NamedTextColor.RED));
    }

    // -- crafting formula --------------------------------------------------------------------------

    /** Sneaking: records the assemblicator's pattern. Otherwise: sets its pattern, if it has none. */
    private static void formula(Player player, ItemStack formula, MachineInstance machine) {
        MachineContext context = machine.context();
        List<StorageView> pattern = new ArrayList<>(context.storage("pattern").views());
        Base64.Encoder encoder = Base64.getEncoder();
        if (player.isSneaking()) {
            List<String> cells = new ArrayList<>();
            boolean any = false;
            for (StorageView cell : pattern) {
                ItemStack stack = cell.isEmpty() ? null : PaperItems.toItemStack(cell.variant(), 1);
                cells.add(stack == null ? "" : encoder.encodeToString(stack.serializeAsBytes()));
                any |= stack != null;
            }
            if (!any) {
                player.sendActionBar(Component.text("The assemblicator has no pattern", NamedTextColor.RED));
                return;
            }
            ItemData.setString(formula, ItemData.Field.FORMULA, String.join(",", cells));
            formula.editMeta(meta -> meta.lore(List.of(Lore.line("Encoded", NamedTextColor.AQUA))));
            player.sendActionBar(Component.text("Pattern recorded", NamedTextColor.GREEN));
            return;
        }
        String encoded = ItemData.getString(formula, ItemData.Field.FORMULA);
        if (encoded == null) {
            player.sendActionBar(Component.text("Blank: sneak-right-click an assemblicator to record its pattern", NamedTextColor.RED));
            return;
        }
        if (pattern.stream().anyMatch(cell -> !cell.isEmpty())) {
            player.sendActionBar(Component.text("Clear the assemblicator's pattern first", NamedTextColor.RED));
            return;
        }
        String[] cells = encoded.split(",", -1);
        Base64.Decoder decoder = Base64.getDecoder();
        try (Transaction transaction = context.transactions().openOuter()) {
            var storage = context.storage("pattern");
            // Slots fill in order: an empty cell gets a placeholder that is taken out again afterwards.
            List<ResourceVariant> placeholders = new ArrayList<>();
            for (String cell : cells) {
                if (cell.isEmpty()) {
                    ResourceVariant barrier = PaperItems.toResource(new ItemStack(Material.BARRIER)).variant();
                    storage.insert(barrier, 1, transaction);
                    placeholders.add(barrier);
                } else {
                    storage.insert(PaperItems.toResource(ItemStack.deserializeBytes(decoder.decode(cell))).variant(), 1, transaction);
                }
            }
            for (ResourceVariant placeholder : placeholders) storage.extract(placeholder, 1, transaction);
            transaction.commit();
        } catch (RuntimeException e) {
            player.sendActionBar(Component.text("This formula cannot be read", NamedTextColor.RED));
            return;
        }
        machine.wake(WakeReason.CONFIG_CHANGED);
        player.sendActionBar(Component.text("Pattern set", NamedTextColor.GREEN));
    }
}
