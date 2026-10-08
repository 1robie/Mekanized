package fr.robie.machineengine.mekanized.paper.command;

import fr.robie.machineengine.mekanized.paper.PaperWorld;
import com.mojang.brigadier.context.CommandContext;
import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineInstance;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.api.paper.PaperItems;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import fr.robie.paperdispatch.command.SubCommand;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * {@code /mekanized test [<x y z>] [world]} (admin): places an enrichment chamber, gives it energy
 * and one osmium ore, and reports the dust after the process (2 expected). A server-side check of
 * the whole chain: CraftEngine block, machine, recipe and tags, energy. {@code test-cable}
 * and {@code test-chemistry} check energy and chemical transport the same way; {@code place-set} places
 * a crusher, an energy cube and a cable.
 */
@SuppressWarnings("UnstableApiUsage")
final class MekanizedDebugCommands {
    private final Plugin plugin;

    MekanizedDebugCommands(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The admin test commands. */
    List<SubCommand<Plugin>> commands() {
        return List.of(
                new PositionSubCommand(this.plugin, "test", this::test),
                new PositionSubCommand(this.plugin, "place-set", (context, world, x, y, z) -> {
                    boolean placed = place(world, x, y, z, "crusher") && place(world, x + 1, y, z, "basic_energy_cube")
                            && place(world, x + 2, y, z, "basic_universal_cable");
                    MekanizedCommands.reply(context, "mekanized place-set: " + (placed ? "crusher, energy cube, cable placed" : "placement failed"));
                }),
                new PositionSubCommand(this.plugin, "test-chemistry", this::testChemistry),
                new PositionSubCommand(this.plugin, "test-cable", this::testCable));
    }

    private void test(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        Location at = new Location(world, x, y, z);
        if (!CraftEngineBlocks.place(at, Key.of(MekanizedContent.NAMESPACE, "enrichment_chamber"), false)) {
            MekanizedCommands.reply(context, "mekanized test: could not place the enrichment chamber (is the Mekanized pack loaded?)");
            return;
        }
        WorldPosition position = WorldPosition.of(world.getKey(), x, y, z);
        MachineInstance chamber = MachineEngine.get().machineAt(position).orElse(null);
        ItemStack ore = PaperItems.itemOf(MekanizedContent.key("osmium_ore"));
        if (chamber == null || ore == null) {
            MekanizedCommands.reply(context, "mekanized test: no machine or no osmium ore item");
            return;
        }
        ResourceHandler port = chamber.capability(Capabilities.RESOURCE_HANDLER, Direction.UP).orElseThrow();
        try (Transaction transaction = MachineEngine.get().transactions().openOuter()) {
            port.insert(MekanizedContent.ENERGY.variant(), 20_000, transaction);
            ResourceStack resource = PaperItems.toResource(ore);
            port.insert(resource.variant(), 1, transaction);
            transaction.commit();
        }
        MekanizedCommands.reply(context, "mekanized test: enrichment chamber fed with 20 kJ and 1 osmium ore; result in 11 s");
        PaperWorld.scheduler().runLater(at, () -> {
            MachineInstance machine = MachineEngine.get().machineAt(position).orElse(null);
            if (machine == null) return;
            long dust = 0;
            long energy = 0;
            for (StorageView view : machine.inspect().storages().get("output")) {
                ItemData data = view.variant() == null ? null : view.variant().components().get(Items.DATA);
                if (data != null && data.item().equals(MekanizedContent.key("dust_osmium"))) dust += view.amount();
            }
            for (StorageView view : machine.inspect().storages().get("energy")) energy += view.amount();
            this.plugin.getLogger().info("mekanized test: " + dust + " osmium dust (expected 2), " + energy + " J left (expected 10000)");
        }, 220);
    }

    /**
     * A creative energy cube (front facing north) powers an enrichment chamber through one universal
     * cable: the chamber only gets energy through the cable. Reports the dust and the cable's arms.
     */
    private void testCable(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        boolean placed = place(world, x, y, z, "enrichment_chamber") && place(world, x, y, z + 1, "basic_universal_cable")
                && place(world, x, y, z + 2, "creative_energy_cube");
        if (!placed) {
            MekanizedCommands.reply(context, "mekanized test-cable: placement failed");
            return;
        }
        WorldPosition chamberAt = WorldPosition.of(world.getKey(), x, y, z);
        MachineInstance chamber = MachineEngine.get().machineAt(chamberAt).orElse(null);
        ItemStack ore = PaperItems.itemOf(MekanizedContent.key("osmium_ore"));
        if (chamber == null || ore == null) return;
        ResourceHandler port = chamber.capability(Capabilities.RESOURCE_HANDLER, Direction.UP).orElseThrow();
        try (Transaction transaction = MachineEngine.get().transactions().openOuter()) {
            port.insert(PaperItems.toResource(ore).variant(), 1, transaction);
            transaction.commit();
        }
        MekanizedCommands.reply(context, "mekanized test-cable: cube > cable > chamber with 1 osmium ore; result in 12 s");
        PaperWorld.scheduler().runLater(new Location(world, x, y, z), () -> {
            MachineInstance machine = MachineEngine.get().machineAt(chamberAt).orElse(null);
            if (machine == null) return;
            long dust = 0;
            for (StorageView view : machine.inspect().storages().get("output")) {
                ItemData data = view.variant() == null ? null : view.variant().components().get(Items.DATA);
                if (data != null && data.item().equals(MekanizedContent.key("dust_osmium"))) dust += view.amount();
            }
            this.plugin.getLogger().info("mekanized test-cable: " + dust + " osmium dust (expected 2), powered through the cable");
        }, 240);
    }

    /**
     * An electrolytic separator splits 1000 mB of water; a pressurized tube (east) carries the
     * hydrogen to a chemical tank. Reports what the tank and the separator hold.
     */
    private void testChemistry(CommandContext<CommandSourceStack> context, World world, int x, int y, int z) {
        boolean placed = place(world, x, y, z, "electrolytic_separator") && place(world, x + 1, y, z, "basic_pressurized_tube")
                && place(world, x + 2, y, z, "basic_chemical_tank");
        WorldPosition separatorAt = WorldPosition.of(world.getKey(), x, y, z);
        MachineInstance separator = MachineEngine.get().machineAt(separatorAt).orElse(null);
        var water = MekanizedContent.fluid("water");
        if (!placed || separator == null || water == null) {
            MekanizedCommands.reply(context, "mekanized test-chemistry: placement failed");
            return;
        }
        ResourceHandler port = separator.capability(Capabilities.RESOURCE_HANDLER, Direction.UP).orElseThrow();
        try (Transaction transaction = MachineEngine.get().transactions().openOuter()) {
            port.insert(MekanizedContent.ENERGY.variant(), 160_000, transaction);
            port.insert(water.variant(), 1_000, transaction);
            transaction.commit();
        }
        MekanizedCommands.reply(context, "mekanized test-chemistry: separator > tube > chemical tank with 1000 mB of water; result in 30 s");
        PaperWorld.scheduler().runLater(new Location(world, x, y, z), () -> {
            WorldPosition tankAt = WorldPosition.of(world.getKey(), x + 2, y, z);
            this.plugin.getLogger().info("mekanized test-chemistry: tank " + contents(tankAt, "chemical") + ", separator "
                    + contents(separatorAt, "output") + " (expected 1000 mB hydrogen and 500 mB oxygen in all)");
        }, 600);
    }

    private static String contents(WorldPosition position, String storage) {
        MachineInstance machine = MachineEngine.get().machineAt(position).orElse(null);
        if (machine == null) return "(gone)";
        StringBuilder text = new StringBuilder();
        for (StorageView view : machine.inspect().storages().get(storage)) {
            if (view.isEmpty()) continue;
            if (!text.isEmpty()) text.append(", ");
            text.append(view.amount()).append(" mB ").append(view.variant().type().key().value());
        }
        return text.isEmpty() ? "empty" : text.toString();
    }

    /** Places a Mekanized block; false (nothing placed) if the position belongs to another Folia region. */
    static boolean place(World world, int x, int y, int z, String block) {
        if (!PaperWorld.scheduler().isOwnedByCurrentThread(world, x >> 4, z >> 4)) return false;
        return CraftEngineBlocks.place(new Location(world, x, y, z), Key.of(MekanizedContent.NAMESPACE, block), false);
    }
}
