package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.Fluids;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.ChemistryBehaviors;
import fr.robie.machineengine.mekanized.machine.ChemistryMachines;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chemicals and fluids end to end, headless: the chemical chain, electrolysis, condensing, buckets, pumps, tubes, reactions. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class ChemistryChainTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void theChemicalChainGivesFiveCrystalsPerOre() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl dissolution = world.place(type(this.engine, "chemical_dissolution_chamber"), FakeWorld.at(0, 64, 0), Direction.NORTH);
        fill(dissolution, "energy", MekanizedContent.ENERGY.stack(160_000));
        fill(dissolution, "chemical", chemical(this.engine, "sulfuric_acid", 1_000));
        fill(dissolution, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(101);
        assertEquals(1_000, amountOfType(dissolution, "output", "dirty_osmium"), "1 osmium ore dissolves into 1000 mB of dirty slurry");
        assertEquals(1_000 - 100, amountOfType(dissolution, "chemical", "sulfuric_acid"), "1 mB of acid per tick");

        MachineInstanceImpl washer = world.place(type(this.engine, "chemical_washer"), FakeWorld.at(2, 64, 0), Direction.NORTH);
        fill(washer, "input", chemical(this.engine, "dirty_osmium", 1_000));
        fill(washer, "fluid", fluid("water", 10_000));
        powered(world, 1_001, washer);
        assertEquals(1_000, amountOfType(washer, "output", "clean_osmium"), "washing is one to one");
        assertEquals(10_000 - 5_000, amountOf(washer, "fluid", Fluids.water().variant()), "5 mB of water per mB of slurry");

        MachineInstanceImpl crystallizer = world.place(type(this.engine, "chemical_crystallizer"), FakeWorld.at(4, 64, 0), Direction.NORTH);
        fill(crystallizer, "input", chemical(this.engine, "clean_osmium", 1_000));
        powered(world, 5 * 200 + 5, crystallizer);
        assertEquals(5, amount(crystallizer, "output", "mekanized:crystal_osmium"), "200 mB of clean slurry per crystal");
    }

    @Test
    void anElectrolyticSeparatorSplitsWater() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl separator = place(this.engine, world, "electrolytic_separator");
        fill(separator, "energy", MekanizedContent.ENERGY.stack(160_000));
        fill(separator, "input", fluid("water", 200));
        world.tick(110);
        assertEquals(200, amountOfType(separator, "output", "hydrogen"), "2 mB of water give 2 mB of hydrogen");
        assertEquals(100, amountOfType(separator, "output", "oxygen"), "and 1 mB of oxygen");
        assertEquals(0, amountOf(separator, "input", Fluids.water().variant()));
    }

    @Test
    void speedUpgradesRunOneTickRecipesSeveralTimesPerTick() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl separator = place(this.engine, world, "electrolytic_separator");
        separator.installUpgrade(MekanizedRegistry.SPEED, 2);
        fill(separator, "input", fluid("water", 1_000));
        powered(world, 10, separator);
        // 1 + 2 operations per tick, 2 mB of water each.
        assertEquals(1_000 - 10 * 3 * 2, amountOf(separator, "input", Fluids.water().variant()));
        assertEquals(10 * 3 * 2, amountOfType(separator, "output", "hydrogen"));
    }

    @Test
    void aRotaryCondensentratorGoesBothWays() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl rotary = place(this.engine, world, "rotary_condensentrator");
        fill(rotary, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(rotary, "chemical", chemical(this.engine, "oxygen", 50));
        world.tick(60);
        assertEquals(50, amountOfType(rotary, "fluid", "fluid/oxygen"), "condensing: chemical to fluid");
        assertEquals(PortDirection.OUTPUT, rotary.context().portDirection("fluid"));

        rotary.context().set(ChemistryBehaviors.MODE, "decondensing");
        rotary.wake(WakeReason.CONFIG_CHANGED);
        world.tick(60);
        assertEquals(50, amountOfType(rotary, "chemical", "oxygen"), "decondensing: fluid back to chemical");
        assertEquals(PortDirection.INPUT, rotary.context().portDirection("fluid"),
                "the fluid side became the input");
        assertEquals(PortDirection.OUTPUT, rotary.context().portDirection("chemical"));
    }

    @Test
    void bucketsFillAndEmptyFluidTanks() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = place(this.engine, world, "basic_fluid_tank");
        fill(tank, "bucket_in", item("minecraft:water_bucket").stack(1));
        world.tick(2);
        assertEquals(1_000, amountOf(tank, "fluid", Fluids.water().variant()), "the bucket empties into the tank");
        assertEquals(1, amount(tank, "bucket_out", "minecraft:bucket"), "and comes back empty");

        // The player takes the empty bucket and puts it back in.
        try (Transaction transaction = tank.services().transactions().openOuter()) {
            assertEquals(1, tank.context().storage("bucket_out").extract(item("minecraft:bucket"), 1, transaction));
            transaction.commit();
        }
        fill(tank, "bucket_in", item("minecraft:bucket").stack(1));
        world.tick(2);
        assertEquals(0, amountOf(tank, "fluid", Fluids.water().variant()), "an empty bucket takes 1000 mB");
        assertEquals(1, amount(tank, "bucket_out", "minecraft:water_bucket"));
    }

    @Test
    void anElectricPumpTakesSourceBlocksBelowIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl pump = place(this.engine, world, "electric_pump");
        fill(pump, "energy", MekanizedContent.ENERGY.stack(40_000));
        Key water = Key.key("minecraft", "water");
        // Flowing water below the pump, a source next to it.
        world.setFluid(FakeWorld.at(0, 63, 0), new WorldFluid(water, false));
        world.setFluid(FakeWorld.at(1, 63, 0), new WorldFluid(water, true));
        world.tick(2);
        assertEquals(1_000, amountOf(pump, "fluid", Fluids.water().variant()), "one source block is 1000 mB");
        assertEquals(null, world.fluidAt(FakeWorld.at(1, 63, 0)), "the source block is gone");
        assertEquals(40_000 - 2_000, energy(pump), "2 kJ per block");
        world.tick(100);
        assertEquals(1_000, amountOf(pump, "fluid", Fluids.water().variant()), "only flowing water is left: nothing more to take");
    }

    @Test
    void pressurizedTubesCarryChemicals() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = world.place(type(this.engine, "basic_chemical_tank"), FakeWorld.at(0, 64, 0), Direction.EAST);
        fill(tank, "chemical", chemical(this.engine, "brine", 30));
        world.placeTransport(ChemistryMachines.TUBES.get(0), FakeWorld.at(1, 64, 0));
        MachineInstanceImpl crystallizer = world.place(type(this.engine, "chemical_crystallizer"), FakeWorld.at(2, 64, 0), Direction.NORTH);
        powered(world, 2 * 200 + 10, crystallizer);
        assertEquals(0, amountOfType(tank, "chemical", "brine"), "the tube emptied the tank's front");
        assertEquals(2, amount(crystallizer, "output", "mekanized:salt"), "15 mB of brine per salt");
    }

    @Test
    void aPressurizedReactionChamberMakesAnItemAndAChemical() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "pressurized_reaction_chamber");
        fill(chamber, "input", item("mekanized:dust_fluorite").stack(1));
        fill(chamber, "fluid", fluid("water", 1_000));
        fill(chamber, "chemical", chemical(this.engine, "plutonium", 1_000));
        powered(world, 105, chamber);
        assertEquals(1, amount(chamber, "output", "mekanized:pellet_plutonium"), "the item into the item slot");
        assertEquals(1_000, amountOfType(chamber, "chemical_output", "spent_nuclear_waste"), "the chemical into its tank");
    }

    @Test
    void aSolarNeutronActivatorWorksOnlyInDaylight() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl activator = place(this.engine, world, "solar_neutron_activator");
        fill(activator, "input", chemical(this.engine, "lithium", 100));
        world.tick(60);
        assertEquals(0, amountOfType(activator, "output", "tritium"), "night: nothing");
        world.setEnvironment(new MachineEnvironment(true, true, false, 15));
        activator.wake(WakeReason.CONFIG_CHANGED);
        world.tick(60);
        assertTrue(amountOfType(activator, "output", "tritium") > 0, "daylight: lithium to tritium");
    }
}
