package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.transfer.ResourceHandler;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 4, headless: Digital Miner, teleporter frames and frequencies, QIO. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class AutomationTest {

    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void theMinerPaysTheConfiguredEnergyPerBlock() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        DigitalMinerSettings.energyPerBlock = 60_000;
        MachineInstanceImpl miner = world.place(type(this.engine, "digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(2, 60, -1), Key.key("minecraft", "iron_ore"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "50");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(0, amountOf(miner, "output", item("minecraft:iron_ore")), "more than it holds per block");
        assertEquals("Needs energy", miner.context().get(AutomationMachines.STATUS));
    }

    @Test
    void theMinersRadiusIsLimitedBySettings() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        DigitalMinerSettings.maxRadius = 4;
        MachineInstanceImpl miner = world.place(type(this.engine, "digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(6, 60, 0), Key.key("minecraft", "iron_ore"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        miner.context().set(AutomationMachines.RADIUS, "10");
        miner.context().set(AutomationMachines.MIN_Y, "50");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(0, amountOf(miner, "output", item("minecraft:iron_ore")), "radius 10 is cut to 4");
        DigitalMinerSettings.maxRadius = 32;
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(1, amountOf(miner, "output", item("minecraft:iron_ore")), "a raised limit reaches it");
    }

    @Test
    void aDigitalMinerMinesOresBelowIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type(this.engine, "digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(2, 60, -1), Key.key("minecraft", "iron_ore"));
        world.setBlock(FakeWorld.at(1, 58, 1), Key.key("minecraft", "stone"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "50");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(1, amountOf(miner, "output", item("minecraft:iron_ore")), "the ore: " + miner.context().get(AutomationMachines.STATUS));
        assertEquals(Key.key("minecraft", "stone"), world.blockAt(FakeWorld.at(1, 58, 1)), "no filters: only ores");
    }

    @Test
    void aDigitalMinerFollowsItsFilters() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl miner = world.place(type(this.engine, "digital_miner"), HERE, Direction.NORTH);
        world.setBlock(FakeWorld.at(0, 62, 0), Key.key("minecraft", "coal_ore"));
        world.setBlock(FakeWorld.at(0, 61, 0), Key.key("minecraft", "stone"));
        fill(miner, "energy", MekanizedContent.ENERGY.stack(50_000));
        fill(miner, "filter", item("minecraft:stone").stack(1));
        miner.context().set(AutomationMachines.RADIUS, "4");
        miner.context().set(AutomationMachines.MIN_Y, "55");
        miner.context().set(AutomationMachines.RUNNING, true);
        miner.wake(WakeReason.CONFIG_CHANGED);
        world.tick(40);
        assertEquals(1, amountOf(miner, "output", item("minecraft:stone")));
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
        world.place(type(this.engine, "teleporter"), at, Direction.NORTH);
        for (int dy = 0; dy <= 3; dy++) {
            world.place(type(this.engine, "teleporter_frame"), FakeWorld.at(x - 1, 64 + dy, 0), Direction.NORTH);
            world.place(type(this.engine, "teleporter_frame"), FakeWorld.at(x + 1, 64 + dy, 0), Direction.NORTH);
        }
        world.place(type(this.engine, "teleporter_frame"), FakeWorld.at(x, 67, 0), Direction.NORTH);
        return at;
    }

    @Test
    void qioImportersAndExportersMoveItemsThroughDriveArrays() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type(this.engine, "qio_drive_array"), FakeWorld.at(5, 64, 5), Direction.NORTH);
        fill(array, "drives", item("mekanized:qio_drive_base").stack(1));
        MachineInstanceImpl importer = world.place(type(this.engine, "qio_importer"), HERE, Direction.NORTH);
        MachineInstanceImpl exporter = world.place(type(this.engine, "qio_exporter"), FakeWorld.at(10, 64, 0), Direction.NORTH);
        for (MachineInstanceImpl machine : new MachineInstanceImpl[]{array, importer, exporter}) {
            machine.context().set(AutomationMachines.FREQUENCY, "base");
        }
        ResourceHandler chest = CombinedResourceHandler.slots(3, 64);
        ResourceHandler barrel = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            chest.insert(item("minecraft:cobblestone"), 40, transaction);
            chest.insert(item("minecraft:dirt"), 10, transaction);
            transaction.commit();
        }
        world.setContainer(FakeWorld.at(0, 64, -1), chest);
        world.setContainer(FakeWorld.at(10, 64, -1), barrel);
        world.tick(45);
        assertEquals(50, amount(array, "items"), "the importer emptied the chest into the array");
        fill(exporter, "filter", item("minecraft:dirt").stack(1));
        world.tick(45);
        assertEquals(10, barrel.views().stream().mapToLong(StorageView::amount).sum(), "the exporter took out the dirt only");
        assertEquals(40, amount(array, "items"));
        assertNotNull(Frequencies.arrays().get(FakeWorld.at(5, 64, 5)), "the array publishes what it holds");
    }

    @Test
    void driveArraysHoldWhatTheirDrivesAllow() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type(this.engine, "qio_drive_array"), HERE, Direction.NORTH);
        var context = array.context();
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            assertEquals(0, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    item("minecraft:dirt"), 10, transaction), "no drive, no room");
        }
        fill(array, "drives", item("mekanized:qio_drive_base").stack(1));
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            assertEquals(16_000, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    item("minecraft:dirt"), 20_000, transaction), "a base drive holds 16,000 items");
        }
    }

    @Test
    void driveCapacitiesComeFromSettings() {
        QioSettings.driveItems = new long[]{100, 128_000, 1_048_000, 16_000_000};
        QioSettings.driveKinds = new long[]{1, 256, 1_024, 8_192};
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type(this.engine, "qio_drive_array"), HERE, Direction.NORTH);
        var context = array.context();
        fill(array, "drives", item("mekanized:qio_drive_base").stack(1));
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            assertEquals(100, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    item("minecraft:dirt"), 20_000, transaction), "the configured item limit");
            assertEquals(0, AutomationMachines.insertIntoArray(context.storage("drives"), context.storage("items"),
                    item("minecraft:stone"), 1, transaction), "the configured kind limit: one kind");
        }
    }

    @Test
    void importersMoveTheConfiguredItemsPerSecond() {
        QioSettings.ioRate = 5;
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl array = world.place(type(this.engine, "qio_drive_array"), FakeWorld.at(5, 64, 5), Direction.NORTH);
        fill(array, "drives", item("mekanized:qio_drive_base").stack(1));
        MachineInstanceImpl importer = world.place(type(this.engine, "qio_importer"), HERE, Direction.NORTH);
        array.context().set(AutomationMachines.FREQUENCY, "slow");
        importer.context().set(AutomationMachines.FREQUENCY, "slow");
        ResourceHandler chest = CombinedResourceHandler.slots(3, 64);
        try (Transaction transaction = this.engine.transactions().openOuter()) {
            chest.insert(item("minecraft:cobblestone"), 40, transaction);
            transaction.commit();
        }
        world.setContainer(FakeWorld.at(0, 64, -1), chest);
        world.tick(45);
        long moved = amount(array, "items");
        assertTrue(moved >= 5 && moved <= 15, "5 a second, not 64: " + moved);
    }

}
