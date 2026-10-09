package fr.robie.machineengine.mekanized.paper.command;

import fr.robie.machineengine.mekanized.paper.PaperWorld;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.machine.MachineAction;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.upgrade.UpgradeActions;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.api.paper.PaperItems;
import fr.robie.paperdispatch.command.CommandResultType;
import fr.robie.paperdispatch.command.SubCommand;
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

import java.util.List;

/**
 * Server-side checks of engine features that headless tests cannot reach (vanilla blocks, drops,
 * redstone), run on a basic bin. They replace the removed example addon's {@code /mexample} checks.
 * Admin only; results go to the console log.
 * <ul>
 *   <li>{@code check-drop}: a bin with 100 iron ingots broken without a player drops them all (§20.4).</li>
 *   <li>{@code check-hopper}: hopper (10 iron) → bin → hopper → chest; all 10 end in the chest.</li>
 *   <li>{@code check-comparator}: a comparator reading a half-full bin gives 8, an empty one 0.</li>
 *   <li>{@code fill <size>}: a size×size grid of idle crushers, to measure the idle cost.</li>
 *   <li>{@code check-item-data}: items holding the old Bukkit persistent data read the same through
 *       CraftEngine item data, which then holds it (D32).</li>
 *   <li>{@code check-upgrades}: an enrichment chamber takes 8 of 10 speed upgrades (items with the
 *       {@code machineengine:upgrade} behavior) through its {@code upgrade} action, lists them, gives all 8
 *       back on a shift-click, and refuses a filter upgrade with a reason (D40).</li>
 *   <li>{@code check-block-options}: a basic bin, basic energy cube, chargepad and basic universal cable in a
 *       row from the position (placed if missing, kept otherwise), and what their machines read from their
 *       block configs (D34): the bin's capacity, the cube's output rate, the chargepad's charge per tick. The
 *       cable's rate is shown by {@code /machineengine network}. Run again after {@code /ce reload} on the same
 *       row to see whether loaded machines follow.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
final class EngineCheckCommands {
    private static final String BIN = "basic_bin";

    private final Plugin plugin;

    EngineCheckCommands(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The checks. */
    List<SubCommand<Plugin>> commands() {
        return List.of(
                new PositionSubCommand(this.plugin, "check-drop", this::checkDrop),
                new PositionSubCommand(this.plugin, "check-hopper", this::checkHopper),
                new PositionSubCommand(this.plugin, "check-comparator", this::checkComparator),
                new PositionSubCommand(this.plugin, "check-block-options", this::checkBlockOptions),
                new PositionSubCommand(this.plugin, "check-upgrades", this::checkUpgrades),
                SubCommand.builder(this.plugin, "check-item-data").permission(PositionSubCommand.ADMIN)
                        .executes(dispatch -> {
                            checkItemData();
                            return CommandResultType.SUCCESS;
                        }).build(),
                new PositionSubCommand(this.plugin, "fill", this::fill,
                        Commands.argument("size", IntegerArgumentType.integer(1, 200))));
    }

    private void checkDrop(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 0, 0);
        ResourceHandler port = placeBin(context, "check-drop", world, x, y, z);
        if (port == null) return;
        ResourceStack iron = PaperItems.toResource(new ItemStack(Material.IRON_INGOT, 100));
        long inserted = move(port, iron, true);
        CraftEngineBlocks.remove(world.getBlockAt(x, y, z));
        PaperWorld.scheduler().runLater(new Location(world, x, y, z), () -> {
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
        PaperWorld.scheduler().runLater(new Location(world, x, y, z), () -> {
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
        PaperWorld.scheduler().runLater(at, () -> {
            int halfPower = ((RedstoneWire) world.getBlockAt(x + 2, y, z).getBlockData()).getPower();
            move(port, iron, false);
            PaperWorld.scheduler().runLater(at, () -> {
                int emptyPower = ((RedstoneWire) world.getBlockAt(x + 2, y, z).getBlockData()).getPower();
                log("check-comparator: half full " + halfPower + " (expected 8), empty " + emptyPower + " (expected 0)");
            }, 10);
        }, 10);
    }

    private void checkBlockOptions(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        String[] blocks = {BIN, "basic_energy_cube", "chargepad", "basic_universal_cable"};
        MachineInstance[] machines = new MachineInstance[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            Block block = world.getBlockAt(x + 2 * i, y, z);
            boolean placed = false;
            if (!CraftEngineBlocks.isCustomBlock(block)) {
                if (!MekanizedDebugCommands.place(world, x + 2 * i, y, z, blocks[i])) {
                    MekanizedCommands.reply(context, "check-block-options: could not place a " + blocks[i]);
                    return;
                }
                placed = true;
            }
            machines[i] = MachineEngine.get().machineAt(WorldPosition.of(world.getKey(), x + 2 * i, y, z)).orElse(null);
            log("check-block-options: " + blocks[i] + " at " + (x + 2 * i) + " " + y + " " + z + (placed ? " placed" : " already there"));
        }
        if (machines[0] == null || machines[1] == null || machines[2] == null) {
            log("check-block-options: a machine is missing");
            return;
        }
        long capacity = machines[0].inspect().storages().get("items").getFirst().capacity();
        long rate = machines[1].context().options().portRate("out", -1);
        long charge = machines[2].context().options().get(UtilityMachines.CHARGE_PER_TICK);
        log("check-block-options: bin capacity " + capacity + ", energy cube out rate " + rate + ", chargepad charge-per-tick " + charge
                + " (shipped: 4096, 4000, 51200)");
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
    private void checkUpgrades(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 0, 0);
        if (!MekanizedDebugCommands.place(world, x, y, z, "enrichment_chamber")) {
            MekanizedCommands.reply(context, "check-upgrades: could not place an enrichment chamber");
            return;
        }
        MachineInstance chamber = MachineEngine.get().machineAt(WorldPosition.of(world.getKey(), x, y, z)).orElse(null);
        ItemStack speed = PaperItems.itemOf(MekanizedContent.key("upgrade_speed"));
        ItemStack filter = PaperItems.itemOf(MekanizedContent.key("upgrade_filter"));
        if (chamber == null || speed == null || filter == null) {
            log("check-upgrades: the chamber or the upgrade items are missing");
            return;
        }
        java.util.UUID nobody = new java.util.UUID(0, 0);
        MachineAction.Outcome install = chamber.runAction(UpgradeActions.ACTION, new MachineAction.Click(nobody,
                MachineAction.ClickKind.LEFT, 0, PaperItems.toResource(speed.asQuantity(10))));
        List<java.util.Map<String, String>> rows = chamber.list(UpgradeActions.LIST);
        int row = 0;
        while (row < rows.size() && !rows.get(row).get("id").equals("mekanized:upgrade_speed")) row++;
        String listed = row < rows.size() ? rows.get(row).get("count") + "/" + rows.get(row).get("max") + " item " + rows.get(row).get("item") : "missing";
        MachineAction.Outcome take = chamber.runAction(UpgradeActions.ACTION, new MachineAction.Click(nobody,
                MachineAction.ClickKind.SHIFT, row, null));
        String given = take.give().stream().map(stack -> stack.amount() + " " + PaperItems.idOf(PaperItems.toItemStacks(stack).getFirst()).asString())
                .reduce((a, b) -> a + ", " + b).orElse("nothing");
        MachineAction.Outcome refused = chamber.runAction(UpgradeActions.ACTION, new MachineAction.Click(nobody,
                MachineAction.ClickKind.LEFT, 0, PaperItems.toResource(filter)));
        String reason = refused.message() == null ? "none"
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(refused.message());
        log("check-upgrades: installed " + install.used() + " (expected 8), listed " + listed
                + " (expected 8/8 item mekanized:upgrade_speed), gave back " + given + " (expected 8 mekanized:upgrade_speed), "
                + rows.size() + " rows (expected 3: speed, energy, anchor), filter refused " + !refused.done() + " (expected true): " + reason);
    }

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

    /** Old items: values written as Bukkit persistent data, as Mekanized did before D32, then read the new way. */
    private void checkItemData() {
        org.bukkit.NamespacedKey energy = new org.bukkit.NamespacedKey(MekanizedContent.NAMESPACE, "energy");
        org.bukkit.NamespacedKey modules = new org.bukkit.NamespacedKey(MekanizedContent.NAMESPACE, "modules");
        org.bukkit.NamespacedKey card = new org.bukkit.NamespacedKey(MekanizedContent.NAMESPACE, "configuration");
        org.bukkit.NamespacedKey dropper = new org.bukkit.NamespacedKey(MekanizedContent.NAMESPACE, "dropper_amount");
        org.bukkit.NamespacedKey box = new org.bukkit.NamespacedKey(MekanizedContent.NAMESPACE, "box_items");
        ItemStack tool = PaperItems.itemOf(MekanizedContent.key("meka_tool"));
        ItemStack cardItem = PaperItems.itemOf(MekanizedContent.key("configuration_card"));
        if (tool == null || cardItem == null) {
            log("check-item-data: Mekanized items are not loaded");
            return;
        }
        tool.editMeta(meta -> {
            meta.getPersistentDataContainer().set(energy, org.bukkit.persistence.PersistentDataType.LONG, 5000L);
            meta.getPersistentDataContainer().set(modules, org.bukkit.persistence.PersistentDataType.STRING, "vein_mining_unit,energy_unit");
        });
        cardItem.editMeta(meta -> {
            meta.getPersistentDataContainer().set(card, org.bukkit.persistence.PersistentDataType.STRING, "old card");
            meta.getPersistentDataContainer().set(dropper, org.bukkit.persistence.PersistentDataType.LONG, 42L);
            meta.getPersistentDataContainer().set(box, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY, new byte[] {1, 2, 3});
        });
        long joules = fr.robie.machineengine.api.paper.PaperItemResources.amount(tool, fr.robie.machineengine.api.resource.Energy.KEY);
        long capacity = fr.robie.machineengine.api.paper.PaperItemResources.capacity(tool, fr.robie.machineengine.api.resource.Energy.KEY);
        var installed = fr.robie.machineengine.mekanized.paper.ItemData.getStrings(tool, fr.robie.machineengine.mekanized.paper.ItemData.Field.MODULES);
        String text = fr.robie.machineengine.mekanized.paper.ItemData.getString(cardItem, fr.robie.machineengine.mekanized.paper.ItemData.Field.CONFIGURATION);
        long amount = fr.robie.machineengine.mekanized.paper.ItemData.getLong(cardItem, fr.robie.machineengine.mekanized.paper.ItemData.Field.DROPPER_AMOUNT);
        byte[] bytes = fr.robie.machineengine.mekanized.paper.ItemData.getBytes(cardItem, fr.robie.machineengine.mekanized.paper.ItemData.Field.BOX_ITEMS);
        log("check-item-data: energy " + joules + " (expected 5000), capacity " + capacity + " (expected 64000000, energy unit x4), modules "
                + installed + " (expected [vein_mining_unit, energy_unit]), card '" + text + "' (expected 'old card'), dropper " + amount
                + " (expected 42), box " + java.util.Arrays.toString(bytes) + " (expected [1, 2, 3])");
        // Writing the new way: kept, and the old persistent data is gone.
        fr.robie.machineengine.api.paper.PaperItemResources.withAmount(tool, fr.robie.machineengine.api.resource.Energy.KEY, 7000);
        fr.robie.machineengine.mekanized.paper.ItemData.setStrings(tool, fr.robie.machineengine.mekanized.paper.ItemData.Field.MODULES,
                java.util.List.of("silk_touch_unit"));
        boolean oldGone = !tool.getItemMeta().getPersistentDataContainer().has(energy) && !tool.getItemMeta().getPersistentDataContainer().has(modules);
        log("check-item-data: after writing, energy " + fr.robie.machineengine.api.paper.PaperItemResources.amount(tool,
                fr.robie.machineengine.api.resource.Energy.KEY) + " (expected 7000), modules "
                + fr.robie.machineengine.mekanized.paper.ItemData.getStrings(tool, fr.robie.machineengine.mekanized.paper.ItemData.Field.MODULES)
                + " (expected [silk_touch_unit]), old persistent data gone: " + oldGone + " (expected true)");
        // The lore a player gets: the item's client-bound processors, run on a copy as when it is sent.
        var item = net.momirealms.craftengine.bukkit.api.BukkitAdaptor.adapt(tool).copy();
        var definition = item.getDefinitionOrNull();
        if (definition == null) {
            log("check-item-data: the Meka-Tool has no CraftEngine definition");
            return;
        }
        var context = net.momirealms.craftengine.core.item.ItemBuildContext.of(null, item,
                net.momirealms.craftengine.core.plugin.context.ContextHolder.empty());
        for (var processor : definition.clientBoundProcessors()) processor.apply(context);
        log("check-item-data: lore sent " + context.item().loreJson().map(Object::toString).orElse("none")
                + " (expected a silk touch unit line, then energy 7,000 / 16,000,000 J)");
    }

    private void log(String message) {
        this.plugin.getLogger().info(message);
    }
}
