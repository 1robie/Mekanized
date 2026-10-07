package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.api.item.ItemResources;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Energy cubes charging tablets and gear, headless (the charge lives in the stand-in item's bytes). */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class EnergyItemsChainTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void energyCubesChargeTabletsThatPowerMachines() {
        // Headless: the charge is kept in the stand-in item's bytes ("id|joules").
        installChargeAccess();
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl cube = place(this.engine, world, "basic_energy_cube");
        fill(cube, "energy", MekanizedContent.ENERGY.stack(100_000));
        fill(cube, "charge", item("mekanized:energy_tablet").stack(1));
        world.tick(10);
        ResourceVariant tablet = cube.inspect().storages().get("charge").getFirst().variant();
        assertEquals(40_000, ItemResources.capacity(tablet, MekanizedContent.ENERGY) > 0 ? chargeOf(tablet) : -1,
                "the basic cube charges at its output rate, 4 kJ per tick");
        assertEquals(60_000, energy(cube));

        MachineInstanceImpl chamber = world.place(type(this.engine, "enrichment_chamber"), FakeWorld.at(3, 64, 0), Direction.NORTH);
        fill(chamber, "energy_slot", tablet.stack(1));
        world.tick(2);
        assertTrue(energy(chamber) >= 10_000, "the tablet powers the machine: " + energy(chamber));
        assertEquals(40_000 - energy(chamber), chargeOf(chamber.inspect().storages().get("energy_slot").getFirst().variant()),
                "energy is conserved");
    }

    @Test
    void energyCubesChargeGear() {
        installChargeAccess();
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl cube = place(this.engine, world, "ultimate_energy_cube");
        fill(cube, "energy", MekanizedContent.ENERGY.stack(100_000_000));
        for (String gear : List.of("mekasuit_bodyarmor", "atomic_disassembler", "free_runners")) {
            fill(cube, "charge", item("mekanized:" + gear).stack(1));
            world.tick(200);
            ResourceVariant charged = cube.inspect().storages().get("charge").getFirst().variant();
            assertEquals(ShippedItems.capacity(gear, "machineengine:energy"), chargeOf(charged),
                    gear + " charges to its capacity");
            try (Transaction transaction = this.engine.transactions().openOuter()) {
                cube.context().storage("charge").extract(charged, 1, transaction);
                transaction.commit();
            }
        }
    }
}
