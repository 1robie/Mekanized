package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.mekanized.paper.menu.SideMenus;
import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.network.TransportSideMode;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.multiblock.MemberBehavior;
import fr.robie.machineengine.mekanized.multiblock.Multiblocks;
import fr.robie.machineengine.mekanized.multiblock.MultiblockMachines;
import fr.robie.machineengine.mekanized.multiblock.ReactorMachines;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.api.paper.MachineBlocks;
import fr.robie.machineengine.api.paper.MachineMenus;
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
import fr.robie.machineengine.api.transfer.ResourceHandler;
import org.jetbrains.annotations.Nullable;
import org.bukkit.block.BlockFace;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import fr.robie.machineengine.api.paper.PaperItemBehavior;
import fr.robie.machineengine.api.item.MachineTool;

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
 * The tools are CraftEngine item behaviors (D32): machines pass clicks with them on ({@link MachineTool}).
 * On a vanilla container that opens, sneak to reach the transports beside it.
 */
public final class PaperConfigurator implements Listener {
    private static final Direction[] TURN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Multiblock frame blocks (the walls of every structure): they open their structure's menu (the anchor's). */
    private static final Set<Key> MEMBERS = MultiblockMachines.TYPES.stream().flatMap(type -> type.faces().stream())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * The block side, until those blocks get behaviors of their own: a multiblock member opens its
     * structure's menu whatever the player holds (but the configurator), and sneaking with an empty hand
     * takes a blade off a turbine rotor.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null) return;
        Key tool = item == null || item.getType().isAir() ? null : PaperItems.idOf(item);
        if (MekanizedContent.key("configurator").equals(tool)) return; // ConfiguratorBehavior
        Player player = event.getPlayer();
        if (!player.isSneaking() && openStructure(player, block)) {
            event.setCancelled(true);
        } else if (tool == null && player.isSneaking() && takeBlade(player, block)) {
            event.setCancelled(true);
        }
    }

    /** {@code mekanized:configurator}: side configuration, turning, port modes, transport sides. */
    public static final class ConfiguratorBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<ConfiguratorBehavior> FACTORY = (pack, path, id, section) -> new ConfiguratorBehavior();

        @Override
        protected boolean use(Use use) {
            Block block = use.block();
            if (block == null) return false;
            Player player = use.player();
            MachineBlocks blocks = MachineBlocks.current();
            WorldPosition position = PaperWorld.position(block);
            MachineInstance machine = PaperWorld.machine(position);
            if (machine != null && !use.sneaking() && cyclePortMode(player, machine)) return true;
            if (machine == null) return blocks != null && cycleTransport(player, blocks, block, position, use.face());
            if (use.sneaking()) {
                turn(player, blocks, machine);
                return true;
            }
            MachineMenus menus = MachineMenus.current();
            Set<String> groups = new HashSet<>();
            machine.type().ports().forEach(port -> {
                if (port.group() != null) groups.add(port.group());
            });
            String menu = SideMenus.sideMenuFor(groups);
            if (menus == null || menu == null || !menus.open(player, machine.position(), menu)) {
                player.sendActionBar(Component.text("No side configuration menu", NamedTextColor.RED));
            }
            return true;
        }
    }

    /** {@code mekanized:tier_installer}: the machine becomes its next tier ({@code tier: advanced}). */
    public static final class InstallerBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<InstallerBehavior> FACTORY = (pack, path, id, section) ->
                new InstallerBehavior(section.getNonEmptyString("tier"));

        private final String tier;

        private InstallerBehavior(String tier) {
            this.tier = tier;
        }

        @Override
        protected boolean use(Use use) {
            MachineInstance machine = use.block() == null ? null : PaperWorld.machine(use.block());
            if (machine == null) return false;
            install(use.player(), use.item(), MachineBlocks.current(), machine, this.tier);
            return true;
        }
    }

    /** {@code mekanized:turbine_blade}: right-click a turbine rotor to add a blade (two at most). */
    public static final class BladeBehavior extends PaperItemBehavior implements MachineTool {
        public static final ItemBehaviorFactory<BladeBehavior> FACTORY = (pack, path, id, section) -> new BladeBehavior();

        @Override
        protected boolean use(Use use) {
            MachineInstance rotor = rotor(use.block());
            if (rotor == null) return false;
            var context = rotor.context();
            var storage = context.storage("blades");
            try (var transaction = context.transactions().openOuter()) {
                if (storage.insert(PaperItems.toResource(use.item()).variant(), 1, transaction) == 1) {
                    transaction.commit();
                    if (use.player().getGameMode() != GameMode.CREATIVE) use.item().setAmount(use.item().getAmount() - 1);
                }
            }
            showBlades(use.player(), storage);
            return true;
        }
    }

    /** An induction port or reactor port clicked with the configurator: its next mode. */
    private static boolean cyclePortMode(Player player, MachineInstance member) {
        Key type = member.inspect().type();
        if (!MEMBERS.contains(type)) return false;
        List<String> modes = type.value().equals("induction_port") ? List.of("input", "output")
                : ReactorMachines.MODES.get(type.value());
        if (modes == null) return false; // side configuration, turning
        var context = member.context();
        String current = context.get(MemberBehavior.PORT_MODE);
        String mode = modes.get((modes.indexOf(current) + 1) % modes.size());
        context.set(MemberBehavior.PORT_MODE, mode);
        member.wake(WakeReason.CONFIG_CHANGED);
        player.sendActionBar(Component.text(type.value().replace('_', ' ') + ": ", NamedTextColor.GRAY)
                .append(Component.text(mode.replace('_', ' '), NamedTextColor.YELLOW)));
        return true;
    }

    /**
     * A transport: cycles how the clicked side connects (Mekanism's connection types). A container:
     * the transports touching it change the side that faces it (easier to hit than a pipe's face).
     */
    private static boolean cycleTransport(Player player, MachineBlocks blocks, Block block, WorldPosition position,
                                          @Nullable BlockFace face) {
        if (face == null) return false;
        Direction side;
        try {
            side = Direction.valueOf(face.name());
        } catch (IllegalArgumentException e) {
            return false;
        }
        var mode = blocks.cycleTransportSide(position, side);
        String what = mode == null ? null : face.name().toLowerCase(Locale.ROOT) + " side of the transport: ";
        if (mode == null && block.getState(false) instanceof Container) {
            for (Direction direction : Direction.values()) {
                WorldPosition neighbor = position.relative(direction);
                var changed = blocks.cycleTransportSide(neighbor, direction.opposite());
                if (changed != null && mode == null) mode = changed;
            }
            what = "transport side facing this container: ";
        }
        if (mode == null) return false;
        player.sendActionBar(Component.text(what, NamedTextColor.GRAY).append(Component.text(describe(mode), NamedTextColor.YELLOW)));
        return true;
    }

    /** A multiblock member: opens its structure's menu, or says it is not formed. */
    private static boolean openStructure(Player player, Block block) {
        WorldPosition position = PaperWorld.position(block);
        MachineInstance member = PaperWorld.machine(position);
        if (member == null || !MEMBERS.contains(member.inspect().type())) return false;
        var structure = Multiblocks.containing(position);
        MachineInstance anchor = structure == null ? null : PaperWorld.machine(structure.anchor());
        MachineMenus menus = MachineMenus.current();
        if (anchor == null || menus == null) {
            player.sendActionBar(Component.text("Not formed: build a closed box (3 to 18 blocks a side), casings on every edge",
                    NamedTextColor.RED));
            return true;
        }
        menus.open(player, anchor.position());
        return true;
    }

    private static @Nullable MachineInstance rotor(@Nullable Block block) {
        MachineInstance rotor = block == null ? null : PaperWorld.machine(block);
        return rotor != null && rotor.inspect().type().equals(MekanizedContent.key("turbine_rotor")) ? rotor : null;
    }

    /** Sneaking with an empty hand on a rotor: one blade comes back. */
    private static boolean takeBlade(Player player, Block block) {
        MachineInstance rotor = rotor(block);
        if (rotor == null) return false;
        var context = rotor.context();
        var storage = context.storage("blades");
        try (var transaction = context.transactions().openOuter()) {
            var held = storage.views().getFirst();
            var blade = held.isEmpty() ? null : held.variant();
            if (blade != null && storage.extract(blade, 1, transaction) == 1) {
                var stack = PaperItems.toItemStack(blade, 1);
                transaction.commit();
                if (stack != null) PaperWorld.give(player, stack);
            }
        }
        showBlades(player, storage);
        return true;
    }

    private static void showBlades(Player player, ResourceHandler storage) {
        long count = storage.views().stream().mapToLong(StorageView::amount).sum();
        player.sendActionBar(Component.text("Rotor blades: " + count + " / 2", NamedTextColor.YELLOW));
    }

    private static String describe(TransportSideMode mode) {
        return switch (mode) {
            case DEFAULT -> "default (machines in and out, containers filled)";
            case PULL -> "pull (takes from the block)";
            case PUSH -> "push (only fills the block)";
            case NONE -> "none (disconnected)";
        };
    }

    private static void install(Player player, ItemStack item, @Nullable MachineBlocks blocks, MachineInstance machine, String tier) {
        Key next = LogisticsMachines.upgrade(machine.inspect().type(), tier);
        if (next == null) {
            player.sendActionBar(Component.text("This " + tier + " tier installer does not fit this machine", NamedTextColor.RED));
            return;
        }
        if (blocks == null || !blocks.replaceBlock(machine.position(), next)) {
            player.sendActionBar(Component.text("The machine is busy; try again", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        player.sendActionBar(Component.text("Upgraded to " + next.value().replace('_', ' '), NamedTextColor.GREEN));
    }

    /** Turns the machine a quarter clockwise (it keeps what it holds; a running operation restarts). */
    private static void turn(Player player, @Nullable MachineBlocks blocks, MachineInstance machine) {
        Direction facing = machine.inspect().facing();
        int index = Arrays.asList(TURN).indexOf(facing);
        Direction next = TURN[(index + 1) % TURN.length];
        if (blocks == null || !blocks.replaceBlock(machine.position(), machine.inspect().type(), next)) {
            player.sendActionBar(Component.text("The machine is busy; try again", NamedTextColor.RED));
        }
    }
}
