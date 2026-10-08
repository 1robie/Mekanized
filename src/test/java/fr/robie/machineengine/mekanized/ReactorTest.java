package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.multiblock.Fission;
import fr.robie.machineengine.mekanized.multiblock.Fusion;
import fr.robie.machineengine.mekanized.multiblock.ReactorMachines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;
import java.util.Map;

import static fr.robie.machineengine.mekanized.MekanizedTestKit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 wave 3, headless: heaters, boiler, turbine, fission and fusion reactors, SPS. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class ReactorTest {
    private final CoreMachineEngine engine = MekanizedTestKit.engine();

    @Test
    void aHeaterOnABoilerValveBoilsWater() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        // 4 x 3 x 3: inside, a pressure disperser and a superheating element; a valve on the front.
        Map<WorldPosition, String> blocks = new HashMap<>();
        blocks.put(FakeWorld.at(1, 65, 1), "pressure_disperser");
        blocks.put(FakeWorld.at(2, 65, 1), "superheating_element");
        blocks.put(FakeWorld.at(1, 65, 0), "boiler_valve");
        box(this.engine, world, 4, 3, 3, "boiler_casing", blocks);
        MachineInstanceImpl heater = world.place(type(this.engine, "resistive_heater"), FakeWorld.at(1, 65, -1), Direction.NORTH);
        fill(heater, "energy", MekanizedContent.ENERGY.stack(100_000));
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "water", fluid("water", 10_000));
        world.tick(200);
        assertTrue(amount(anchor, "steam") > 0, "heat boiled water: " + anchor.context().get(ReactorMachines.STATUS));
        assertEquals(10_000, amount(anchor, "steam") + amount(anchor, "water"), "1 mB of water, 1 mB of steam");
    }

    @Test
    void aTurbineMakesEnergyAndWaterFromSteam() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        Map<WorldPosition, String> blocks = new HashMap<>();
        blocks.put(FakeWorld.at(2, 65, 2), "turbine_rotor");
        blocks.put(FakeWorld.at(2, 66, 2), "rotational_complex");
        blocks.put(FakeWorld.at(1, 66, 1), "pressure_disperser");
        blocks.put(FakeWorld.at(1, 67, 1), "electromagnetic_coil");
        blocks.put(FakeWorld.at(3, 67, 3), "saturating_condenser");
        blocks.put(FakeWorld.at(2, 68, 2), "turbine_vent");
        box(this.engine, world, 5, 5, 5, "turbine_casing", blocks);
        fill(world.machine(FakeWorld.at(2, 65, 2)), "blades", item("mekanized:turbine_blade").stack(2));
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "steam", chemical(this.engine, "steam", 100_000));
        world.tick(41);
        assertTrue(amount(anchor, "energy") > 0, "steam turned the blades: " + anchor.context().get(ReactorMachines.STATUS));
        assertTrue(amount(anchor, "water") > 0, "the condenser gave water back");
    }

    @Test
    void aFissionReactorBurnsFuelIntoSteam() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        box(this.engine, world, 3, 3, 3, "fission_reactor_casing", Map.of(FakeWorld.at(1, 65, 1), "fission_fuel_assembly"));
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "fuel", chemical(this.engine, "fissile_fuel", 100));
        fill(anchor, "water", fluid("water", 100_000));
        anchor.context().set(Fission.BURN_RATE, "1");
        anchor.context().set(ReactorMachines.RUNNING, true);
        world.tick(41);
        assertTrue(amount(anchor, "waste") > 0, "fuel became waste: " + anchor.context().get(ReactorMachines.STATUS));
        assertTrue(amount(anchor, "steam") > 0, "water carried the heat away as steam");
    }

    @Test
    void anUncooledFissionReactorScrams() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        box(this.engine, world, 3, 3, 3, "fission_reactor_casing", Map.of(FakeWorld.at(1, 65, 1), "fission_fuel_assembly"));
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "fuel", chemical(this.engine, "fissile_fuel", 8_000));
        anchor.context().set(Fission.BURN_RATE, "1");
        anchor.context().set(ReactorMachines.RUNNING, true);
        world.tick(1_200);
        assertFalse(anchor.context().get(ReactorMachines.RUNNING), "too hot without coolant: " + anchor.context().get(ReactorMachines.STATUS));
        assertEquals(100, anchor.context().get(Fission.DAMAGE));
    }

    @Test
    void anIgnitedFusionReactorMakesEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        box(this.engine, world, 3, 3, 3, "fusion_reactor_frame", Map.of(FakeWorld.at(1, 65, 0), "fusion_reactor_controller",
                FakeWorld.at(1, 65, 2), "laser_focus_matrix"));
        // A laser aimed at the focus matrix, through it into the plasma.
        MachineInstanceImpl amplifier = world.place(type(this.engine, "laser_amplifier"), FakeWorld.at(1, 65, 4), Direction.NORTH);
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "deuterium", chemical(this.engine, "deuterium", 1_000));
        fill(anchor, "tritium", chemical(this.engine, "tritium", 1_000));
        fill(amplifier, "energy", MekanizedContent.ENERGY.stack(Fusion.FUSION_IGNITION.defaultValue()));
        world.tick(200);
        assertTrue(anchor.context().get(Fusion.IGNITED), "ignited: " + anchor.context().get(ReactorMachines.STATUS));
        assertTrue(amount(anchor, "energy") > 0);
        assertTrue(amount(anchor, "deuterium") < 1_000, "it burns its fuel");
    }

    @Test
    void anSpsTurnsPoloniumIntoAntimatter() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        box(this.engine, world, 3, 3, 3, "sps_casing", Map.of());
        world.tick(5);
        MachineInstanceImpl anchor = world.machine(FakeWorld.at(0, 64, 0));
        fill(anchor, "polonium", chemical(this.engine, "polonium", 2_000));
        fill(anchor, "energy", MekanizedContent.ENERGY.stack(2_000_000_000L));
        world.tick(21);
        assertEquals(2, amount(anchor, "antimatter"), "1,000 mB of polonium and 1 GJ per mB");
    }

    // ------------------------------------------------------------------------------------------

}
