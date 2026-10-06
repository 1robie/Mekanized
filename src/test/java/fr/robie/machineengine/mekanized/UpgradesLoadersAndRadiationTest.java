package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.api.error.Result;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.definition.DefinitionCompiler;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.test.TestEngine;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 6, headless: the last upgrades, chunk loading, the quantum entangloporter. */
class UpgradesLoadersAndRadiationTest {
    private static final WorldPosition HERE = FakeWorld.at(0, 64, 0);

    private final CoreMachineEngine engine = TestEngine.builder()
            .addon(AddonRegistration.builder("Mekanized", "1.0.0").register(MekanizedContent::register).build())
            .definitions(MekanizedChainTest::definitions, new DefinitionCompiler.ItemResolver() {
                @Override
                public Result<ResourceVariant> item(Key id) {
                    return Result.ok(MekanizedChainTest.item(id.asString()));
                }
            })
            .start();

    @BeforeEach
    void forget() {
        ChunkLoaders.loaders().clear();
        Entangloporters.buffers().clear();
        Radiation.sources().clear();
    }

    @Test
    void radiationFadesWithDistanceAndTime() {
        Radiation.release(HERE, 1.0);
        assertEquals(1.0 + Radiation.BACKGROUND, Radiation.levelAt(FakeWorld.WORLD, 0.5, 64.5, 0.5), 1e-9, "at the source");
        assertEquals(0.01 + Radiation.BACKGROUND, Radiation.levelAt(FakeWorld.WORLD, 10.5, 64.5, 0.5), 1e-9, "over the squared distance");
        Radiation.decay();
        assertEquals(Radiation.DECAY_PER_SECOND, Radiation.sources().get(HERE), 1e-12);
    }

    @Test
    void theLiquifierTurnsFoodIntoPaste() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl liquifier = world.place(type("nutritional_liquifier"), HERE, Direction.NORTH);
        fill(liquifier, "energy", MekanizedContent.ENERGY.stack(40_000));
        fill(liquifier, "input", MekanizedChainTest.item("minecraft:bread").stack(2));
        liquifier.wake(WakeReason.CONFIG_CHANGED);
        world.tick(150);
        assertEquals(500, amountOf(liquifier, "paste", MekanizedContent.fluid("nutritional_paste").variant()), "bread: 5 points, 250 mB");
    }

    @Test
    void aWasteBarrelLetsItsWasteDecay() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl barrel = world.place(type("radioactive_waste_barrel"), HERE, Direction.NORTH);
        fill(barrel, "waste", MekanizedContent.chemical("nuclear_waste").stack(100));
        world.tick(100);
        long left = amountOf(barrel, "waste", MekanizedContent.chemical("nuclear_waste").variant());
        assertTrue(left < 100 && left >= 94, "a millibucket a second: " + left);
    }

    @Test
    void energyAndChemicalUpgradesLowerOnlyTheirOwnCosts() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl compressor = world.place(type("osmium_compressor"), HERE, Direction.NORTH);
        var osmium = MekanizedContent.chemical("osmium");
        world.runInRegionOf(HERE, () -> assertTrue(compressor.installUpgrade(MekanizedRegistry.ENERGY_UPGRADE, 2).isOk()));
        assertEquals(640, compressor.context().modifiedCost(MekanizedContent.ENERGY, 1_000), "-20 % twice");
        assertEquals(1_000, compressor.context().modifiedCost(osmium, 1_000), "energy upgrades leave chemicals alone");
        world.runInRegionOf(HERE, () -> assertTrue(compressor.installUpgrade(MekanizedRegistry.CHEMICAL_UPGRADE, 1).isOk()));
        assertEquals(800, compressor.context().modifiedCost(osmium, 1_000));
        MachineInstanceImpl crusher = world.place(type("crusher"), FakeWorld.at(5, 64, 0), Direction.NORTH);
        world.runInRegionOf(crusher.position(), () -> assertFalse(crusher.installUpgrade(MekanizedRegistry.CHEMICAL_UPGRADE, 1).isOk(),
                "the crusher uses no chemical"));
    }

    @Test
    void aFilteredPumpSeparatesHeavyWater() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl pump = world.place(type("electric_pump"), HERE, Direction.NORTH);
        world.runInRegionOf(HERE, () -> assertTrue(pump.installUpgrade(MekanizedRegistry.FILTER_UPGRADE, 1).isOk()));
        fill(pump, "energy", MekanizedContent.ENERGY.stack(40_000));
        world.setFluid(FakeWorld.at(0, 63, 0), new WorldFluid(Key.key("minecraft", "water"), true));
        world.tick(2);
        assertEquals(10, amountOf(pump, "fluid", MekanizedContent.fluid("heavy_water").variant()), "10 mB of heavy water a block");
    }

    @Test
    void theStoneGeneratorFillsWhatTheMinerDigs() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type("digital_miner"), HERE, Direction.NORTH);
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
        MachineInstanceImpl stabilizer = world.place(type("dimensional_stabilizer"), HERE, Direction.NORTH);
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
    void anAnchorUpgradeKeepsItsMachinesChunkLoaded() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl crusher = world.place(type("crusher"), HERE, Direction.NORTH);
        world.runInRegionOf(HERE, () -> assertTrue(crusher.installUpgrade(MekanizedRegistry.ANCHOR_UPGRADE, 1).isOk()));
        assertEquals(0, ChunkLoaders.loaders().get(HERE));
        world.remove(HERE);
        assertNull(ChunkLoaders.loaders().get(HERE), "removed with its machine");
    }

    @Test
    void entangloportersShareTheirFrequencyAcrossRegions() {
        WorldPosition far = FakeWorld.at(1_000, 64, 0);
        FakeWorld world = FakeWorld.regions(this.engine, position -> position.x() < 500 ? "west" : "east");
        MachineInstanceImpl sender = world.place(type("quantum_entangloporter"), HERE, Direction.NORTH);
        MachineInstanceImpl receiver = world.place(type("quantum_entangloporter"), far, Direction.NORTH);
        sender.context().set(AutomationMachines.FREQUENCY, "link");
        receiver.context().set(AutomationMachines.FREQUENCY, "link");
        receiver.context().set(Entangloporters.MODE, "receive");
        sender.wake(WakeReason.CONFIG_CHANGED);
        receiver.wake(WakeReason.CONFIG_CHANGED);
        fill(sender, "energy", MekanizedContent.ENERGY.stack(100_000));
        fill(sender, "items", MekanizedChainTest.item("minecraft:dirt").stack(5));
        fill(sender, "fluid", MekanizedContent.fluid("water").stack(3_000));
        world.tick(5);
        assertEquals(100_000, amountOf(receiver, "energy", MekanizedContent.ENERGY.variant()));
        assertEquals(5, amountOf(receiver, "items", MekanizedChainTest.item("minecraft:dirt")));
        assertEquals(3_000, amountOf(receiver, "fluid", MekanizedContent.fluid("water").variant()));
        assertEquals(0, amountOf(sender, "energy", MekanizedContent.ENERGY.variant()), "nothing made: it moved");
    }

    // ------------------------------------------------------------------------------------------

    private MachineType type(String name) {
        return this.engine.registries().registry(Registries.MACHINE_TYPE).getOrThrow(MekanizedContent.key(name));
    }

    private static void fill(MachineInstanceImpl machine, String storage, ResourceStack stack) {
        try (Transaction transaction = machine.services().transactions().openOuter()) {
            long inserted = machine.context().storage(storage).insert(stack.variant(), stack.amount(), transaction);
            assertEquals(stack.amount(), inserted, stack + " fits in " + storage);
            transaction.commit();
        }
    }

    private static long amountOf(MachineInstanceImpl machine, String storage, ResourceVariant variant) {
        return machine.inspect().storages().get(storage).stream().filter(view -> variant.equals(view.variant()))
                .mapToLong(StorageView::amount).sum();
    }
}
