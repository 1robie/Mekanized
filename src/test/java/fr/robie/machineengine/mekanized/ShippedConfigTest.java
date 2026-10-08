package fr.robie.machineengine.mekanized;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link ShippedConfig} reads config factories the way CraftEngine fills them in. */
class ShippedConfigTest {
    private static final String FILE = """
            blocks:
              mekanized:plain:
                behavior:
                  rate: 5
            config_factory#cables:
              instances:
                - {tier: basic, rate: 8000}
                - {tier: elite, rate: 1024000, group: elite_cables}
              blueprint:
                blocks:
                  mekanized:${tier}_cable:
                    template: mekanized:block/transport
                    arguments:
                      state_group: ${group:-"cables"}
                    behavior:
                      rate: ${rate}
                      connection_items: mekanized:cable_part/${tier}_cable
            """;

    @Test
    void factoryInstancesAreFilledIn() {
        Map<String, Map<String, Object>> blocks = blocks(FILE);
        assertEquals(3, blocks.size());
        assertEquals(Map.of("rate", 5), blocks.get("mekanized:plain").get("behavior"));
        assertEquals(Map.of("rate", 8000, "connection_items", "mekanized:cable_part/basic_cable"),
                blocks.get("mekanized:basic_cable").get("behavior"));
        assertEquals(1024000, ((Map<?, ?>) blocks.get("mekanized:elite_cable").get("behavior")).get("rate"));
        assertEquals("cables", ((Map<?, ?>) blocks.get("mekanized:basic_cable").get("arguments")).get("state_group"));
        assertEquals("elite_cables", ((Map<?, ?>) blocks.get("mekanized:elite_cable").get("arguments")).get("state_group"));
    }

    @Test
    void behaviorPlaceholderWithoutValueFails() {
        String file = FILE.replace("rate: ${rate}", "rate: ${speed}");
        assertThrows(IllegalStateException.class, () -> blocks(file));
    }

    private static Map<String, Map<String, Object>> blocks(String yaml) {
        Map<String, Map<String, Object>> blocks = new LinkedHashMap<>();
        ShippedConfig.forEach(new Yaml().load(yaml), "blocks", blocks::put);
        return blocks;
    }
}
