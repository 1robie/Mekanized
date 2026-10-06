package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.GaugeDropper;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Objects;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gauge dropper's moves between a machine and the dropper's contents, with no player. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class GaugeDropperTest {
    private static final ResourceType WATER = Objects.requireNonNull(MekanizedContent.fluid("water"));
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    private MachineInstanceImpl tank(FakeWorld world, long water) {
        MachineInstanceImpl tank = place(this.engine, world, "basic_fluid_tank");
        if (water > 0) fill(tank, "fluid", WATER.stack(water));
        return tank;
    }

    @Test
    void onlyFluidsAndChemicalsCount() {
        assertTrue(GaugeDropper.isLiquid(WATER.variant()));
        assertTrue(GaugeDropper.isLiquid(chemical(this.engine, "oxygen", 1).variant()));
        assertFalse(GaugeDropper.isLiquid(item("minecraft:bucket")));
        assertFalse(GaugeDropper.isLiquid(MekanizedContent.ENERGY.variant()));
    }

    @Test
    void theLoreSaysWhatItHolds() {
        assertEquals("water: 1,500 / 16,000 mB", GaugeDropper.lore(WATER, 1_500));
    }

    @Test
    void takingFillsTheDropperUpToItsCapacity() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = tank(world, 10_000);
        GaugeDropper.Taken first = GaugeDropper.take(tank.context(), tank.inspect().storages(), null, 0);
        assertNotNull(first);
        assertEquals(WATER, first.type());
        assertEquals(10_000, first.amount());
        assertEquals(0, amountOfType(tank, "fluid", "fluid/water"), "the tank gave it all");

        fill(tank, "fluid", WATER.stack(10_000));
        GaugeDropper.Taken second = GaugeDropper.take(tank.context(), tank.inspect().storages(), WATER, 10_000);
        assertNotNull(second);
        assertEquals(6_000, second.amount(), "only the room left in the dropper");
        assertEquals(4_000, amountOfType(tank, "fluid", "fluid/water"));
    }

    @Test
    void aFullDropperOrAnEmptyMachineGivesNothing() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = tank(world, 5_000);
        assertNull(GaugeDropper.take(tank.context(), tank.inspect().storages(), WATER, GaugeDropper.CAPACITY), "no room");
        assertEquals(5_000, amountOfType(tank, "fluid", "fluid/water"));
        MachineInstanceImpl empty = place(this.engine, world, "basic_fluid_tank");
        assertNull(GaugeDropper.take(empty.context(), empty.inspect().storages(), null, 0), "nothing to take");
    }

    @Test
    void aDropperHoldingOneLiquidTakesOnlyThatOne() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "pressurized_reaction_chamber");
        fill(chamber, "fluid", fluid("water", 1_000));
        fill(chamber, "chemical", chemical(this.engine, "plutonium", 500));
        ResourceType plutonium = chemical(this.engine, "plutonium", 1).variant().type();
        GaugeDropper.Taken taken = GaugeDropper.take(chamber.context(), chamber.inspect().storages(), plutonium, 0);
        assertNotNull(taken);
        assertEquals(plutonium, taken.type());
        assertEquals(500, taken.amount());
        assertEquals(1_000, amountOfType(chamber, "fluid", "fluid/water"), "the water stayed");
    }

    @Test
    void puttingBackGoesIntoTheFirstStorageThatTakesIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = tank(world, 0);
        long given = GaugeDropper.putBack(tank.context(), tank.inspect().storages().keySet(), WATER, 2_500);
        assertEquals(2_500, given);
        assertEquals(2_500, amountOfType(tank, "fluid", "fluid/water"));

        MachineInstanceImpl chemicals = place(this.engine, world, "basic_chemical_tank");
        assertEquals(0, GaugeDropper.putBack(chemicals.context(), chemicals.inspect().storages().keySet(), WATER, 100),
                "a chemical tank takes no water");
    }
}
