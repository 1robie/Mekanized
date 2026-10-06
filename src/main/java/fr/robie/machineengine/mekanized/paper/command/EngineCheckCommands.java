package fr.robie.machineengine.mekanized.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.paper.PaperItems;
import fr.robie.machineengine.paper.PositionArguments;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Server-side checks of engine features that headless tests cannot reach (vanilla blocks, drops,
 * redstone), run on a basic bin. They replace the removed example addon's {@code /mexample} checks.
 * Admin only; results go to the console log.
 * <ul>
 *   <li>{@code check-drop}: a bin with 100 iron ingots broken without a player drops them all (§20.4).</li>
 *   <li>{@code check-hopper}: hopper (10 iron) → bin → hopper → chest; all 10 end in the chest.</li>
 *   <li>{@code check-comparator}: a comparator reading a half-full bin gives 8, an empty one 0.</li>
 *   <li>{@code fill <size>}: a size×size grid of idle crushers, to measure the idle cost.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
final class EngineCheckCommands {
    private static final String BIN = "basic_bin";

    private final Plugin plugin;

    EngineCheckCommands(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Adds the checks to {@code root}. */
    void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root
                .then(PositionArguments.optionalPosition(Commands.literal("check-drop").requires(EngineCheckCommands::admin),
                        PositionArguments.Default.PLACEMENT, onRegion(this::checkDrop)))
                .then(PositionArguments.optionalPosition(Commands.literal("check-hopper").requires(EngineCheckCommands::admin),
                        PositionArguments.Default.PLACEMENT, onRegion(this::checkHopper)))
                .then(PositionArguments.optionalPosition(Commands.literal("check-comparator").requires(EngineCheckCommands::admin),
                        PositionArguments.Default.PLACEMENT, onRegion(this::checkComparator)))
                .then(Commands.literal("fill").requires(EngineCheckCommands::admin)
                        .then(PositionArguments.optionalPosition(Commands.argument("size", IntegerArgumentType.integer(1, 200)),
                                PositionArguments.Default.PLACEMENT, onRegion(this::fill))));
    }

    private static boolean admin(CommandSourceStack source) {
        return source.getSender().hasPermission("machineengine.admin");
    }

    private interface Check {
        void run(CommandContext<CommandSourceStack> context, World world, int x, int y, int z);
    }

    /** Runs {@code check} on the thread owning the target position, because it touches the world there. */
    private PositionArguments.PositionCommand onRegion(Check check) {
        return (context, world, x, y, z) -> {
            Bukkit.getRegionScheduler().execute(this.plugin, new Location(world, x, y, z), () -> check.run(context, world, x, y, z));
            return Command.SINGLE_SUCCESS;
        };
    }

    private void checkDrop(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 0, 0);
        ResourceHandler port = placeBin(context, "check-drop", world, x, y, z);
        if (port == null) return;
        ResourceStack iron = PaperItems.toResource(new ItemStack(Material.IRON_INGOT, 100));
        long inserted = move(port, iron, true);
        CraftEngineBlocks.remove(world.getBlockAt(x, y, z));
        Bukkit.getRegionScheduler().runDelayed(this.plugin, new Location(world, x, y, z), task -> {
            int dropped = world.getNearbyEntitiesByType(Item.class, new Location(world, x + 0.5, y + 0.5, z + 0.5), 3).stream()
                    .filter(item -> item.getItemStack().getType() == Material.IRON_INGOT)
                    .mapToInt(item -> item.getItemStack().getAmount()).sum();
            log("check-drop: inserted " + inserted + " iron ingots, " + dropped + " dropped after breaking the bin (expected 100)");
        }, 5);
    }

    private void checkHopper(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 0, 0);
        for (int dy : new int[] {-2, -1, 1}) reset(world, x, y + dy, z, 0, 0);
        world.getBlockAt(x, y - 2, z).setType(Material.CHEST);
        world.getBlockAt(x, y - 1, z).setBlockData(Bukkit.createBlockData("minecraft:hopper[facing=down]"));
        if (placeBin(context, "check-hopper", world, x, y, z) == null) return;
        world.getBlockAt(x, y + 1, z).setBlockData(Bukkit.createBlockData("minecraft:hopper[facing=down]"));
        ((Hopper) world.getBlockAt(x, y + 1, z).getState(false)).getInventory().addItem(new ItemStack(Material.IRON_INGOT, 10));
        MekanizedCommands.reply(context, "check-hopper: hopper (10 iron) > bin > hopper > chest; counting in 15 s");
        Bukkit.getRegionScheduler().runDelayed(this.plugin, new Location(world, x, y, z), task -> {
            int top = ironIn(((Hopper) world.getBlockAt(x, y + 1, z).getState(false)).getInventory());
            int bottom = ironIn(((Hopper) world.getBlockAt(x, y - 1, z).getState(false)).getInventory());
            int chest = ironIn(((Chest) world.getBlockAt(x, y - 2, z).getState(false)).getInventory());
            MachineInstance bin = MachineEngine.get().machineAt(WorldPosition.of(world.getKey(), x, y, z)).orElse(null);
            long inBin = bin == null ? -1 : bin.inspect().storages().get("items").stream().mapToLong(StorageView::amount).sum();
            log("check-hopper: top hopper " + top + ", bin " + inBin + ", bottom hopper " + bottom + ", chest " + chest
                    + ", total " + (top + inBin + bottom + chest) + " (expected 10, all in the chest)");
        }, 300);
    }

    /** Bin, then a comparator reading it (east of it), then redstone dust. */
    private void checkComparator(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 2, 0);
        for (int dx = 0; dx <= 2; dx++) world.getBlockAt(x + dx, y - 1, z).setType(Material.STONE);
        ResourceHandler port = placeBin(context, "check-comparator", world, x, y, z);
        if (port == null) return;
        world.getBlockAt(x + 1, y, z).setBlockData(Bukkit.createBlockData("minecraft:comparator[facing=west]"));
        world.getBlockAt(x + 2, y, z).setType(Material.REDSTONE_WIRE);
        // Half of the basic bin's single 4096-item slot.
        ResourceStack iron = PaperItems.toResource(new ItemStack(Material.IRON_INGOT, 2_048));
        move(port, iron, true);
        Location at = new Location(world, x, y, z);
        Bukkit.getRegionScheduler().runDelayed(this.plugin, at, half -> {
            int halfPower = ((RedstoneWire) world.getBlockAt(x + 2, y, z).getBlockData()).getPower();
            move(port, iron, false);
            Bukkit.getRegionScheduler().runDelayed(this.plugin, at, empty -> {
                int emptyPower = ((RedstoneWire) world.getBlockAt(x + 2, y, z).getBlockData()).getPower();
                log("check-comparator: half full " + halfPower + " (expected 8), empty " + emptyPower + " (expected 0)");
            }, 10);
        }, 10);
    }

    private void fill(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        int size = IntegerArgumentType.getInteger(context, "size");
        int placed = 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                if (MekanizedDebugCommands.place(world, x + dx, y, z + dz, "crusher")) placed++;
            }
        }
        MekanizedCommands.reply(context, "fill: placed " + placed + " idle crushers");
    }

    /**
     * Clears the blocks from {@code (x, y, z)} to {@code (x + dx, y, z + dz)} and the items lying around,
     * so a check can run again at the same place: CraftEngine does not place a block over the same
     * block, and leftover hoppers, chests or drops would skew the counts.
     */
    private static void reset(World world, int x, int y, int z, int dx, int dz) {
        for (int i = 0; i <= dx; i++) {
            for (int j = 0; j <= dz; j++) {
                Block block = world.getBlockAt(x + i, y, z + j);
                if (CraftEngineBlocks.isCustomBlock(block)) CraftEngineBlocks.remove(block);
                if (block.getState(false) instanceof Container container) container.getInventory().clear();
                block.setType(Material.AIR);
            }
        }
        world.getNearbyEntitiesByType(Item.class, new Location(world, x + 0.5, y + 0.5, z + 0.5), 4).forEach(Item::remove);
    }

    /** Places a basic bin and returns its item port, or null (and says why) if it failed. */
    private static ResourceHandler placeBin(CommandContext<CommandSourceStack> context, String check, World world, int x, int y, int z) {
        if (!MekanizedDebugCommands.place(world, x, y, z, BIN)) {
            MekanizedCommands.reply(context, check + ": could not place a " + MekanizedContent.NAMESPACE + ":" + BIN);
            return null;
        }
        MachineInstance bin = MachineEngine.get().machineAt(WorldPosition.of(world.getKey(), x, y, z)).orElse(null);
        if (bin == null) {
            MekanizedCommands.reply(context, check + ": the " + BIN + " was placed but holds no machine");
            return null;
        }
        return bin.capability(Capabilities.RESOURCE_HANDLER, Direction.UP).orElseThrow();
    }

    /** Inserts or extracts {@code stack} through {@code port} in one transaction; returns the amount moved. */
    private static long move(ResourceHandler port, ResourceStack stack, boolean insert) {
        try (Transaction transaction = MachineEngine.get().transactions().openOuter()) {
            long moved = insert ? port.insert(stack.variant(), stack.amount(), transaction)
                    : port.extract(stack.variant(), stack.amount(), transaction);
            transaction.commit();
            return moved;
        }
    }

    private static int ironIn(Inventory inventory) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == Material.IRON_INGOT) total += stack.getAmount();
        }
        return total;
    }

    private void log(String message) {
        this.plugin.getLogger().info(message);
    }
}
