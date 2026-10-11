package fr.robie.mekanized.paper.command;

import fr.robie.mekanized.paper.PaperWorld;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.machine.MachineAction;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.upgrade.UpgradeActions;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.mekanized.MekanizedContent;
import fr.robie.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.api.paper.PaperItems;
import fr.robie.paperdispatch.command.CommandResultType;
import fr.robie.paperdispatch.command.SubCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Server-side checks of engine features on Mekanized content, which headless tests cannot reach.
 * The checks that need no Mekanized content (drop, hopper, comparator, idle cost) are MachineEngine's
 * {@code /machineengine check}. Admin only; results go to the console log.
 * <ul>
 *   <li>{@code check-item-data}: items holding the old Bukkit persistent data read the same through
 *       CraftEngine item data, which then holds it (D32).</li>
 *   <li>{@code check-upgrades}: an enrichment chamber takes 8 of 10 speed upgrades (items with the
 *       {@code machineengine:upgrade} behavior) through its {@code upgrade} action, lists them, gives all 8
 *       back on a shift-click, and refuses a filter upgrade with a reason (D40).</li>
 *   <li>{@code check-modules}: module levels and the on/off switch on real gear (D40): fortune units raise the
 *       Meka-Tool's own Fortune and give it back when they go, an energy unit doubles a MekaSuit piece's
 *       capacity per level and cannot be switched off, and taking it out loses the energy over the capacity.</li>
 *   <li>{@code check-station}: a modification station with energy, a Meka-Tool and two fortune units installs
 *       both levels over time (Fortune II), then its module list takes one level back out (D40).</li>
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
                new PositionSubCommand(this.plugin, "check-block-options", this::checkBlockOptions),
                new PositionSubCommand(this.plugin, "check-upgrades", this::checkUpgrades),
                new PositionSubCommand(this.plugin, "check-station", this::checkStation),
                SubCommand.builder(this.plugin, "check-modules").permission(PositionSubCommand.ADMIN)
                        .executes(dispatch -> {
                            checkModules();
                            return CommandResultType.SUCCESS;
                        }).build(),
                SubCommand.builder(this.plugin, "check-item-data").permission(PositionSubCommand.ADMIN)
                        .executes(dispatch -> {
                            checkItemData();
                            return CommandResultType.SUCCESS;
                        }).build());
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

    private void checkStation(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        reset(world, x, y, z, 0, 0);
        if (!MekanizedDebugCommands.place(world, x, y, z, "modification_station")) {
            MekanizedCommands.reply(context, "check-station: could not place a modification station");
            return;
        }
        MachineInstance station = MachineEngine.get().machineAt(WorldPosition.of(world.getKey(), x, y, z)).orElse(null);
        ItemStack tool = PaperItems.itemOf(MekanizedContent.key("meka_tool"));
        ItemStack fortune = PaperItems.itemOf(MekanizedContent.key("module_fortune_unit"));
        if (station == null || tool == null || fortune == null) {
            log("check-station: the station or the items are missing");
            return;
        }
        long[] in = new long[3];
        try (Transaction transaction = station.context().transactions().openOuter()) {
            in[0] = station.context().storage("gear").insert(PaperItems.toResource(tool).variant(), 1, transaction);
            in[1] = station.context().storage("module").insert(PaperItems.toResource(fortune).variant(), 2, transaction);
            in[2] = station.context().storage("energy").insert(MekanizedContent.ENERGY.variant(), 40_000, transaction);
            transaction.commit();
        }
        station.wake(fr.robie.machineengine.api.machine.WakeReason.INPUT_CHANGED);
        MekanizedCommands.reply(context, "check-station: installing; checking in 6 s");
        PaperWorld.scheduler().runLater(new Location(world, x, y, z), () -> {
            List<java.util.Map<String, String>> rows = station.list(fr.robie.mekanized.machine.ModificationStation.LIST);
            String listed = rows.isEmpty() ? "none" : rows.getFirst().get("name") + " " + rows.getFirst().get("level") + "/" + rows.getFirst().get("max");
            int enchanted = enchantment(station);
            MachineAction.Outcome take = station.runAction(fr.robie.mekanized.machine.ModificationStation.ACTION,
                    new MachineAction.Click(new java.util.UUID(0, 0), MachineAction.ClickKind.LEFT, 0, null));
            log("check-station: put in " + java.util.Arrays.toString(in) + " (expected [1, 2, 40000]), listed " + listed
                    + " (expected fortune_unit 2/3), Fortune " + enchanted + " (expected 2), status '"
                    + station.context().get(fr.robie.mekanized.machine.ModificationStation.STATUS)
                    + "', took out " + take.give().stream().mapToLong(ResourceStack::amount).sum() + " (expected 1), then Fortune "
                    + enchantment(station) + " (expected 1)");
        }, 120);
    }

    /** The Fortune level of the gear in a station's gear slot. */
    private static int enchantment(MachineInstance station) {
        for (StorageView view : station.context().storage("gear").views()) {
            if (view.isEmpty()) continue;
            ItemStack gear = PaperItems.toItemStack(view.variant(), 1);
            org.bukkit.enchantments.Enchantment fortune = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("fortune"));
            return gear == null || fortune == null ? -1 : gear.getEnchantmentLevel(fortune);
        }
        return -1;
    }

    /**
     * Clears the blocks from {@code (x, y, z)} to {@code (x + dx, y, z + dz)} and the items lying around,
     * so a check can run again at the same place: CraftEngine does not place a block over the same block.
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

    private void checkModules() {
        ItemStack tool = PaperItems.itemOf(MekanizedContent.key("meka_tool"));
        ItemStack chest = PaperItems.itemOf(MekanizedContent.key("mekasuit_bodyarmor"));
        var fortune = fr.robie.mekanized.paper.gear.GearBehaviors.module("fortune_unit");
        var energyUnit = fr.robie.mekanized.paper.gear.GearBehaviors.module("energy_unit");
        if (tool == null || chest == null || fortune == null || energyUnit == null) {
            log("check-modules: the gear or the module items are missing");
            return;
        }
        org.bukkit.enchantments.Enchantment fortuneEnchantment = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft("fortune"));
        tool.addUnsafeEnchantment(fortuneEnchantment, 1); // the tool's own Fortune I
        List<Object> levels = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) levels.add(fr.robie.mekanized.paper.gear.GearModules.install(tool, fortune));
        int installed = tool.getEnchantmentLevel(fortuneEnchantment);
        fr.robie.mekanized.paper.gear.GearModules.toggle(tool, "fortune_unit");
        int off = tool.getEnchantmentLevel(fortuneEnchantment);
        fr.robie.mekanized.paper.gear.GearModules.toggle(tool, "fortune_unit");
        fr.robie.mekanized.paper.gear.GearModules.remove(tool, "fortune_unit", 1);
        int two = tool.getEnchantmentLevel(fortuneEnchantment);
        int removed = fr.robie.mekanized.paper.gear.GearModules.remove(tool, "fortune_unit", 9);
        int gone = tool.getEnchantmentLevel(fortuneEnchantment);
        log("check-modules: fortune units " + levels + " (expected [1, 2, 3, null]), Fortune " + installed + " (expected 3), off "
                + off + " (expected 1, the tool's own), after one out " + two + " (expected 2), " + removed + " more out (expected 2), then "
                + gone + " (expected 1)");

        long base = fr.robie.machineengine.api.paper.PaperItemResources.capacity(chest, fr.robie.machineengine.api.resource.Energy.KEY);
        fr.robie.mekanized.paper.gear.GearModules.install(chest, energyUnit);
        fr.robie.mekanized.paper.gear.GearModules.install(chest, energyUnit);
        long four = fr.robie.machineengine.api.paper.PaperItemResources.capacity(chest, fr.robie.machineengine.api.resource.Energy.KEY);
        boolean toggled = fr.robie.mekanized.paper.gear.GearModules.toggle(chest, "energy_unit");
        fr.robie.machineengine.api.paper.PaperItemResources.withAmount(chest, fr.robie.machineengine.api.resource.Energy.KEY, four);
        fr.robie.mekanized.paper.gear.GearModules.remove(chest, "energy_unit", 1);
        long left = fr.robie.machineengine.api.paper.PaperItemResources.amount(chest, fr.robie.machineengine.api.resource.Energy.KEY);
        log("check-modules: energy units: capacity " + base + " then " + four + " (expected x4), switched off " + toggled
                + " (expected false), full then one out: " + left + " (expected " + base * 2 + "), modules "
                + fr.robie.mekanized.paper.ItemData.getStrings(chest, fr.robie.mekanized.paper.ItemData.Field.MODULES)
                + " (expected [energy_unit:1])");
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
        var installed = fr.robie.mekanized.paper.ItemData.getStrings(tool, fr.robie.mekanized.paper.ItemData.Field.MODULES);
        String text = fr.robie.mekanized.paper.ItemData.getString(cardItem, fr.robie.mekanized.paper.ItemData.Field.CONFIGURATION);
        long amount = fr.robie.mekanized.paper.ItemData.getLong(cardItem, fr.robie.mekanized.paper.ItemData.Field.DROPPER_AMOUNT);
        byte[] bytes = fr.robie.mekanized.paper.ItemData.getBytes(cardItem, fr.robie.mekanized.paper.ItemData.Field.BOX_ITEMS);
        log("check-item-data: energy " + joules + " (expected 5000), capacity " + capacity + " (expected 32000000, energy unit x2), modules "
                + installed + " (expected [vein_mining_unit, energy_unit]), card '" + text + "' (expected 'old card'), dropper " + amount
                + " (expected 42), box " + java.util.Arrays.toString(bytes) + " (expected [1, 2, 3])");
        // Writing the new way: kept, and the old persistent data is gone.
        fr.robie.machineengine.api.paper.PaperItemResources.withAmount(tool, fr.robie.machineengine.api.resource.Energy.KEY, 7000);
        fr.robie.mekanized.paper.ItemData.setStrings(tool, fr.robie.mekanized.paper.ItemData.Field.MODULES,
                java.util.List.of("silk_touch_unit"));
        boolean oldGone = !tool.getItemMeta().getPersistentDataContainer().has(energy) && !tool.getItemMeta().getPersistentDataContainer().has(modules);
        log("check-item-data: after writing, energy " + fr.robie.machineengine.api.paper.PaperItemResources.amount(tool,
                fr.robie.machineengine.api.resource.Energy.KEY) + " (expected 7000), modules "
                + fr.robie.mekanized.paper.ItemData.getStrings(tool, fr.robie.mekanized.paper.ItemData.Field.MODULES)
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
