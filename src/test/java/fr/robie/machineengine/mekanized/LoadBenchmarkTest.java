package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.api.error.Result;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.definition.DefinitionCompiler;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.machine.MekanizedMachines;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.test.TestEngine;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rough load test, headless: many busy machines on cables, factories, and item networks, ticked
 * for a while. Prints the average time per tick; fails only far above a server's 50 ms budget, so it
 * catches big regressions without flaking on slow machines.
 */
class LoadBenchmarkTest {
    private final CoreMachineEngine engine = TestEngine.builder()
            .addon(AddonRegistration.builder("Mekanized", "1.0.0").register(MekanizedContent::register).build())
            .definitions(MekanizedChainTest::definitions, new DefinitionCompiler.ItemResolver() {
                @Override
                public Result<ResourceVariant> item(Key id) {
                    return Result.ok(MekanizedChainTest.item(id.asString()));
                }
            })
            .start();

    private MachineType type(String name) {
        return this.engine.registries().registry(Registries.MACHINE_TYPE).getOrThrow(MekanizedContent.key(name));
    }

    private void fill(MachineInstanceImpl machine, String storage, ResourceStack stack) {
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            machine.context().storage(storage).insert(stack.variant(), stack.amount(), transaction);
            transaction.commit();
        }
    }

    @Test
    void manyMachinesAndNetworks() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        int rows = 50;
        List<MachineInstanceImpl> machines = new ArrayList<>();
        // 50 rows: creative cube > 4 cables > enrichment chamber > transporter > enriching factory > chest.
        for (int row = 0; row < rows; row++) {
            int z = row * 2;
            world.place(type("creative_energy_cube"), FakeWorld.at(0, 64, z), Direction.EAST);
            for (int x = 1; x <= 4; x++) world.placeTransport(MekanizedMachines.CABLES.get(3), FakeWorld.at(x, 64, z));
            MachineInstanceImpl chamber = world.place(type("enrichment_chamber"), FakeWorld.at(5, 64, z), Direction.NORTH);
            chamber.context().set(fr.robie.machineengine.mekanized.machine.EjectBehavior.EJECT, true);
            fill(chamber, "input", MekanizedChainTest.item("mekanized:osmium_ore").stack(64));
            machines.add(chamber);
            world.placeTransport(LogisticsMachines.TRANSPORTERS.get(3), FakeWorld.at(6, 64, z));
            MachineInstanceImpl factory = world.place(type("ultimate_enriching_factory"), FakeWorld.at(7, 64, z), Direction.NORTH);
            fill(factory, "energy", fr.robie.machineengine.mekanized.MekanizedContent.ENERGY.stack(1_000_000));
            fill(factory, "input", MekanizedChainTest.item("mekanized:osmium_ore").stack(64));
            machines.add(factory);
            world.setContainer(FakeWorld.at(8, 64, z), fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler.slots(27, 64));
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
