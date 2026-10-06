package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.api.error.Result;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.definition.DefinitionCompiler;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.test.TestEngine;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 4, headless: Digital Miner, teleporter frames and frequencies, QIO. */
class AutomationTest {
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
        Frequencies.clear();
    }

    @Test
    void aDigitalMinerMinesOresBelowIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type("digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(2, 60, -1), Key.key("minecraft", "iron_ore"));
        world.setBlock(FakeWorld.at(1, 58, 1), Key.key("minecraft", "stone"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "50");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(1, amountOf(miner, "output", MekanizedChainTest.item("minecraft:iron_ore")), "the ore: " + miner.context().get(AutomationMachines.STATUS));
        assertEquals(Key.key("minecraft", "stone"), world.blockAt(FakeWorld.at(1, 58, 1)), "no filters: only ores");
    }

    @Test
    void aDigitalMinerFollowsItsFilters() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type("digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(0, 62, 0), Key.key("minecraft", "coal_ore"));
        world.setBlock(FakeWorld.at(0, 61, 0), Key.key("minecraft", "stone"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        fill(miner, "filter", MekanizedChainTest.item("minecraft:stone").stack(1));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "55");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(1, amountOf(miner, "output", MekanizedChainTest.item("minecraft:stone")));
        assertEquals(Key.key("minecraft", "coal_ore"), world.blockAt(FakeWorld.at(0, 62, 0)), "not in the filters");
    }

    @Test
    void teleportersWithFramesLinkByFrequency() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        WorldPosition first = teleporter(world, 0);
        WorldPosition second = teleporter(world, 20);
        world.machine(first).context().set(AutomationMachines.FREQUENCY, "home");
        world.machine(second).context().set(AutomationMachines.FREQUENCY, "home");
        world.tick(45);
        assertTrue(Frequencies.teleporters().get(first).formed(), "the frame is complete");
        assertEquals(second, Frequencies.destination(first, "home"));
        assertEquals(first, Frequencies.destination(second, "home"));
    }

    /** A teleporter at (x, 64, 0) in its frame along x. */
    private WorldPosition teleporter(FakeWorld world, int x) {
        WorldPosition at = FakeWorld.at(x, 64, 0);
        world.place(type("teleporter"), at, Direction.NORTH);
        for (int dy = 0; dy <= 3; dy++) {
            world.place(type("teleporter_frame"), FakeWorld.at(x - 1, 64 + dy, 0), Direction.NORTH);
            world.place(type("teleporter_frame"), FakeWorld.at(x + 1, 64 + dy, 0), Direction.NORTH);
        }
        world.place(type("teleporter_frame"), FakeWorld.at(x, 67, 0), Direction.NORTH);
        return at;
    }

    @Test
    void qioImportersAndExportersMoveItemsThroughDriveArrays() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type("qio_drive_array"), FakeWorld.at(5, 64, 5), Direction.NORTH);
        fill(array, "drives", MekanizedChainTest.item("mekanized:qio_drive_base").stack(1));
        MachineInstanceImpl importer = world.place(type("qio_importer"), HERE, Direction.NORTH);
        MachineInstanceImpl exporter = world.place(type("qio_exporter"), FakeWorld.at(10, 64, 0), Direction.NORTH);
        for (MachineInstanceImpl machine : new MachineInstanceImpl[]{array, importer, exporter}) {
            machine.context().set(AutomationMachines.FREQUENCY, "base");
        }
        ResourceHandler chest = CombinedResourceHandler.slots(3, 64);
        ResourceHandler barrel = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            chest.insert(MekanizedChainTest.item("minecraft:cobblestone"), 40, transaction);
            chest.insert(MekanizedChainTest.item("minecraft:dirt"), 10, transaction);
            transaction.commit();
        }
        world.setContainer(FakeWorld.at(0, 64, -1), chest);
        world.setContainer(FakeWorld.at(10, 64, -1), barrel);
        world.tick(45);
        assertEquals(50, amount(array, "items"), "the importer emptied the chest into the array");
        fill(exporter, "filter", MekanizedChainTest.item("minecraft:dirt").stack(1));
        world.tick(45);
        assertEquals(10, barrel.views().stream().mapToLong(StorageView::amount).sum(), "the exporter took out the dirt only");
        assertEquals(40, amount(array, "items"));
        assertNotNull(Frequencies.arrays().get(FakeWorld.at(5, 64, 5)), "the array publishes what it holds");
    }

    @Test
    void driveArraysHoldWhatTheirDrivesAllow() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type("qio_drive_array"), HERE, Direction.NORTH);
        var context = array.context();
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            assertEquals(0, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    MekanizedChainTest.item("minecraft:dirt"), 10, transaction), "no drive, no room");
        }
        fill(array, "drives", MekanizedChainTest.item("mekanized:qio_drive_base").stack(1));
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            assertEquals(16_000, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    MekanizedChainTest.item("minecraft:dirt"), 20_000, transaction), "a base drive holds 16,000 items");
        }
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

    private static long amount(MachineInstanceImpl machine, String storage) {
        return machine.inspect().storages().get(storage).stream().mapToLong(StorageView::amount).sum();
    }

    private static long amountOf(MachineInstanceImpl machine, String storage, ResourceVariant variant) {
        return machine.inspect().storages().get(storage).stream().filter(view -> variant.equals(view.variant()))
                .mapToLong(StorageView::amount).sum();
    }
}
