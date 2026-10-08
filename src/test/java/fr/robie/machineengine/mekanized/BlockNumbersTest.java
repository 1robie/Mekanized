package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.config.BlockOptions;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import fr.robie.machineengine.test.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
        assertTrue(checked >= 4, "the bins at least");
    }

    @Test
    void theChargepadsShippedOptionIsItsDefault() {
        assertEquals(UtilityMachines.CHARGE_PER_TICK.defaultValue(), ((Number) ShippedBlocks.option("chargepad", "charge-per-tick")).longValue());
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
