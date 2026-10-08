package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.test.FakeWorld;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 6, headless: the last upgrades, chunk loading, the quantum entangloporter. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class UpgradesLoadersAndRadiationTest {

    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void radiationFadesWithDistanceAndTime() {
        Radiation.release(HERE, 1.0);
        assertEquals(1.0 + RadiationSettings.background, Radiation.levelAt(FakeWorld.WORLD, 0.5, 64.5, 0.5), 1e-9, "at the source");
        assertEquals(0.01 + RadiationSettings.background, Radiation.levelAt(FakeWorld.WORLD, 10.5, 64.5, 0.5), 1e-9, "over the squared distance");
        Radiation.decay();
        assertEquals(RadiationSettings.sourceDecayPerSecond, Radiation.sources().get(HERE), 1e-12);
    }

    @Test
    void theLiquifierTurnsFoodIntoPaste() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl liquifier = world.place(type(this.engine, "nutritional_liquifier"), HERE, Direction.NORTH);
        fill(liquifier, "energy", MekanizedContent.ENERGY.stack(40_000));
        fill(liquifier, "input", item("minecraft:bread").stack(2));
        liquifier.wake(WakeReason.CONFIG_CHANGED);
        world.tick(150);
        assertEquals(500, amountOf(liquifier, "paste", MekanizedContent.fluid("nutritional_paste").variant()), "bread: 5 points, 250 mB");
    }

    @Test
    void aWasteBarrelLetsItsWasteDecay() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl barrel = world.place(type(this.engine, "radioactive_waste_barrel"), HERE, Direction.NORTH);
        fill(barrel, "waste", MekanizedContent.chemical("nuclear_waste").stack(100));
        world.tick(100);
        long left = amountOf(barrel, "waste", MekanizedContent.chemical("nuclear_waste").variant());
        assertTrue(left < 100 && left >= 94, "a millibucket a second: " + left);
    }

    @Test
    void theRadiationSettingsChangeBackgroundAndDecay() {
        RadiationSettings.background = 0.5;
        RadiationSettings.sourceDecayPerSecond = 0.5;
        RadiationSettings.minSource = 0.3;
        Radiation.release(HERE, 1.0);
        assertEquals(1.5, Radiation.levelAt(FakeWorld.WORLD, 0.5, 64.5, 0.5), 1e-9, "the configured background");
        Radiation.decay();
        assertEquals(0.5, Radiation.sources().get(HERE), 1e-12, "the configured decay");
        Radiation.decay();
        assertNull(Radiation.sources().get(HERE), "below the configured minimum: forgotten");
    }

    @Test
    void aWasteBarrelDecaysAtTheConfiguredRate() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        world.setOptions(HERE, MekanizedTestKit.options("radioactive_waste_barrel", Map.of("decay-per-second", 10)));
        MachineInstanceImpl barrel = world.place(type(this.engine, "radioactive_waste_barrel"), HERE, Direction.NORTH);
        fill(barrel, "waste", MekanizedContent.chemical("nuclear_waste").stack(100));
        world.tick(100);
        long left = amountOf(barrel, "waste", MekanizedContent.chemical("nuclear_waste").variant());
        assertTrue(left <= 60 && left >= 40, "ten millibuckets a second: " + left);
    }

    @Test
    void energyAndChemicalUpgradesLowerOnlyTheirOwnCosts() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl compressor = world.place(type(this.engine, "osmium_compressor"), HERE, Direction.NORTH);
        var osmium = MekanizedContent.chemical("osmium");
        world.runInRegionOf(HERE, () -> assertTrue(compressor.installUpgrade(MekanizedRegistry.ENERGY_UPGRADE, 2).isOk()));
        assertEquals(640, compressor.context().modifiedCost(MekanizedContent.ENERGY, 1_000), "-20 % twice");
        assertEquals(1_000, compressor.context().modifiedCost(osmium, 1_000), "energy upgrades leave chemicals alone");
        world.runInRegionOf(HERE, () -> assertTrue(compressor.installUpgrade(MekanizedRegistry.CHEMICAL_UPGRADE, 1).isOk()));
        assertEquals(800, compressor.context().modifiedCost(osmium, 1_000));
        MachineInstanceImpl crusher = world.place(type(this.engine, "crusher"), FakeWorld.at(5, 64, 0), Direction.NORTH);
        world.runInRegionOf(crusher.position(), () -> assertFalse(crusher.installUpgrade(MekanizedRegistry.CHEMICAL_UPGRADE, 1).isOk(),
                "the crusher uses no chemical"));
    }

    @Test
    void aFilteredPumpSeparatesHeavyWater() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl pump = world.place(type(this.engine, "electric_pump"), HERE, Direction.NORTH);
        world.runInRegionOf(HERE, () -> assertTrue(pump.installUpgrade(MekanizedRegistry.FILTER_UPGRADE, 1).isOk()));
        fill(pump, "energy", MekanizedContent.ENERGY.stack(40_000));
        world.setFluid(FakeWorld.at(0, 63, 0), new WorldFluid(Key.key("minecraft", "water"), true));
        world.tick(2);
        assertEquals(10, amountOf(pump, "fluid", MekanizedContent.fluid("heavy_water").variant()), "10 mB of heavy water a block");
    }

    @Test
    void theStoneGeneratorFillsWhatTheMinerDigs() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type(this.engine, "digital_miner"), HERE, Direction.NORTH);
        world.runInRegionOf(HERE, () -> assertTrue(miner.installUpgrade(MekanizedRegistry.STONE_GENERATOR_UPGRADE, 1).isOk()));
        world.setBlock(FakeWorld.at(1, 60, 1), Key.key("minecraft", "iron_ore"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "55");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(Key.key("minecraft", "stone"), world.blockAt(FakeWorld.at(1, 60, 1)));
    }

    @Test
    void aStabilizerLoadsChunksWhileItHasEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl stabilizer = world.place(type(this.engine, "dimensional_stabilizer"), HERE, Direction.NORTH);
        stabilizer.context().set(ChunkLoaders.RADIUS, "1");
        // 9 chunks x 500 J/t x 20 ticks = 90 kJ a second: two seconds' worth.
        fill(stabilizer, "energy", MekanizedContent.ENERGY.stack(180_000));
        stabilizer.wake(WakeReason.CONFIG_CHANGED);
        world.tick(1);
        assertEquals(1, ChunkLoaders.loaders().get(HERE));
        world.tick(60);
        assertNull(ChunkLoaders.loaders().get(HERE), "out of energy: the chunks may unload");
    }

    @Test
    void theStabilizersRadiusIsLimitedBySettings() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl stabilizer = world.place(type(this.engine, "dimensional_stabilizer"), HERE, Direction.NORTH);
        stabilizer.context().set(ChunkLoaders.RADIUS, "3");
        fill(stabilizer, "energy", MekanizedContent.ENERGY.stack(1_000_000));
        stabilizer.wake(WakeReason.CONFIG_CHANGED);
        world.tick(1);
        assertEquals(2, ChunkLoaders.loaders().get(HERE), "the default limit: a 5 x 5 area");
        ChunkLoadingSettings.maxRadius = 3;
        stabilizer.wake(WakeReason.CONFIG_CHANGED);
        world.tick(1);
        assertEquals(3, ChunkLoaders.loaders().get(HERE), "a raised limit is used");
    }

    @Test
    void aStabilizerPaysTheConfiguredEnergyPerChunk() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        world.setOptions(HERE, MekanizedTestKit.options("dimensional_stabilizer", Map.of("energy-per-chunk", 10_000)));
        MachineInstanceImpl stabilizer = world.place(type(this.engine, "dimensional_stabilizer"), HERE, Direction.NORTH);
        // Its own chunk only: 10 kJ x 20 ticks = 200 kJ a second, more than it holds.
        fill(stabilizer, "energy", MekanizedContent.ENERGY.stack(150_000));
        stabilizer.wake(WakeReason.CONFIG_CHANGED);
        world.tick(1);
        assertNull(ChunkLoaders.loaders().get(HERE), "too expensive for what it holds");
    }

    @Test
    void anAnchorUpgradeKeepsItsMachinesChunkLoaded() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl crusher = world.place(type(this.engine, "crusher"), HERE, Direction.NORTH);
        world.runInRegionOf(HERE, () -> assertTrue(crusher.installUpgrade(MekanizedRegistry.ANCHOR_UPGRADE, 1).isOk()));
        assertEquals(0, ChunkLoaders.loaders().get(HERE));
        world.remove(HERE);
        assertNull(ChunkLoaders.loaders().get(HERE), "removed with its machine");
    }

    @Test
    void entangloportersShareTheirFrequencyAcrossRegions() {
        WorldPosition far = FakeWorld.at(1_000, 64, 0);
        FakeWorld world = FakeWorld.regions(this.engine, position -> position.x() < 500 ? "west" : "east");
        MachineInstanceImpl sender = world.place(type(this.engine, "quantum_entangloporter"), HERE, Direction.NORTH);
        MachineInstanceImpl receiver = world.place(type(this.engine, "quantum_entangloporter"), far, Direction.NORTH);
        sender.context().set(AutomationMachines.FREQUENCY, "link");
        receiver.context().set(AutomationMachines.FREQUENCY, "link");
        receiver.context().set(Entangloporters.MODE, "receive");
        sender.wake(WakeReason.CONFIG_CHANGED);
        receiver.wake(WakeReason.CONFIG_CHANGED);
        fill(sender, "energy", MekanizedContent.ENERGY.stack(100_000));
        fill(sender, "items", item("minecraft:dirt").stack(5));
        fill(sender, "fluid", MekanizedContent.fluid("water").stack(3_000));
        world.tick(5);
        assertEquals(100_000, amountOf(receiver, "energy", MekanizedContent.ENERGY.variant()));
        assertEquals(5, amountOf(receiver, "items", item("minecraft:dirt")));
        assertEquals(3_000, amountOf(receiver, "fluid", MekanizedContent.fluid("water").variant()));
        assertEquals(0, amountOf(sender, "energy", MekanizedContent.ENERGY.variant()), "nothing made: it moved");
    }

}
