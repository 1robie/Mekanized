package fr.robie.machineengine.mekanized.paper.command;

import fr.robie.machineengine.api.multiblock.Multiblocks;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.MekanizedTestKit;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.multiblock.MultiblockMachines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.item;
import static fr.robie.machineengine.mekanized.MekanizedTestKit.type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every structure {@code /mekanized build} knows is built the way the command builds it (frames last, ports
 * set to their modes, rotors given their blades) in a headless world, and must form.
 */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class StructuresFormTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    private static WorldPosition at(List<Integer> relative) {
        return FakeWorld.at(relative.get(0), 64 + relative.get(1), relative.get(2));
    }

    @Test
    void theCommandKnowsEveryStructure() {
        assertEquals(List.of("dynamic_tank", "induction_matrix", "thermal_evaporation", "boiler", "turbine", "fission_reactor",
                "fusion_reactor", "sps", "teleporter"), List.copyOf(MekanizedStructures.STRUCTURES.keySet()));
    }

    @TestFactory
    Stream<DynamicTest> everyBuiltStructureForms() {
        return MekanizedStructures.STRUCTURES.entrySet().stream().map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> {
            MekanizedTestKit.ResetStatics.reset();
            forms(entry.getKey(), entry.getValue());
        }));
    }

    private boolean isMachine(String block) {
        return this.engine.registries().registry(Registries.MACHINE_TYPE).get(MekanizedContent.key(block)).isPresent();
    }

    private void forms(String name, MekanizedStructures.Structure structure) {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        // Inner blocks first, frames last, as the command does.
        List<Map.Entry<List<Integer>, String>> order = structure.blocks().entrySet().stream()
                .sorted((a, b) -> Boolean.compare(MekanizedStructures.isFrame(a.getValue()), MekanizedStructures.isFrame(b.getValue()))).toList();
        for (Map.Entry<List<Integer>, String> block : order) {
            // Plain blocks (induction cells and providers) are no machines: the structure only counts them.
            if (isMachine(block.getValue())) {
                world.place(type(this.engine, block.getValue()), at(block.getKey()), Direction.NORTH);
            } else {
                world.setBlock(at(block.getKey()), MekanizedContent.key(block.getValue()));
            }
        }
        for (Map.Entry<List<Integer>, String> block : structure.blocks().entrySet()) {
            WorldPosition position = at(block.getKey());
            String mode = structure.modes().get(block.getKey());
            if (!isMachine(block.getValue())) continue;
            var machine = world.machine(position);
            if (mode != null) machine.context().set(MultiblockMachines.PORT_MODE, mode);
            if (block.getValue().equals("turbine_rotor")) {
                MekanizedTestKit.insert(machine, "blades", item("mekanized:turbine_blade"), 2);
            }
            machine.wake(fr.robie.machineengine.api.machine.WakeReason.CONFIG_CHANGED);
        }
        if (name.equals("teleporter")) {
            WorldPosition teleporter = at(List.of(1, 0, 0));
            world.machine(teleporter).context().set(AutomationMachines.FREQUENCY, "home");
            world.tick(45);
            Frequencies.Teleporter registered = Frequencies.teleporters().get(teleporter);
            assertNotNull(registered, "the teleporter registers its frequency");
            assertTrue(registered.formed(), "its frame is complete");
            return;
        }
        world.tick(5);
        for (List<Integer> relative : structure.blocks().keySet()) {
            assertNotNull(Multiblocks.containing(at(relative)), name + ": the block at " + relative + " is part of a formed structure");
        }
    }
}
