package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldFluid;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.UtilityBehaviors;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Objects;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 2 utility blocks, headless: bins, plenisher, assemblicator, lasers, alarm. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class UtilityTest {
    private static final WorldPosition HERE = FakeWorld.at(0, 64, 0);
    private static final Key STONE = Key.key("minecraft", "stone");

    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void aBinHoldsManyOfOneItem() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl bin = world.place(type(this.engine, "basic_bin"), HERE, Direction.NORTH);
        assertEquals(4_096, insert(bin, "items", item("minecraft:cobblestone"), 5_000), "a basic bin holds 4096");
        assertEquals(0, insert(bin, "items", item("minecraft:dirt"), 1), "and one kind of item");
    }

    @Test
    void aCreativeBinStaysFull() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl bin = world.place(type(this.engine, "creative_bin"), HERE, Direction.NORTH);
        insert(bin, "items", item("minecraft:diamond"), 1);
        world.tick(2);
        assertEquals(Long.MAX_VALUE / 4, amount(bin, "items"), "filled with its item");
    }

    @Test
    void aFluidicPlenisherFillsTheSpaceBelowIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl plenisher = world.place(type(this.engine, "fluidic_plenisher"), HERE, Direction.NORTH);
        fill(plenisher, "energy", MekanizedContent.ENERGY.stack(40_000));
        fill(plenisher, "fluid", Objects.requireNonNull(MekanizedContent.fluid("water")).stack(2_500));
        world.tick(61);
        Key water = Key.key("minecraft", "water");
        assertEquals(new WorldFluid(water, true), world.fluidAt(FakeWorld.at(0, 63, 0)), "a source right below");
        assertEquals(500, amount(plenisher, "fluid"), "1000 mB per block, two blocks");
        assertEquals(40_000 - 2 * 2_000, amount(plenisher, "energy"), "2 kJ per block");
    }

    @Test
    void aFormulaicAssemblicatorCraftsItsPattern() {
        ResourceVariant plank = item("minecraft:oak_planks");
        ResourceVariant table = item("minecraft:crafting_table");
        UtilityBehaviors.setCrafting((world, grid) -> grid.stream().filter(Objects::nonNull).count() == 4 && grid.get(0) != null
                && grid.get(0).equals(plank) ? table.stack(1) : null);
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl assemblicator = world.place(type(this.engine, "formulaic_assemblicator"), HERE, Direction.NORTH);
        fill(assemblicator, "energy", MekanizedContent.ENERGY.stack(40_000));
        fill(assemblicator, "pattern", plank.stack(4)); // one per slot: the first four cells
        fill(assemblicator, "input", plank.stack(8));
        world.tick(2);
        // The first four cells are 0, 1, 2 and 3: not a 2x2, but the stub only counts planks.
        assertEquals(1, amountOf(assemblicator, "output", table), "4 planks made a table");
        assertEquals(4, amountOf(assemblicator, "input", plank), "4 planks used");
        assertEquals(4, amountOf(assemblicator, "pattern", plank), "the pattern stays");
    }

    @Test
    void aLaserBreaksTheBlockItFacesAfterEnoughEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl laser = world.place(type(this.engine, "laser"), HERE, Direction.NORTH);
        fill(laser, "energy", MekanizedContent.ENERGY.stack(2_000_000));
        WorldPosition target = HERE.relative(Direction.NORTH).relative(Direction.NORTH).relative(Direction.NORTH);
        world.setBlock(target, STONE);
        world.tick(20);
        assertEquals(STONE, world.blockAt(target), "stone takes 150 kJ: 30 ticks at 5 kJ");
        world.tick(15);
        assertEquals(Key.key("minecraft", "air"), world.blockAt(target), "broken");
        assertEquals(1, world.itemsAt(target).size(), "its drop lies there");
    }

    @Test
    void aLaserFeedsAnAmplifierThatHoldsItUnderRedstoneControl() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl laser = world.place(type(this.engine, "laser"), HERE, Direction.NORTH);
        WorldPosition at = HERE.relative(Direction.NORTH).relative(Direction.NORTH);
        MachineInstanceImpl amplifier = world.place(type(this.engine, "laser_amplifier"), at, Direction.NORTH);
        // High: fires only while powered, and nothing powers it.
        amplifier.context().set(UtilityMachines.REDSTONE, "high");
        fill(laser, "energy", MekanizedContent.ENERGY.stack(100_000));
        world.tick(30);
        assertEquals(100_000, amount(amplifier, "energy"), "the laser's energy went into the amplifier");
        assertEquals(0, amount(laser, "energy"));
    }

    @Test
    void aTractorBeamKeepsTheDropsOfWhatItBreaks() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tractor = world.place(type(this.engine, "laser_tractor_beam"), HERE, Direction.NORTH);
        WorldPosition target = HERE.relative(Direction.NORTH);
        world.setBlock(target, STONE);
        fill(tractor, "energy", MekanizedContent.ENERGY.stack(1_000_000));
        world.tick(3);
        assertEquals(Key.key("minecraft", "air"), world.blockAt(target));
        assertEquals(1, amountOf(tractor, "items", item("minecraft:stone")), "the stone was collected");
        assertTrue(world.itemsAt(target).isEmpty());
    }

    @Test
    void anIndustrialAlarmSoundsWhilePowered() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl alarm = world.place(type(this.engine, "industrial_alarm"), HERE, Direction.NORTH);
        world.tick(2);
        assertFalse(alarm.isActive());
        world.setRedstone(HERE, 15);
        world.tick(41);
        assertTrue(alarm.isActive());
    }

    @Test
    void anOredictionificatorKeepsStandardItems() {
        UtilityBehaviors.setItemFactory(id -> item(id.asString()));
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl oredict = world.place(type(this.engine, "oredictionificator"), HERE, Direction.NORTH);
        ResourceVariant ingot = item("mekanized:ingot_osmium");
        fill(oredict, "input", ingot.stack(5));
        world.tick(2);
        assertEquals(5, amountOf(oredict, "output", ingot), "already the standard item: passed through");
    }

    // ------------------------------------------------------------------------------------------

}
