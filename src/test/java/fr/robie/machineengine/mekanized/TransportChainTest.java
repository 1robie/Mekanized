package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.network.TransportSideMode;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.transfer.storage.CombinedResourceHandler;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sorters and transporters moving items between containers and machines, headless. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class TransportChainTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

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
}
