package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.paper.gear.GearItems;
import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.machine.ConfigurationCard;
import fr.robie.machineengine.mekanized.machine.EjectBehavior;
import fr.robie.machineengine.mekanized.machine.GaugeDropper;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.mekanized.paper.ItemLongs;
import fr.robie.machineengine.mekanized.paper.Lore;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mekanism's small tools, on Paper:
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
public final class PaperTools implements Listener {
    private static final NamespacedKey CARD = new NamespacedKey(MekanizedContent.NAMESPACE, "configuration");
    private static final NamespacedKey DROPPER_TYPE = new NamespacedKey(MekanizedContent.NAMESPACE, "dropper_type");
    private static final NamespacedKey DROPPER_AMOUNT = new NamespacedKey(MekanizedContent.NAMESPACE, "dropper_amount");
    private static final NamespacedKey FORMULA = new NamespacedKey(MekanizedContent.NAMESPACE, "formula");

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        boolean onBlock = event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!onBlock && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = GearItems.id(hand);
        if (id == null) return;
        Block block = event.getClickedBlock();
        switch (id) {
            case "configuration_card" -> {
                LoadedMachine machine = onBlock ? machine(block) : null;
                if (machine == null) return;
                event.setCancelled(true);
                if (player.isSneaking()) copy(player, hand, machine);
                else paste(player, hand, machine);
            }
            case "dictionary" -> {
                event.setCancelled(true);
                dictionary(player, block);
            }
            case "gauge_dropper" -> {
                event.setCancelled(true);
                LoadedMachine machine = onBlock ? machine(block) : null;
                if (machine == null) {
                    if (player.isSneaking()) setDropper(hand, null, 0);
                    player.sendActionBar(Component.text(player.isSneaking() ? "Emptied" : "Right-click a machine", NamedTextColor.GRAY));
                } else if (player.isSneaking()) {
                    putBack(player, hand, machine);
                } else {
                    take(player, hand, machine);
                }
            }
            case "crafting_formula" -> {
                LoadedMachine machine = onBlock ? machine(block) : null;
                if (machine == null || !machine.inspect().type().equals(MekanizedContent.key("formulaic_assemblicator"))) return;
                event.setCancelled(true);
                formula(player, hand, machine);
            }
            default -> {
            }
        }
    }

    private static @Nullable LoadedMachine machine(@Nullable Block block) {
        if (block == null) return null;
        LoadedMachine machine = PaperWorld.machine(block);
        return machine == null || machine.instance() == null ? null : machine;
    }

    // -- configuration card ---------------------------------------------------------------------

    /** The card's text: machine type; then per port {@code port=DIRECTION:SIDES}; eject; redstone. */
    private static void copy(Player player, ItemStack card, LoadedMachine machine) {
        MachineContext context = machine.instance().context();
        List<ConfigurationCard.PortSetting> ports = new ArrayList<>();
        for (PortDefinition port : machine.instance().type().ports()) {
            ports.add(new ConfigurationCard.PortSetting(port.id(), context.portDirection(port.id()), context.portSides(port.id())));
        }
        String text = ConfigurationCard.encode(machine.inspect().type().asString(), ports,
                has(machine, EjectBehavior.EJECT) ? context.get(EjectBehavior.EJECT) : null,
                has(machine, UtilityMachines.REDSTONE) ? context.get(UtilityMachines.REDSTONE) : null);
        card.editMeta(meta -> {
            meta.getPersistentDataContainer().set(CARD, PersistentDataType.STRING, text);
            meta.lore(List.of(Lore.line("Holds: " + machine.inspect().type().value().replace('_', ' '))));
        });
        player.sendActionBar(Component.text("Configuration copied", NamedTextColor.GREEN));
    }

    private static void paste(Player player, ItemStack card, LoadedMachine machine) {
        String text = card.getItemMeta().getPersistentDataContainer().get(CARD, PersistentDataType.STRING);
        if (text == null) {
            player.sendActionBar(Component.text("Empty: sneak-right-click a machine to copy it", NamedTextColor.RED));
            return;
        }
        ConfigurationCard.Card parsed = ConfigurationCard.parse(text);
        if (!parsed.type().equals(machine.inspect().type().asString())) {
            player.sendActionBar(Component.text("This card holds a " + Key.key(parsed.type()).value().replace('_', ' '), NamedTextColor.RED));
            return;
        }
        MachineContext context = machine.instance().context();
        Set<String> ports = new HashSet<>();
        machine.instance().type().ports().forEach(port -> ports.add(port.id()));
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
        machine.instance().wake(WakeReason.CONFIG_CHANGED);
        player.sendActionBar(Component.text("Configuration pasted", NamedTextColor.GREEN));
    }

    private static boolean has(LoadedMachine machine, MachineDataKey<?> key) {
        return machine.instance().type().data().stream().anyMatch(data -> data.id().equals(key.id()));
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
        LoadedMachine machine = PaperWorld.machine(block);
        if (machine != null) return machine.inspect().type().asString();
        return block.getType().getKey().asString();
    }

    // -- gauge dropper -----------------------------------------------------------------------------

    private static @Nullable ResourceType dropperType(ItemStack dropper) {
        String key = dropper.getItemMeta().getPersistentDataContainer().get(DROPPER_TYPE, PersistentDataType.STRING);
        if (key == null) return null;
        return MekanizedContent.resourceType(Key.key(key));
    }

    private static long dropperAmount(ItemStack dropper) {
        return ItemLongs.get(dropper, DROPPER_AMOUNT);
    }

    private static void setDropper(ItemStack dropper, @Nullable ResourceType type, long amount) {
        dropper.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (type == null || amount <= 0) {
                pdc.remove(DROPPER_TYPE);
                pdc.remove(DROPPER_AMOUNT);
                meta.lore(List.of());
                return;
            }
            pdc.set(DROPPER_TYPE, PersistentDataType.STRING, type.key().asString());
            pdc.set(DROPPER_AMOUNT, PersistentDataType.LONG, amount);
            meta.lore(List.of(Lore.line(GaugeDropper.lore(type, amount))));
        });
    }

    /** Takes a fluid or chemical (the one it holds, or the first the machine has) into the dropper. */
    private static void take(Player player, ItemStack dropper, LoadedMachine machine) {
        ResourceType held = dropperType(dropper);
        long room = GaugeDropper.CAPACITY - dropperAmount(dropper);
        GaugeDropper.Taken taken = GaugeDropper.take(machine.instance().context(), machine.inspect().storages(), held, dropperAmount(dropper));
        if (taken != null) {
            setDropper(dropper, taken.type(), dropperAmount(dropper) + taken.amount());
            machine.instance().wake(WakeReason.RESOURCE_EXTRACTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Took %,d mB", taken.amount()), NamedTextColor.GREEN));
            return;
        }
        player.sendActionBar(Component.text(room <= 0 ? "The dropper is full" : "Nothing to take", NamedTextColor.RED));
    }

    /** Puts what the dropper holds into the first storage of the machine that takes it. */
    private static void putBack(Player player, ItemStack dropper, LoadedMachine machine) {
        ResourceType held = dropperType(dropper);
        long amount = dropperAmount(dropper);
        if (held == null || amount <= 0) {
            player.sendActionBar(Component.text("The dropper is empty", NamedTextColor.RED));
            return;
        }
        long given = GaugeDropper.putBack(machine.instance().context(), machine.inspect().storages().keySet(), held, amount);
        if (given > 0) {
            setDropper(dropper, held, amount - given);
            machine.instance().wake(WakeReason.RESOURCE_INSERTED);
            player.sendActionBar(Component.text(String.format(Locale.ROOT, "Put in %,d mB", given), NamedTextColor.GREEN));
            return;
        }
        player.sendActionBar(Component.text("The machine takes none of it", NamedTextColor.RED));
    }

    // -- crafting formula --------------------------------------------------------------------------

    /** Sneaking: records the assemblicator's pattern. Otherwise: sets its pattern, if it has none. */
    private static void formula(Player player, ItemStack formula, LoadedMachine machine) {
        MachineContext context = machine.instance().context();
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
            formula.editMeta(meta -> {
                meta.getPersistentDataContainer().set(FORMULA, PersistentDataType.STRING, String.join(",", cells));
                meta.lore(List.of(Lore.line("Encoded", NamedTextColor.AQUA)));
            });
            player.sendActionBar(Component.text("Pattern recorded", NamedTextColor.GREEN));
            return;
        }
        String encoded = formula.getItemMeta().getPersistentDataContainer().get(FORMULA, PersistentDataType.STRING);
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
        machine.instance().wake(WakeReason.CONFIG_CHANGED);
        player.sendActionBar(Component.text("Pattern set", NamedTextColor.GREEN));
    }
}
