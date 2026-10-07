package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.multiblock.Multiblocks;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.multiblock.MultiblockMachines;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Phase 5a multiblocks, headless: formation, the anchor's storage, valves and ports. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class MultiblockTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    private static long insert(ResourceHandler handler, ResourceVariant variant, long amount, CoreMachineEngine engine) {
        try (Transaction transaction = engine.transactions().openOuter()) {
            long inserted = handler.insert(variant, amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Test
    void aDynamicTankFormsAndHoldsOneFluidThroughItsValves() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        WorldPosition valveAt = FakeWorld.at(1, 65, 0);
        box(this.engine, world, 3, "dynamic_tank", Map.of(valveAt, "dynamic_valve", FakeWorld.at(1, 66, 1), "structural_glass"));
        world.tick(3);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        assertNotNull(Multiblocks.containing(valveAt), "a closed 3x3x3 box forms");
        assertEquals(350_000, capacity(anchor, "fluid"), "one block inside: 350,000 mB");

        ResourceHandler valve = world.machine(valveAt).capability(Capabilities.RESOURCE_HANDLER, Direction.NORTH).orElseThrow();
        ResourceVariant water = MekanizedContent.fluid("water").variant();
        assertEquals(1_000, insert(valve, water, 1_000, this.engine));
        assertEquals(1_000, amount(anchor, "fluid"), "the valve fills the anchor's tank");
        ResourceVariant hydrogen = this.engine.registries().registry(Registries.RESOURCE_TYPE).getOrThrow(MekanizedContent.key("hydrogen")).variant();
        assertEquals(0, insert(valve, hydrogen, 1_000, this.engine), "a fluid or a chemical, not both");

        world.remove(FakeWorld.at(2, 65, 1));
        world.tick(3);
        assertNull(Multiblocks.containing(valveAt), "a hole unforms it");
        assertEquals(0, insert(valve, water, 1_000, this.engine), "the valve passes nothing while unformed");
        assertEquals(1_000, amount(anchor, "fluid"), "the anchor keeps the contents");

        world.place(type(this.engine, "dynamic_tank"), FakeWorld.at(2, 65, 1), Direction.NORTH);
        world.tick(3);
        assertEquals(1_000, insert(valve, water, 1_000, this.engine), "formed again");
        assertEquals(2_000, amount(anchor, "fluid"));
    }

    @Test
    void anInductionMatrixStoresEnergyInItsCellsAndOutputsThroughAPort() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        WorldPosition portAt = FakeWorld.at(1, 65, 0);
        // Inside (2x2x2): two basic cells, one basic provider, air.
        world.setBlock(FakeWorld.at(1, 65, 1), MekanizedContent.key("basic_induction_cell"));
        world.setBlock(FakeWorld.at(2, 65, 1), MekanizedContent.key("basic_induction_cell"));
        world.setBlock(FakeWorld.at(1, 66, 1), MekanizedContent.key("basic_induction_provider"));
        box(this.engine, world, 4, "induction_casing", Map.of(portAt, "induction_port"));
        world.tick(3);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        assertEquals(16_000_000_000L, capacity(anchor, "energy"), "2 basic cells of 8 GJ");

        MachineInstanceImpl port = world.machine(portAt);
        ResourceHandler handler = port.capability(Capabilities.RESOURCE_HANDLER, Direction.NORTH).orElseThrow();
        assertEquals(1_000_000, insert(handler, MekanizedContent.ENERGY.variant(), 1_000_000, this.engine), "an input port fills the matrix");

        MachineInstanceImpl chamber = world.place(type(this.engine, "enrichment_chamber"), FakeWorld.at(1, 65, -1), Direction.NORTH);
        port.context().set(MultiblockMachines.PORT_MODE, "output");
        port.wake(WakeReason.CONFIG_CHANGED);
        world.tick(3);
        assertEquals(20_000, amount(chamber, "energy"), "an output port pushes into its neighbor (up to 256 kJ/t)");
        assertEquals(true, port.isActive(), "an output port shows Mekanism's output model (active)");
        assertEquals(1_000_000 - 20_000, amount(anchor, "energy"));
    }

    @Test
    void aThermalEvaporationPlantTurnsWaterIntoBrine() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        WorldPosition controllerAt = FakeWorld.at(1, 65, 0);
        WorldPosition valveAt = FakeWorld.at(2, 65, 0);
        // A 4x4 tower, 3 high, open at the top.
        for (int x = 0; x < 4; x++) for (int y = 64; y < 67; y++) for (int z = 0; z < 4; z++) {
            boolean wall = x == 0 || x == 3 || z == 0 || z == 3 || y == 64;
            if (!wall) continue;
            WorldPosition at = FakeWorld.at(x, y, z);
            String block = at.equals(controllerAt) ? "thermal_evaporation_controller"
                    : at.equals(valveAt) ? "thermal_evaporation_valve" : "thermal_evaporation_block";
            world.place(type(this.engine, block), at, Direction.NORTH);
        }
        world.tick(3);
        assertNotNull(Multiblocks.containing(valveAt), "a 4x4 open tower with one controller forms");
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        assertEquals(3 * 16_000, capacity(anchor, "input"));

        ResourceHandler valve = world.machine(valveAt).capability(Capabilities.RESOURCE_HANDLER, Direction.NORTH).orElseThrow();
        assertEquals(16_000, insert(valve, MekanizedContent.fluid("water").variant(), 16_000, this.engine));
        world.tick(45);
        // Night (no environment headless): half heat, 2 levels: 200 mB of water per second, 20 mB of brine.
        assertEquals(true, amount(anchor, "output") >= 40, "brine made: " + amount(anchor, "output"));
        assertEquals(16_000 - amount(anchor, "output") * 10, amount(anchor, "input"), "10 mB of water per mB of brine");
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            long taken = valve.extract(MekanizedContent.fluid("brine").variant(), 10, transaction);
            assertEquals(10, taken, "the valve gives the output");
        }
    }

    @Test
    void aBoxWithSomethingInsideDoesNotForm() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        world.setBlock(FakeWorld.at(1, 65, 1), Key.key("minecraft", "stone"));
        box(this.engine, world, 3, "dynamic_tank", Map.of());
        world.tick(3);
        assertNull(Multiblocks.containing(FakeWorld.at(0, 64, 0)), "stone inside a tank");
    }
}
