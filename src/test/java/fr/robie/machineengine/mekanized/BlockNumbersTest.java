package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.BlockOptions;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.AutomationMachines;
import fr.robie.machineengine.mekanized.machine.ChemistryBehaviors;
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.machine.GaugeDropper;
import fr.robie.machineengine.mekanized.machine.HeatMachines;
import fr.robie.machineengine.mekanized.machine.Liquifier;
import fr.robie.machineengine.mekanized.machine.QioDrives;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.UtilityBehaviors;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers Mekanized's block configs give their blocks (D34) are the Java defaults, so the shipped
 * configs change nothing, and a server owner's value reaches the machine.
 */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class BlockNumbersTest {
    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};

    @Test
    void everyShippedRateIsTheTransportsDefault() {
        CoreMachineEngine engine = MekanizedTestKit.engine();
        int checked = 0;
        for (String family : new String[]{"universal_cable", "pressurized_tube", "mechanical_pipe", "logistical_transporter",
                "thermodynamic_conductor"}) {
            for (String tier : TIERS) {
                String id = tier + "_" + family;
                TransportType type = engine.registries().registry(Registries.TRANSPORT_TYPE).getOrThrow(MekanizedContent.key(id));
                Object rate = ShippedBlocks.behavior(id).get("rate");
                assertNotNull(rate, id + " has a rate");
                assertEquals(type.ratePerTick(), ((Number) rate).longValue(), id);
                checked++;
            }
        }
        assertEquals(20, checked);
    }

    @Test
    void everyShippedCapacityIsTheMachinesDefault() {
        CoreMachineEngine engine = MekanizedTestKit.engine();
        int checked = 0;
        for (Map.Entry<String, Map<String, Object>> block : ShippedBlocks.BEHAVIORS.entrySet()) {
            if (!(block.getValue().get("storages") instanceof Map<?, ?> storages)) continue;
            MachineType type = MekanizedTestKit.type(engine, String.valueOf(block.getValue().get("machine")).substring("mekanized:".length()));
            for (Object storage : storages.keySet()) {
                StorageDefinition definition = type.storage(String.valueOf(storage)).orElseThrow();
                assertEquals(definition.capacity(), ShippedBlocks.capacity(block.getKey(), String.valueOf(storage)), block.getKey());
                checked++;
            }
        }
        assertTrue(checked >= 5, "the bins and the waste barrel at least");
    }

    @Test
    void everyShippedOptionIsItsDefault() {
        Map<String, Map<String, Number>> expected = new LinkedHashMap<>();
        expected.put("chargepad", Map.of("charge-per-tick", UtilityMachines.CHARGE_PER_TICK.defaultValue()));
        expected.put("nutritional_liquifier", Map.of("ticks", Liquifier.TICKS.defaultValue(), "energy-per-tick", Liquifier.ENERGY_PER_TICK.defaultValue()));
        // The pump's and lasers' defaults are the arguments of their behaviors.
        expected.put("electric_pump", Map.of("energy", 2_000, "ticks", 20, "range", 32,
                "heavy-water-per-block", ChemistryBehaviors.HEAVY_WATER_PER_BLOCK.defaultValue()));
        expected.put("fuelwood_heater", Map.of("heat-per-tick", HeatMachines.FUELWOOD_HEAT.defaultValue()));
        expected.put("seismic_vibrator", Map.of("energy-per-tick", AutomationMachines.VIBRATOR_ENERGY.defaultValue()));
        for (String laser : new String[]{"laser", "laser_amplifier", "laser_tractor_beam"}) {
            expected.put(laser, Map.of("energy-per-tick", laser.equals("laser") ? 5_000 : 5_000_000,
                    "range", UtilityBehaviors.BEAM_RANGE.defaultValue(), "energy-per-hardness", UtilityBehaviors.ENERGY_PER_HARDNESS.defaultValue()));
        }
        expected.put("radioactive_waste_barrel", Map.of("decay-per-second", Radiation.BARREL_DECAY.defaultValue()));
        expected.put("digital_miner", Map.of("energy-per-block", AutomationMachines.MINER_ENERGY.defaultValue(),
                "ticks-per-block", AutomationMachines.MINER_TICKS.defaultValue()));
        expected.put("dimensional_stabilizer", Map.of("energy-per-chunk", ChunkLoaders.ENERGY_PER_CHUNK.defaultValue()));
        expected.put("qio_importer", Map.of("items-per-second", AutomationMachines.IO_RATE.defaultValue()));
        expected.put("qio_exporter", Map.of("items-per-second", AutomationMachines.IO_RATE.defaultValue()));

        for (Map.Entry<String, Map<String, Object>> block : ShippedBlocks.BEHAVIORS.entrySet()) {
            if (!(block.getValue().get("options") instanceof Map<?, ?> options)) continue;
            Map<String, Number> wanted = expected.get(block.getKey());
            assertNotNull(wanted, block.getKey() + " has options this test does not know");
            assertEquals(wanted.keySet(), options.keySet(), block.getKey());
            wanted.forEach((key, value) -> assertEquals(value.longValue(), ((Number) options.get(key)).longValue(), block.getKey() + " " + key));
        }
        for (String block : expected.keySet()) assertNotNull(ShippedBlocks.behavior(block).get("options"), block + " has options");
    }

    @Test
    void everyShippedDriveHoldsTheDefault() {
        // The drives' numbers are item config (D32): what headless tests use without a platform lookup.
        assertEquals(4, QioDrives.DEFAULTS.size());
        QioDrives.DEFAULTS.forEach((id, limits) -> {
            assertEquals(limits.items(), ((Number) ShippedItems.option(id.value(), "mekanized:qio_drive", "items")).longValue(), id.asString());
            assertEquals(limits.kinds(), ((Number) ShippedItems.option(id.value(), "mekanized:qio_drive", "kinds")).longValue(), id.asString());
        });
    }

    @Test
    void theShippedGaugeDropperHoldsTheDefault() {
        assertEquals(GaugeDropper.DEFAULT_CAPACITY, ((Number) ShippedItems.option("gauge_dropper", "mekanized:gauge_dropper", "capacity")).longValue());
    }

    @Test
    void aBinHoldsWhatItsBlockSays() {
        CoreMachineEngine engine = MekanizedTestKit.engine();
        FakeWorld world = FakeWorld.singleRegion(engine);
        world.setOptions(MekanizedTestKit.HERE, BlockOptions.of("mekanized:basic_bin", null, Map.of("items", Map.of("capacity", 100)),
                message -> {
                }));
        MachineInstanceImpl bin = MekanizedTestKit.place(engine, world, "basic_bin");
        assertEquals(100, MekanizedTestKit.capacity(bin, "items"));
        assertEquals(100, MekanizedTestKit.insert(bin, "items", MekanizedTestKit.item("minecraft:cobblestone"), 500));
    }
}
