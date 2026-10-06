package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.capability.Capabilities;
import fr.robie.machineengine.api.machine.WakeReason;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.EjectBehavior;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Side configuration, auto-eject and factories end to end, headless. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class FactoryAndSidesChainTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

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
}
