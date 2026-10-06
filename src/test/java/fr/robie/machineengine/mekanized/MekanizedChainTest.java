package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.diagnostic.Diagnostic;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recipes, energy and upgrades, headless: Mekanism's recipes and tags (as installed on a server), Mekanized's
 * machines and the MachineEngine core. Items are stand-ins identified by id.
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
    void aGasBurningGeneratorBurnsHydrogen() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl generator = place(this.engine, world, "gas_burning_generator");
        fill(generator, "fuel", chemical(this.engine, "hydrogen", 10));
        world.tick(20);
        assertEquals(10 * 200, energy(generator), "200 J per mB of hydrogen");
        assertEquals(0, amountOfType(generator, "fuel", "hydrogen"));
    }
}
