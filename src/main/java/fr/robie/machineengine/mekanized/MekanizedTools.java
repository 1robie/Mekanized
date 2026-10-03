package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.machine.LoadedMachine;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.paper.MachineLocator;
import fr.robie.machineengine.paper.MachineMenuService;
import fr.robie.machineengine.paper.PaperItems;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Mekanized's tools on machines (Phase 4):
 * <ul>
 *   <li>tier installers: a machine becomes its basic factory, a factory its next tier, keeping what it
 *       holds (the running operation is cancelled, its inputs given back);</li>
 *   <li>the configurator: opens the side configuration; while sneaking, turns the machine. On a
 *       transport, it cycles the clicked side: default, pull, push, none; on a container, the side of
 *       every transport touching it.</li>
 * </ul>
 * Runs before CraftEngine's own handling, which then sees a cancelled event and opens no menu.
 */
final class MekanizedTools implements Listener {
    private static final String INSTALLER_SUFFIX = "_tier_installer";
    private static final Direction[] TURN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || item == null || item.getType().isAir()) return;
        Key tool = PaperItems.idOf(item);
        if (!tool.namespace().equals(MekanizedContent.NAMESPACE)) return;
        boolean installer = tool.value().endsWith(INSTALLER_SUFFIX);
        if (!installer && !tool.value().equals("configurator")) return;
        MachineLocator locator = MachineLocator.current();
        WorldPosition position = WorldPosition.of(block.getWorld().getKey(), block.getX(), block.getY(), block.getZ());
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
            String what = mode == null ? null : face.name().toLowerCase(java.util.Locale.ROOT) + " side of the transport: ";
            if (mode == null && block.getState(false) instanceof org.bukkit.block.Container) {
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
            java.util.Set<String> groups = new java.util.HashSet<>();
            machine.instance().type().ports().forEach(port -> {
                if (port.group() != null) groups.add(port.group());
            });
            String menu = MekanizedMenus.sideMenuFor(groups);
            if (menus == null || menu == null || !menus.open(player, machine, menu)) {
                player.sendActionBar(Component.text("No side configuration menu", NamedTextColor.RED));
            }
        }
    }

    private static String describe(fr.robie.machineengine.api.network.TransportSideMode mode) {
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
        int index = java.util.Arrays.asList(TURN).indexOf(facing);
        Direction next = TURN[(index + 1) % TURN.length];
        if (!locator.replaceBlock(machine.position(), machine.inspect().type(), next)) {
            player.sendActionBar(Component.text("The machine is busy; try again", NamedTextColor.RED));
        }
    }
}
