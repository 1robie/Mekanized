package fr.robie.machineengine.mekanized.paper.command;

import fr.robie.machineengine.mekanized.MekanizedContent;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.multiblock.MemberBehavior;
import fr.robie.machineengine.mekanized.paper.PaperWorld;
import fr.robie.machineengine.paper.PaperItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ready-made multiblocks for {@code /mekanized build <structure> [x y z]} (admins): the smallest
 * working build of each, from the given corner towards +x, +y and +z, valves and ports set.
 */
public final class MekanizedStructures {
    /** A structure: blocks by relative position, and the valves or ports to set to a mode. */
    record Structure(Map<List<Integer>, String> blocks, Map<List<Integer>, String> modes) {
    }

    static final Map<String, Structure> STRUCTURES = new LinkedHashMap<>();

    static {
        STRUCTURES.put("dynamic_tank", box(3, 3, 3, "dynamic_tank", (blocks) -> {
            blocks.put(at(1, 1, 0), "dynamic_valve");
            blocks.put(at(1, 2, 1), "structural_glass");
        }, Map.of()));
        STRUCTURES.put("induction_matrix", box(4, 3, 3, "induction_casing", (blocks) -> {
            blocks.put(at(1, 1, 1), "basic_induction_cell");
            blocks.put(at(2, 1, 1), "basic_induction_provider");
            blocks.put(at(1, 1, 0), "induction_port");
            blocks.put(at(2, 1, 0), "induction_port");
        }, Map.of(at(2, 1, 0), "output")));
        Map<List<Integer>, String> tower = new LinkedHashMap<>();
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) {
            boolean wall = y == 0 || x == 0 || x == 3 || z == 0 || z == 3;
            if (wall) tower.put(at(x, y, z), "thermal_evaporation_block");
        }
        tower.put(at(1, 1, 0), "thermal_evaporation_controller");
        tower.put(at(2, 1, 0), "thermal_evaporation_valve");
        STRUCTURES.put("thermal_evaporation", new Structure(tower, Map.of()));
        STRUCTURES.put("boiler", box(4, 4, 4, "boiler_casing", (blocks) -> {
            for (int x = 1; x <= 2; x++) for (int z = 1; z <= 2; z++) {
                blocks.put(at(x, 1, z), "superheating_element");
                blocks.put(at(x, 2, z), "pressure_disperser");
            }
            blocks.put(at(1, 1, 0), "boiler_valve");
            blocks.put(at(2, 2, 0), "boiler_valve");
        }, Map.of(at(2, 2, 0), "output_steam")));
        STRUCTURES.put("turbine", box(5, 7, 5, "turbine_casing", (blocks) -> {
            for (int y = 1; y <= 3; y++) blocks.put(at(2, y, 2), "turbine_rotor");
            for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
                blocks.put(at(x, 4, z), x == 2 && z == 2 ? "rotational_complex" : "pressure_disperser");
                blocks.put(at(x, 5, z), x == 2 && z == 2 ? "electromagnetic_coil" : "saturating_condenser");
                blocks.put(at(x, 6, z), "turbine_vent");
            }
            blocks.put(at(1, 1, 0), "turbine_valve");
            blocks.put(at(3, 1, 0), "turbine_valve");
        }, Map.of()));
        STRUCTURES.put("fission_reactor", box(5, 5, 5, "fission_reactor_casing", (blocks) -> {
            blocks.put(at(2, 1, 2), "fission_fuel_assembly");
            blocks.put(at(2, 2, 2), "fission_fuel_assembly");
            blocks.put(at(2, 3, 2), "control_rod_assembly");
            blocks.put(at(1, 1, 0), "fission_reactor_port");
            blocks.put(at(2, 1, 0), "fission_reactor_port");
            blocks.put(at(3, 1, 0), "fission_reactor_port");
            blocks.put(at(2, 2, 0), "reactor_glass");
            blocks.put(at(2, 3, 0), "fission_reactor_logic_adapter");
        }, Map.of(at(2, 1, 0), "output_coolant", at(3, 1, 0), "output_waste")));
        STRUCTURES.put("fusion_reactor", box(5, 5, 5, "fusion_reactor_frame", (blocks) -> {
            blocks.put(at(2, 2, 0), "fusion_reactor_controller");
            blocks.put(at(1, 2, 0), "fusion_reactor_port");
            blocks.put(at(3, 2, 0), "fusion_reactor_port");
            blocks.put(at(2, 2, 4), "laser_focus_matrix");
            blocks.put(at(2, 4, 2), "reactor_glass");
        }, Map.of(at(3, 2, 0), "output")));
        STRUCTURES.put("sps", box(5, 5, 5, "sps_casing", (blocks) -> {
            blocks.put(at(1, 2, 0), "sps_port");
            blocks.put(at(3, 2, 0), "sps_port");
            blocks.put(at(2, 4, 2), "reactor_glass");
        }, Map.of(at(3, 2, 0), "output")));
        Map<List<Integer>, String> teleporter = new LinkedHashMap<>();
        teleporter.put(at(1, 0, 0), "teleporter");
        for (int y = 0; y <= 3; y++) {
            teleporter.put(at(0, y, 0), "teleporter_frame");
            teleporter.put(at(2, y, 0), "teleporter_frame");
        }
        teleporter.put(at(1, 3, 0), "teleporter_frame");
        STRUCTURES.put("teleporter", new Structure(teleporter, Map.of()));
    }

    private MekanizedStructures() {
    }

    private static List<Integer> at(int x, int y, int z) {
        return List.of(x, y, z);
    }

    /** A box of {@code casing}, then {@code inside} places the rest (inside or in the walls). */
    private static Structure box(int sx, int sy, int sz, String casing, Consumer<Map<List<Integer>, String>> inside,
                                 Map<List<Integer>, String> modes) {
        Map<List<Integer>, String> blocks = new LinkedHashMap<>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            if (x == 0 || x == sx - 1 || y == 0 || y == sy - 1 || z == 0 || z == sz - 1) blocks.put(at(x, y, z), casing);
        }
        inside.accept(blocks);
        return new Structure(blocks, modes);
    }

    /**
     * Builds {@code name} with its lowest corner at (x, y, z), on the thread owning it; the space must
     * be empty. Turbine rotors get their blades.
     *
     * @return what went wrong, or null
     */
    static String build(String name, World world, int x, int y, int z) {
        Structure structure = STRUCTURES.get(name);
        if (structure == null) return "unknown structure " + name;
        // Folia: the whole structure must belong to the region running the command.
        for (List<Integer> at : structure.blocks().keySet()) {
            if (!Bukkit.isOwnedByCurrentRegion(world, (x + at.get(0)) >> 4, (z + at.get(2)) >> 4)) {
                return "the structure crosses into another region: build it a few blocks away";
            }
        }
        for (List<Integer> at : structure.blocks().keySet()) {
            if (!world.getBlockAt(x + at.get(0), y + at.get(1), z + at.get(2)).getType().isAir()) {
                return "the space is not empty at " + (x + at.get(0)) + " " + (y + at.get(1)) + " " + (z + at.get(2));
            }
        }
        // Inner blocks first: frames find their structure once everything is there.
        List<Map.Entry<List<Integer>, String>> order = structure.blocks().entrySet().stream()
                .sorted((a, b) -> Boolean.compare(isFrame(a.getValue()), isFrame(b.getValue()))).toList();
        for (Map.Entry<List<Integer>, String> block : order) {
            List<Integer> at = block.getKey();
            Location location = new Location(world, x + at.get(0), y + at.get(1), z + at.get(2));
            if (!CraftEngineBlocks.place(location, Key.of(MekanizedContent.NAMESPACE, block.getValue()), false)) {
                return "could not place " + block.getValue() + " (is the Mekanized pack loaded?)";
            }
        }
        ItemStack blade = PaperItems.itemOf(MekanizedContent.key("turbine_blade"));
        for (Map.Entry<List<Integer>, String> block : structure.blocks().entrySet()) {
            List<Integer> at = block.getKey();
            WorldPosition position = WorldPosition.of(world.getKey(), x + at.get(0), y + at.get(1), z + at.get(2));
            String mode = structure.modes().get(at);
            var loaded = PaperWorld.machine(position);
            if (loaded == null || loaded.instance() == null) continue;
            if (mode != null) loaded.instance().context().set(MemberBehavior.PORT_MODE, mode);
            if (block.getValue().equals("turbine_rotor") && blade != null) {
                var context = loaded.instance().context();
                try (Transaction transaction = context.transactions().openOuter()) {
                    context.storage("blades").insert(PaperItems.toResource(blade).variant(), 2, transaction);
                    transaction.commit();
                }
            }
            loaded.instance().wake(WakeReason.CONFIG_CHANGED);
        }
        return null;
    }

    static boolean isFrame(String block) {
        return block.endsWith("_casing") || block.endsWith("_frame") || block.equals("dynamic_tank")
                || block.equals("thermal_evaporation_block");
    }
}
