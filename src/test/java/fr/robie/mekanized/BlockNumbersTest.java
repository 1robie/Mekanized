package fr.robie.mekanized;

import fr.robie.machineengine.api.config.BlockOptions;
import fr.robie.machineengine.api.config.ConfigKey;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.mekanized.machine.ModificationStation;
import fr.robie.mekanized.machine.AutomationMachines;
import fr.robie.mekanized.machine.ChemistryBehaviors;
import fr.robie.mekanized.machine.ChunkLoaders;
import fr.robie.mekanized.machine.GaugeDropper;
import fr.robie.mekanized.machine.HeatMachines;
import fr.robie.mekanized.machine.Liquifier;
import fr.robie.mekanized.machine.QioDrives;
import fr.robie.mekanized.machine.Radiation;
import fr.robie.mekanized.machine.UtilityBehaviors;
import fr.robie.mekanized.machine.UtilityMachines;
import fr.robie.mekanized.multiblock.Boilers;
import fr.robie.mekanized.multiblock.Fission;
import fr.robie.mekanized.multiblock.Fusion;
import fr.robie.mekanized.multiblock.MultiblockMachines;
import fr.robie.mekanized.multiblock.Sps;
import fr.robie.mekanized.multiblock.Turbines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.List;
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
        assertTrue(checked >= 17, "the bins, the waste barrel, the cubes and the tanks at least");
    }

    @Test
    void everyShippedPortRateIsTheMachinesDefault() {
        CoreMachineEngine engine = MekanizedTestKit.engine();
        int checked = 0;
        for (Map.Entry<String, Map<String, Object>> block : ShippedBlocks.BEHAVIORS.entrySet()) {
            if (!(block.getValue().get("ports") instanceof Map<?, ?> ports)) continue;
            MachineType type = MekanizedTestKit.type(engine, String.valueOf(block.getValue().get("machine")).substring("mekanized:".length()));
            for (Map.Entry<?, ?> port : ports.entrySet()) {
                PortDefinition definition = type.ports().stream().filter(p -> p.id().equals(port.getKey())).findFirst().orElseThrow();
                long rate = ((Number) ((Map<?, ?>) port.getValue()).get("rate")).longValue();
                assertEquals(definition.ratePerTick(), rate, block.getKey() + " " + port.getKey());
                checked++;
            }
        }
        assertEquals(12, checked, "the energy cubes, chemical tanks and fluid tanks");
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
        // Behavior arguments that became options: the arguments are their defaults.
        expected.put("heat_generator", Map.of("energy-per-tick", 200));
        expected.put("bio_generator", Map.of("energy-per-tick", 350));
        expected.put("solar_generator", Map.of("energy-per-tick", 50));
        expected.put("advanced_solar_generator", Map.of("energy-per-tick", 300));
        expected.put("wind_generator", Map.of("max-energy-per-tick", 480));
        expected.put("logistical_sorter", Map.of("every-ticks", 10, "stack-size", 64));
        expected.put("fluidic_plenisher", Map.of("energy", 2_000, "ticks", 20, "range", 32));
        expected.put("formulaic_assemblicator", Map.of("energy", 4_000, "ticks", 40));
        expected.put("modification_station", defaults(List.of(ModificationStation.TICKS, ModificationStation.ENERGY_PER_TICK, ModificationStation.CONFIRM_SECONDS)));
        // Multiblocks: on the casing that anchors each structure, the providers' on the induction port.
        expected.put("dynamic_tank", Map.of("fluid-per-block", MultiblockMachines.TANK_FLUID_PER_BLOCK.defaultValue(),
                "chemical-per-block", MultiblockMachines.TANK_CHEMICAL_PER_BLOCK.defaultValue()));
        expected.put("induction_casing", defaults(MultiblockMachines.CELLS));
        expected.put("induction_port", defaults(MultiblockMachines.PROVIDERS));
        expected.put("thermal_evaporation_block", defaults(List.of(MultiblockMachines.EVAPORATION_PER_LEVEL,
                MultiblockMachines.EVAPORATION_TANK_PER_LEVEL, MultiblockMachines.EVAPORATION_OUTPUT_TANK)));
        expected.put("boiler_casing", defaults(List.of(Boilers.HEAT_PER_MB, Boilers.BOIL_PER_ELEMENT, Boilers.WATER_PER_BLOCK,
                Boilers.STEAM_PER_BLOCK, Boilers.HEAT_PER_BLOCK, Boilers.COOLANT_PER_BLOCK, Boilers.COOLED_PER_BLOCK)));
        expected.put("fission_reactor_casing", defaults(List.of(Fission.FISSION_HEAT_PER_MB, Fission.COOLANT_HEAT_PER_MB,
                Fission.FUEL_PER_ASSEMBLY, Fission.WASTE_PER_ASSEMBLY, Fission.WATER_PER_ASSEMBLY, Fission.SODIUM_PER_ASSEMBLY,
                Fission.STEAM_PER_ASSEMBLY, Fission.HOT_PER_ASSEMBLY)));
        expected.put("fusion_reactor_frame", defaults(List.of(Fusion.FUSION_IGNITION, Fusion.FUSION_ENERGY_PER_MB, Fusion.FUEL_TANK,
                Fusion.ENERGY_BUFFER)));
        expected.put("sps_casing", defaults(List.of(Sps.SPS_POLONIUM_PER_ANTIMATTER, Sps.SPS_ENERGY_PER_POLONIUM, Sps.POLONIUM_TANK,
                Sps.ANTIMATTER_TANK, Sps.ENERGY_BUFFER)));
        Map<String, Number> turbine = new LinkedHashMap<>(defaults(List.of(Turbines.FULL_BLADES, Turbines.BLADES_PER_COIL,
                Turbines.VENT_FLOW, Turbines.CONDENSER_RATE, Turbines.STEAM_PER_BLOCK, Turbines.ENERGY_PER_BLOCK)));
        turbine.put(Turbines.STEAM_ENERGY.path(), Turbines.STEAM_ENERGY.defaultValue());
        expected.put("turbine_casing", turbine);

        for (Map.Entry<String, Map<String, Object>> block : ShippedBlocks.BEHAVIORS.entrySet()) {
            if (!(block.getValue().get("options") instanceof Map<?, ?> nested)) continue;
            Map<String, Number> wanted = expected.get(block.getKey());
            assertNotNull(wanted, block.getKey() + " has options this test does not know");
            Map<String, Number> options = flatten("", nested, new LinkedHashMap<>());
            assertEquals(wanted.keySet(), options.keySet(), block.getKey());
            wanted.forEach((key, value) -> assertEquals(value.doubleValue(), options.get(key).doubleValue(), block.getKey() + " " + key));
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

    private static Map<String, Number> defaults(List<ConfigKey<Long>> keys) {
        Map<String, Number> defaults = new LinkedHashMap<>();
        for (ConfigKey<Long> key : keys) defaults.put(key.path(), key.defaultValue());
        return defaults;
    }

    /** {@code {a: {b: 1}}} as {@code {a.b: 1}}: the paths the machines' keys read. */
    private static Map<String, Number> flatten(String prefix, Map<?, ?> map, Map<String, Number> into) {
        map.forEach((key, value) -> {
            if (value instanceof Map<?, ?> child) flatten(prefix + key + ".", child, into);
            else into.put(prefix + key, (Number) value);
        });
        return into;
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
