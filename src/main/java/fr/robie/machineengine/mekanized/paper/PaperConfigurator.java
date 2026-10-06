package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.paper.menu.SideMenus;
import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.network.TransportSideMode;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.multiblock.MemberBehavior;
import fr.robie.machineengine.mekanized.multiblock.Multiblocks;
import fr.robie.machineengine.mekanized.multiblock.MultiblockMachines;
import fr.robie.machineengine.mekanized.multiblock.ReactorMachines;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.paper.MachineLocator;
import fr.robie.machineengine.paper.MachineMenuService;
import fr.robie.machineengine.api.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mekanized's tools on machines:
 * <ul>
 *   <li>tier installers: a machine becomes its basic factory, a factory its next tier, keeping what it
 *       holds (the running operation is cancelled, its inputs given back);</li>
 *   <li>the configurator: opens the side configuration; while sneaking, turns the machine. On a
 *       transport, it cycles the clicked side: default, pull, push, none; on a container, the side of
 *       every transport touching it.</li>
 * </ul>
 * Runs before CraftEngine's own handling, which then sees a cancelled event and opens no menu.
 */
public final class PaperConfigurator implements Listener {
    private static final String INSTALLER_SUFFIX = "_tier_installer";
    private static final Direction[] TURN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Multiblock frame blocks (the walls of every structure): they open their structure's menu (the anchor's). */
    private static final Set<Key> MEMBERS = MultiblockMachines.TYPES.stream().flatMap(type -> type.faces().stream())
            .collect(Collectors.toUnmodifiableSet());

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null) return;
        Key tool = item == null || item.getType().isAir() ? null : PaperItems.idOf(item);
        if (!event.getPlayer().isSneaking() && multiblock(event, block, tool)) return;
        if (blades(event, block, item, tool)) return;
        if (tool == null) return;
        if (!tool.namespace().equals(MekanizedContent.NAMESPACE)) return;
        boolean installer = tool.value().endsWith(INSTALLER_SUFFIX);
        if (!installer && !tool.value().equals("configurator")) return;
        MachineLocator locator = MachineLocator.current();
        WorldPosition position = PaperWorld.position(block);
        LoadedMachine machine = locator == null ? null : locator.machine(position);
        if (!installer && locator != null && (machine == null || machine.instance() == null)) {
            // A transport: the configurator cycles how the clicked side connects (Mekanism's connection types).
            var face = event.getBlockFace();
            Direction side;
            try {
                side = Direction.valueOf(face.name());
            } catch (IllegalArgumentException e) {
                return;
            }
            var mode = locator.cycleTransportSide(position, side);
            String what = mode == null ? null : face.name().toLowerCase(Locale.ROOT) + " side of the transport: ";
            if (mode == null && block.getState(false) instanceof Container) {
                // A container: the transports touching it change the side that faces it (easier to hit than a pipe's face).
                for (Direction direction : Direction.values()) {
                    WorldPosition neighbor = position.relative(direction);
                    var changed = locator.cycleTransportSide(neighbor, direction.opposite());
                    if (changed != null && mode == null) mode = changed;
                }
                what = "transport side facing this container: ";
            }
            if (mode == null) return;
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(what, NamedTextColor.GRAY).append(Component.text(describe(mode), NamedTextColor.YELLOW)));
            return;
        }
        if (machine == null || machine.instance() == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (installer) {
            install(player, item, locator, machine, tool.value().substring(0, tool.value().length() - INSTALLER_SUFFIX.length()));
        } else if (player.isSneaking()) {
            turn(player, locator, machine);
        } else {
            MachineMenuService menus = MachineMenuService.current();
            Set<String> groups = new HashSet<>();
            machine.instance().type().ports().forEach(port -> {
                if (port.group() != null) groups.add(port.group());
            });
            String menu = SideMenus.sideMenuFor(groups);
            if (menus == null || menu == null || !menus.open(player, machine, menu)) {
                player.sendActionBar(Component.text("No side configuration menu", NamedTextColor.RED));
            }
        }
    }

    /**
     * A multiblock member clicked: an induction port switches between input and output with the
     * configurator; otherwise every member opens its structure's menu, or says it is not formed.
     *
     * @return whether the click was handled
     */
    private static boolean multiblock(PlayerInteractEvent event, Block block, @org.jetbrains.annotations.Nullable Key tool) {
        MachineLocator locator = MachineLocator.current();
        if (locator == null) return false;
        WorldPosition position = PaperWorld.position(block);
        LoadedMachine member = locator.machine(position);
        if (member == null || member.instance() == null) return false;
        Key type = member.inspect().type();
        if (!MEMBERS.contains(type)) return false;
        boolean configurator = tool != null && tool.equals(MekanizedContent.key("configurator"));
        List<String> modes = type.value().equals("induction_port") ? List.of("input", "output")
                : ReactorMachines.MODES.get(type.value());
        if (configurator && modes == null) return false; // side configuration, turning
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (configurator) {
            var context = member.instance().context();
            String current = context.get(MemberBehavior.PORT_MODE);
            String mode = modes.get((modes.indexOf(current) + 1) % modes.size());
            context.set(MemberBehavior.PORT_MODE, mode);
            member.instance().wake(WakeReason.CONFIG_CHANGED);
            player.sendActionBar(Component.text(type.value().replace('_', ' ') + ": ", NamedTextColor.GRAY)
                    .append(Component.text(mode.replace('_', ' '), NamedTextColor.YELLOW)));
            return true;
        }
        var structure = Multiblocks.containing(position);
        LoadedMachine anchor = structure == null ? null : locator.machine(structure.anchor());
        MachineMenuService menus = MachineMenuService.current();
        if (anchor == null || menus == null) {
            player.sendActionBar(Component.text("Not formed: build a closed box (3 to 18 blocks a side), casings on every edge",
                    NamedTextColor.RED));
            return true;
        }
        menus.open(player, anchor);
        return true;
    }

    /**
     * Turbine blades on a rotor: right-click with blades adds one (two at most), with an empty hand
     * while sneaking takes one back.
     *
     * @return whether the click was handled
     */
    private static boolean blades(PlayerInteractEvent event, Block block, @org.jetbrains.annotations.Nullable ItemStack item,
                                  @org.jetbrains.annotations.Nullable Key tool) {
        LoadedMachine rotor = PaperWorld.machine(block);
        if (rotor == null || rotor.instance() == null || !rotor.inspect().type().equals(MekanizedContent.key("turbine_rotor"))) return false;
        Player player = event.getPlayer();
        boolean add = tool != null && tool.equals(MekanizedContent.key("turbine_blade"));
        boolean take = tool == null && player.isSneaking();
        if (!add && !take) return false;
        event.setCancelled(true);
        var context = rotor.instance().context();
        var storage = context.storage("blades");
        try (var transaction = context.transactions().openOuter()) {
            if (add) {
                var variant = PaperItems.toResource(item).variant();
                if (storage.insert(variant, 1, transaction) == 1) {
                    transaction.commit();
                    if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
                }
            } else {
                var held = storage.views().getFirst();
                var blade = held.isEmpty() ? null : held.variant();
                if (blade != null && storage.extract(blade, 1, transaction) == 1) {
                    var stack = PaperItems.toItemStack(blade, 1);
                    transaction.commit();
                    if (stack != null) PaperWorld.give(player, stack);
                }
            }
        }
        long count = storage.views().stream().mapToLong(StorageView::amount).sum();
        player.sendActionBar(Component.text("Rotor blades: " + count + " / 2", NamedTextColor.YELLOW));
        return true;
    }

    private static String describe(TransportSideMode mode) {
        return switch (mode) {
            case DEFAULT -> "default (machines in and out, containers filled)";
            case PULL -> "pull (takes from the block)";
            case PUSH -> "push (only fills the block)";
            case NONE -> "none (disconnected)";
        };
    }

    private static void install(Player player, ItemStack item, MachineLocator locator, LoadedMachine machine, String tier) {
        Key next = LogisticsMachines.upgrade(machine.inspect().type(), tier);
        if (next == null) {
            player.sendActionBar(Component.text("This " + tier + " tier installer does not fit this machine", NamedTextColor.RED));
            return;
        }
        if (!locator.replaceBlock(machine.position(), next)) {
            player.sendActionBar(Component.text("The machine is busy; try again", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        player.sendActionBar(Component.text("Upgraded to " + next.value().replace('_', ' '), NamedTextColor.GREEN));
    }

    /** Turns the machine a quarter clockwise (it keeps what it holds; a running operation restarts). */
    private static void turn(Player player, MachineLocator locator, LoadedMachine machine) {
        Direction facing = machine.inspect().facing();
        int index = Arrays.asList(TURN).indexOf(facing);
        Direction next = TURN[(index + 1) % TURN.length];
        if (!locator.replaceBlock(machine.position(), machine.inspect().type(), next)) {
            player.sendActionBar(Component.text("The machine is busy; try again", NamedTextColor.RED));
        }
    }
}
