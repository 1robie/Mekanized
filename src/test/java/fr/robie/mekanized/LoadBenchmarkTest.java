package fr.robie.mekanized;

import fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.mekanized.machine.EjectBehavior;
import fr.robie.mekanized.machine.LogisticsMachines;
import fr.robie.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static fr.robie.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rough load test, headless: many busy machines on cables, factories, and item networks, ticked
 * for a while. Prints the average time per tick; fails only far above a server's 50 ms budget, so it
 * catches big regressions without flaking on slow machines.
 */
class LoadBenchmarkTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void manyMachinesAndNetworks() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        int rows = 50;
        List<MachineInstanceImpl> machines = new ArrayList<>();
        // 50 rows: creative cube > 4 cables > enrichment chamber > transporter > enriching factory > chest.
        for (int row = 0; row < rows; row++) {
            int z = row * 2;
            world.place(type(this.engine, "creative_energy_cube"), FakeWorld.at(0, 64, z), Direction.EAST);
            for (int x = 1; x <= 4; x++) world.placeTransport(MekanizedRegistry.CABLES.get(3), FakeWorld.at(x, 64, z));
            MachineInstanceImpl chamber = world.place(type(this.engine, "enrichment_chamber"), FakeWorld.at(5, 64, z), Direction.NORTH);
            chamber.context().set(EjectBehavior.EJECT, true);
            insert(chamber, "input", item("mekanized:osmium_ore"), 64);
            machines.add(chamber);
            world.placeTransport(LogisticsMachines.TRANSPORTERS.get(3), FakeWorld.at(6, 64, z));
            MachineInstanceImpl factory = world.place(type(this.engine, "ultimate_enriching_factory"), FakeWorld.at(7, 64, z), Direction.NORTH);
            insert(factory, "energy", MekanizedContent.ENERGY.variant(), 1_000_000);
            insert(factory, "input", item("mekanized:osmium_ore"), 64);
            machines.add(factory);
            world.setContainer(FakeWorld.at(8, 64, z), CombinedResourceHandler.slots(27, 64));
        }
        world.tick(100); // warm up: networks form, processes start
        int ticks = 400;
        long start = System.nanoTime();
        world.tick(ticks);
        double perTick = (System.nanoTime() - start) / 1e6 / ticks;
        System.out.printf("Load benchmark: %d machines, %d cables/transporters: %.3f ms per tick%n",
                rows * 3, rows * 5, perTick);
        assertTrue(perTick < 50, "a tick took " + perTick + " ms");
    }
}
