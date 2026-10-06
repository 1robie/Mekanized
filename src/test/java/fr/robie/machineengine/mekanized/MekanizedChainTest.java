package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.diagnostic.Diagnostic;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.network.TransportSideMode;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.ChemistryBehaviors;
import fr.robie.machineengine.mekanized.machine.ChemistryMachines;
import fr.robie.machineengine.mekanized.machine.EjectBehavior;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The imported content working end to end, headless: Mekanism's recipes and tags (as installed on a
 * server), Mekanized's machines and the MachineEngine core. Items are stand-ins identified by id.
 */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class MekanizedChainTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void everyImportedRecipeAndTagLoads() {
        List<Diagnostic> problems = new ArrayList<>(this.engine.startupReport().errors());
        problems.addAll(this.engine.startupReport().warnings());
        assertTrue(problems.isEmpty(), "imported definitions load cleanly: " + this.engine.startupReport().format());
        assertTrue(this.engine.registries().registry(Registries.RECIPE).size() > 400, "recipes loaded");
    }

    @Test
    void anEnrichmentChamberDoublesOre() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber");
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(201);
        assertEquals(2, amount(chamber, "output", "mekanized:dust_osmium"), "1 osmium ore gives 2 osmium dust");
        assertEquals(20_000 - 200 * 50, energy(chamber), "50 J per tick for 200 ticks");
    }

    @Test
    void machinesNeedEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber");
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(400);
        assertEquals(0, amount(chamber, "output", "mekanized:dust_osmium"), "no energy, no dust");
        assertEquals(0, chamber.inspect().process() == null ? 0 : chamber.inspect().process().progress(), "and no progress");
    }

    @Test
    void aMetallurgicInfuserMakesInfusedAlloyFromRedstone() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl infuser = place(this.engine, world, "metallurgic_infuser");
        fill(infuser, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(infuser, "chemical_slot", item("minecraft:redstone").stack(1));
        fill(infuser, "input", item("minecraft:copper_ingot").stack(1));
        world.tick(205);
        assertEquals(1, amount(infuser, "output", "mekanized:alloy_infused"), "copper ingot + 10 redstone infuse type: " + infuser.inspect());
    }

    @Test
    void slotsOnlyTakeWhatTheirRecipesUse() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl infuser = place(this.engine, world, "metallurgic_infuser");
        assertEquals(0, insert(infuser, "input", item("minecraft:redstone")), "redstone is no infusing input");
        assertEquals(0, insert(infuser, "energy_slot", item("mekanized:osmium_ore")), "an ore gives no energy");
        assertEquals(1, insert(infuser, "chemical_slot", item("minecraft:redstone")), "redstone is an infuse source");
        assertEquals(1, insert(infuser, "input", item("minecraft:copper_ingot")));
    }

    @Test
    void speedUpgradesMakeItFasterAndCostlier() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber");
        world.runInRegionOf(HERE, () -> assertTrue(chamber.installUpgrade(MekanizedRegistry.SPEED, 2).isOk()));
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(114);
        assertEquals(2, amount(chamber, "output", "mekanized:dust_osmium"), "200 x 0.75 x 0.75 = 112 ticks");
    }

    @Test
    void aPurificationChamberTriplesOreWithOxygenFromItsTank() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "purification_chamber");
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(80_000));
        fill(chamber, "chemical", chemical(this.engine, "oxygen", 210));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(201);
        assertEquals(3, amount(chamber, "output", "mekanized:clump_osmium"), "1 ore gives 3 clumps");
        assertEquals(10, amountOfType(chamber, "chemical", "oxygen"), "1 mB of oxygen per tick");
    }

    @Test
    void redstoneInTheEnergySlotBecomesEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "crusher");
        fill(chamber, "energy_slot", item("minecraft:redstone").stack(1));
        world.tick(2);
        assertEquals(10_000, energy(chamber), "Mekanism: 10 kJ per redstone (vanilla redstone is in c:dusts/redstone)");
    }

    @Test
    void generatorsMakeEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl heat = place(this.engine, world, "heat_generator");
        fill(heat, "fuel", item("minecraft:coal").stack(1));
        world.tick(11);
        assertTrue(energy(heat) >= 10 * 200, "200 J per tick while coal burns: " + energy(heat));

        WorldPosition there = FakeWorld.at(10, 64, 0);
        world.setEnvironment(new MachineEnvironment(true, true, false, 15));
        MachineInstanceImpl solar = world.place(type(this.engine, "solar_generator"), there, Direction.NORTH);
        world.tick(41);
        assertTrue(energy(solar) >= 2 * 20 * 50, "50 J per tick in the sun: " + energy(solar));
        assertTrue(solar.isActive());
    }

    // ------------------------------------------------------------------------------------------

    // -- Phase 3: chemicals and fluids ----------------------------------------------------------

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
        assertEquals(10_000 - 5_000, amountOfType(washer, "fluid", "fluid/water"), "5 mB of water per mB of slurry");

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
        assertEquals(0, amountOfType(separator, "input", "fluid/water"));
    }

    @Test
    void energyUpgradesEnlargeTheBufferAndLowerTheCost() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber");
        world.runInRegionOf(HERE, () -> assertTrue(chamber.installUpgrade(MekanizedRegistry.ENERGY_UPGRADE, 2).isOk()));
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(60_000)); // 20 kJ base, +100 % per upgrade
        assertEquals(60_000, energy(chamber));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(201);
        assertEquals(2, amount(chamber, "output", "mekanized:dust_osmium"));
        assertEquals(60_000 - 200 * 32, energy(chamber), "50 J x 0.8 x 0.8 = 32 J per tick");

        var saved = world.unload(HERE);
        MachineInstanceImpl reloaded = (MachineInstanceImpl) world.load(HERE, saved);
        assertEquals(60_000 - 200 * 32, energy(reloaded), "energy above the base capacity survives a reload");
    }

    @Test
    void speedUpgradesRunOneTickRecipesSeveralTimesPerTick() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl separator = place(this.engine, world, "electrolytic_separator");
        separator.installUpgrade(MekanizedRegistry.SPEED, 2);
        fill(separator, "input", fluid("water", 1_000));
        powered(world, 10, separator);
        // 1 + 2 operations per tick, 2 mB of water each.
        assertEquals(1_000 - 10 * 3 * 2, amountOfType(separator, "input", "fluid/water"));
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
        assertEquals(1_000, amountOfType(tank, "fluid", "fluid/water"), "the bucket empties into the tank");
        assertEquals(1, amount(tank, "bucket_out", "minecraft:bucket"), "and comes back empty");

        // The player takes the empty bucket and puts it back in.
        try (Transaction transaction = tank.services().transactions().openOuter()) {
            assertEquals(1, tank.context().storage("bucket_out").extract(item("minecraft:bucket"), 1, transaction));
            transaction.commit();
        }
        fill(tank, "bucket_in", item("minecraft:bucket").stack(1));
        world.tick(2);
        assertEquals(0, amountOfType(tank, "fluid", "fluid/water"), "an empty bucket takes 1000 mB");
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
        assertEquals(1_000, amountOfType(pump, "fluid", "fluid/water"), "one source block is 1000 mB");
        assertEquals(null, world.fluidAt(FakeWorld.at(1, 63, 0)), "the source block is gone");
        assertEquals(40_000 - 2_000, energy(pump), "2 kJ per block");
        world.tick(100);
        assertEquals(1_000, amountOfType(pump, "fluid", "fluid/water"), "only flowing water is left: nothing more to take");
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

    // -- Phase 4: side configuration and auto-eject ----------------------------------------------

    @Test
    void sideConfigurationMovesPortsAndIsSaved() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl crusher = place(this.engine, world, "crusher");
        Set<RelativeSide> left = EnumSet.of(RelativeSide.LEFT);
        crusher.context().setPortSides("output", left);
        Direction west = RelativeSide.LEFT.toAbsolute(Direction.NORTH);
        Direction east = RelativeSide.RIGHT.toAbsolute(Direction.NORTH);
        fill(crusher, "output", item("mekanized:dust_osmium").stack(3));
        assertEquals(3, extractThrough(crusher, west, "mekanized:dust_osmium", 3), "the output port is on the left now");
        fill(crusher, "output", item("mekanized:dust_osmium").stack(3));
        assertEquals(0, extractThrough(crusher, east, "mekanized:dust_osmium", 3), "and no longer on the right");

        var saved = world.unload(HERE);
        MachineInstanceImpl reloaded = (MachineInstanceImpl) world.load(HERE, saved);
        assertEquals(left, reloaded.context().portSides("output"), "side configuration is saved with the machine");
        assertEquals(EnumSet.allOf(RelativeSide.class), reloaded.context().portSides("input"),
                "ports left alone keep the type's sides");
    }

    @Test
    void autoEjectPushesOutputsIntoNeighbors() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl oxidizer = world.place(type(this.engine, "chemical_oxidizer"), FakeWorld.at(0, 64, 0), Direction.NORTH);
        // The tank's back (an input) touches the oxidizer.
        MachineInstanceImpl tank = world.place(type(this.engine, "basic_chemical_tank"), FakeWorld.at(1, 64, 0), Direction.EAST);
        fill(oxidizer, "input", item("mekanized:salt").stack(2));
        powered(world, 105, oxidizer);
        assertEquals(15, amountOfType(oxidizer, "output", "brine"), "without auto-eject the brine stays");
        assertEquals(0, amountOfType(tank, "chemical", "brine"));

        oxidizer.context().set(EjectBehavior.EJECT, true);
        oxidizer.wake(WakeReason.CONFIG_CHANGED);
        powered(world, 105, oxidizer);
        assertEquals(30, amountOfType(tank, "chemical", "brine"), "auto-eject pushed both batches into the tank");
        assertEquals(0, amountOfType(oxidizer, "output", "brine"));
    }

    @Test
    void aFactoryRunsItsLanesAtOnce() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl factory = place(this.engine, world, "basic_enriching_factory");
        fill(factory, "input", item("mekanized:osmium_ore").stack(3));
        powered(world, 205, factory);
        assertEquals(6, amount(factory, "output", "mekanized:dust_osmium"), "3 ores spread over 3 lanes, enriched at once");
        for (String lane : List.of("input", "input_2", "input_3")) {
            assertEquals(0, amount(factory, lane, "mekanized:osmium_ore"), lane + " used its ore");
        }
    }

    @Test
    void anInfusingFactoryUsesItsInfuseType() {
        // Mekanism lists the infuse type before the item: a lane must still find its recipe.
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl factory = place(this.engine, world, "basic_infusing_factory");
        fill(factory, "chemical", chemical(this.engine, "redstone", 60));
        fill(factory, "input", item("mekanized:ingot_osmium").stack(3));
        powered(world, 205, factory);
        assertEquals(3, amount(factory, "output", "mekanized:basic_control_circuit"), "3 lanes, 20 mB of redstone each");
        assertEquals(0, amountOfType(factory, "chemical", "redstone"));
    }

    @Test
    void aTierInstallerKeepsWhatTheMachineHolds() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber");
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(15_000));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(5));
        world.tick(20); // a process is running: it is cancelled, its ore given back
        chamber.context().process().cancel();
        var packed = this.engine.machines().pack(chamber).orElseThrow();
        WorldPosition next = FakeWorld.at(5, 64, 0);
        MachineInstanceImpl fresh = world.place(type(this.engine, "basic_enriching_factory"), next, Direction.NORTH);
        MachineInstanceImpl factory = (MachineInstanceImpl) this.engine.machines().restoreAs(fresh, packed.state(), world);
        assertEquals(type(this.engine, "basic_enriching_factory"), factory.type());
        assertEquals(15_000 - 20 * 50, energy(factory), "energy carries over");
        assertEquals(5, amount(factory, "input", "mekanized:osmium_ore"), "the input is lane 1's");
        assertEquals(mekanizedKey("basic_enriching_factory"), LogisticsMachines
                .upgrade(mekanizedKey("enrichment_chamber"), "basic"));
        assertEquals(mekanizedKey("advanced_enriching_factory"), LogisticsMachines
                .upgrade(mekanizedKey("basic_enriching_factory"), "advanced"));
        assertEquals(null, LogisticsMachines
                .upgrade(mekanizedKey("enrichment_chamber"), "advanced"), "tiers go one at a time");
    }

    @Test
    void aSorterFeedsAChestThroughTransporters() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        var from = CombinedResourceHandler.slots(3, 64);
        var to = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            from.insert(item("minecraft:cobblestone"), 10, transaction);
            from.insert(item("minecraft:dirt"), 4, transaction);
            transaction.commit();
        }
        world.setContainer(FakeWorld.at(0, 64, 0), from);
        world.setContainer(FakeWorld.at(4, 64, 0), to);
        MachineInstanceImpl sorter = world.place(type(this.engine, "logistical_sorter"), FakeWorld.at(1, 64, 0), Direction.EAST);
        fill(sorter, "filter", item("minecraft:cobblestone").stack(1));
        world.placeTransport(LogisticsMachines.TRANSPORTERS.get(0), FakeWorld.at(2, 64, 0));
        world.placeTransport(LogisticsMachines.TRANSPORTERS.get(0), FakeWorld.at(3, 64, 0));
        world.tick(60);
        assertEquals(10, to.amountOf(item("minecraft:cobblestone")), "the sorter pulled the cobblestone, the transporters delivered it");
        assertEquals(0, to.amountOf(item("minecraft:dirt")), "dirt does not match the filter");
        assertEquals(4, from.amountOf(item("minecraft:dirt")));
        assertEquals(0, from.amountOf(item("minecraft:cobblestone")));
    }

    @Test
    void energyCubesChargeTabletsThatPowerMachines() {
        // Headless: the charge is kept in the stand-in item's bytes ("id|joules").
        installChargeAccess();
        {
            FakeWorld world = FakeWorld.singleRegion(this.engine);
            MachineInstanceImpl cube = place(this.engine, world, "basic_energy_cube");
            fill(cube, "energy", MekanizedContent.ENERGY.stack(100_000));
            fill(cube, "charge", item("mekanized:energy_tablet").stack(1));
            world.tick(10);
            ResourceVariant tablet = cube.inspect().storages().get("charge").getFirst().variant();
            assertEquals(40_000, EnergyItems.capacity(tablet) > 0 ? chargeOf(tablet) : -1,
                    "the basic cube charges at its output rate, 4 kJ per tick");
            assertEquals(60_000, energy(cube));

            MachineInstanceImpl chamber = world.place(type(this.engine, "enrichment_chamber"), FakeWorld.at(3, 64, 0), Direction.NORTH);
            fill(chamber, "energy_slot", tablet.stack(1));
            world.tick(2);
            assertTrue(energy(chamber) >= 10_000, "the tablet powers the machine: " + energy(chamber));
            assertEquals(40_000 - energy(chamber), chargeOf(chamber.inspect().storages().get("energy_slot").getFirst().variant()),
                    "energy is conserved");
        }
    }

    @Test
    void energyCubesChargeGear() {
        installChargeAccess();
        {
            FakeWorld world = FakeWorld.singleRegion(this.engine);
            MachineInstanceImpl cube = place(this.engine, world, "ultimate_energy_cube");
            fill(cube, "energy", MekanizedContent.ENERGY.stack(100_000_000));
            for (String gear : List.of("mekasuit_bodyarmor", "atomic_disassembler", "free_runners")) {
                fill(cube, "charge", item("mekanized:" + gear).stack(1));
                world.tick(200);
                ResourceVariant charged = cube.inspect().storages().get("charge").getFirst().variant();
                assertEquals(EnergyItems.baseCapacity(MekanizedContent.key(gear)), chargeOf(charged),
                        gear + " charges to its capacity");
                try (Transaction transaction = this.engine.transactions().openOuter()) {
                    cube.context().storage("charge").extract(charged, 1, transaction);
                    transaction.commit();
                }
            }
        }
    }

    @Test
    void transportSidesPullFromContainersAndFillOthers() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        var barrel = CombinedResourceHandler.slots(3, 64);
        var chest = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            barrel.insert(item("mekanized:osmium_ore"), 5, transaction);
            transaction.commit();
        }
        WorldPosition transporter = FakeWorld.at(0, 65, 0);
        world.setContainer(FakeWorld.at(0, 66, 0), barrel);
        world.setContainer(FakeWorld.at(1, 65, 0), chest);
        world.placeTransport(LogisticsMachines.TRANSPORTERS.get(0), transporter);
        var networks = this.engine.machines().networks();

        world.tick(25);
        assertEquals(5, barrel.amountOf(item("mekanized:osmium_ore")), "default: containers are only filled, never emptied");

        networks.setSideMode(transporter, Direction.UP, TransportSideMode.PULL);
        world.tick(25);
        assertEquals(5, chest.amountOf(item("mekanized:osmium_ore")), "a pull side empties the barrel into the chest");
        assertEquals(0, barrel.amountOf(item("mekanized:osmium_ore")));

        networks.setSideMode(transporter, Direction.EAST, TransportSideMode.PULL);
        networks.setSideMode(transporter, Direction.UP, TransportSideMode.PUSH);
        world.tick(25);
        assertEquals(5, barrel.amountOf(item("mekanized:osmium_ore")), "and back: pull the chest, push into the barrel");

        networks.setSideMode(transporter, Direction.UP, TransportSideMode.NONE);
        MachineInstanceImpl chamber = place(this.engine, world, "enrichment_chamber"); // below the transporter, at (0, 64, 0)
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            chest.insert(item("mekanized:osmium_ore"), 3, transaction);
            transaction.commit();
        }
        world.tick(25);
        long started = chamber.context().process().current() == null ? 0 : 1; // one ore may be in the running process
        assertEquals(3, amount(chamber, "input", "mekanized:osmium_ore") + started, "the chest's pull side feeds the chamber");
        assertEquals(5, barrel.amountOf(item("mekanized:osmium_ore")), "a side set to none is disconnected");
    }

    @Test
    void aSideSetToNoneCutsTwoTransportsApart() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        var barrel = CombinedResourceHandler.slots(3, 64);
        var chest = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            barrel.insert(item("minecraft:cobblestone"), 6, transaction);
            transaction.commit();
        }
        world.setContainer(FakeWorld.at(0, 64, 0), barrel);
        world.setContainer(FakeWorld.at(3, 64, 0), chest);
        WorldPosition first = FakeWorld.at(1, 64, 0);
        WorldPosition second = FakeWorld.at(2, 64, 0);
        var transporter = LogisticsMachines.TRANSPORTERS.get(0);
        world.placeTransport(transporter, first);
        world.placeTransport(transporter, second);
        var networks = this.engine.machines().networks();
        networks.setSideMode(first, Direction.WEST, TransportSideMode.PULL);
        networks.setSideMode(first, Direction.EAST, TransportSideMode.NONE);
        world.tick(25);
        assertEquals(0, chest.amountOf(item("minecraft:cobblestone")), "the two transporters are not linked");
        assertEquals(6, barrel.amountOf(item("minecraft:cobblestone")));
        assertEquals(Set.of(), networks.connections(second, world, List.of(item("minecraft:cobblestone")))
                .stream().filter(side -> side == Direction.WEST).collect(Collectors.toSet()), "no arm toward the cut side");

        networks.setSideMode(first, Direction.EAST, TransportSideMode.DEFAULT);
        world.tick(25);
        assertEquals(6, chest.amountOf(item("minecraft:cobblestone")), "linked again");
    }

    private static Key mekanizedKey(String name) {
        return MekanizedContent.key(name);
    }

    private static long extractThrough(MachineInstanceImpl machine, Direction side, String itemId, long amount) {
        var handler = machine.capability(Capabilities.RESOURCE_HANDLER, side);
        if (handler.isEmpty()) return 0;
        try (Transaction transaction = machine.services().transactions().openOuter()) {
            long moved = handler.get().extract(item(itemId), amount, transaction);
            transaction.commit();
            return moved;
        }
    }

    // -- Phase 6 ---------------------------------------------------------------------------------

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

    @Test
    void aGasBurningGeneratorBurnsHydrogen() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl generator = place(this.engine, world, "gas_burning_generator");
        fill(generator, "fuel", chemical(this.engine, "hydrogen", 10));
        world.tick(20);
        assertEquals(10 * 200, energy(generator), "200 J per mB of hydrogen");
        assertEquals(0, amountOfType(generator, "fuel", "hydrogen"));
    }

}
