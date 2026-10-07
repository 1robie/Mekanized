package fr.robie.machineengine.mekanized;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the shipped CraftEngine item configs say items hold: the {@code machineengine:energy_item} and
 * {@code machineengine:resource_item} behaviors (D32), read without a server.
 */
final class ShippedItems {
    private static final List<String> FILES = List.of("gear.yml", "chemistry.yml");
    private static final Map<String, Map<String, Long>> CAPACITIES = load();

    private ShippedItems() {
    }

    /** The capacity of {@code resource} (a key, e.g. {@code machineengine:energy}) in the Mekanized item {@code id}; 0 if none. */
    static long capacity(String id, String resource) {
        return CAPACITIES.getOrDefault(id, Map.of()).getOrDefault(resource, 0L);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Long>> load() {
        Map<String, Map<String, Long>> capacities = new HashMap<>();
        for (String file : FILES) {
            try (InputStream in = Objects.requireNonNull(ShippedItems.class.getClassLoader()
                    .getResourceAsStream("mekanized/pack/configuration/" + file), file)) {
                Map<String, Object> root = new Yaml().load(in);
                Map<String, Object> items = (Map<String, Object>) root.getOrDefault("items", Map.of());
                items.forEach((id, value) -> {
                    Object behaviors = ((Map<String, Object>) value).get("behaviors");
                    if (!(behaviors instanceof List<?> list)) return;
                    for (Object entry : list) {
                        Map<String, Object> behavior = (Map<String, Object>) entry;
                        String type = String.valueOf(behavior.get("type"));
                        Map<String, Long> held = capacities.computeIfAbsent(id.substring(id.indexOf(':') + 1), k -> new HashMap<>());
                        if (type.equals("machineengine:energy_item")) {
                            held.put("machineengine:energy", ((Number) behavior.get("max-energy")).longValue());
                        } else if (type.equals("machineengine:resource_item")) {
                            held.put(String.valueOf(behavior.get("resource")), ((Number) behavior.get("capacity")).longValue());
                        }
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return capacities;
    }
}
