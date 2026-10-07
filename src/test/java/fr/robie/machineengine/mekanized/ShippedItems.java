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
 * What the shipped CraftEngine item configs say about items, read without a server (D32): what they
 * hold ({@code machineengine:energy_item}, {@code machineengine:resource_item}) and their behaviors' options.
 */
final class ShippedItems {
    private static final List<String> FILES = List.of("automation.yml", "chemistry.yml", "endgame.yml", "extras.yml", "gear.yml",
            "logistics.yml", "machines.yml", "materials.yml", "multiblocks.yml", "reactors.yml", "utility.yml");
    /** Item id (without namespace) -> behavior type -> its options. */
    private static final Map<String, Map<String, Map<String, Object>>> BEHAVIORS = load();

    private ShippedItems() {
    }

    /** The capacity of {@code resource} (a key, e.g. {@code machineengine:energy}) in the Mekanized item {@code id}; 0 if none. */
    static long capacity(String id, String resource) {
        Map<String, Map<String, Object>> behaviors = BEHAVIORS.getOrDefault(id, Map.of());
        if (resource.equals("machineengine:energy")) {
            Object max = behaviors.getOrDefault("machineengine:energy_item", Map.of()).get("max-energy");
            return max == null ? 0 : ((Number) max).longValue();
        }
        Map<String, Object> holder = behaviors.getOrDefault("machineengine:resource_item", Map.of());
        return resource.equals(holder.get("resource")) ? ((Number) holder.get("capacity")).longValue() : 0;
    }

    /** The option {@code key} of the behavior {@code type} on the Mekanized item {@code id}, or {@code null}. */
    static Object option(String id, String type, String key) {
        return BEHAVIORS.getOrDefault(id, Map.of()).getOrDefault(type, Map.of()).get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Map<String, Object>>> load() {
        Map<String, Map<String, Map<String, Object>>> all = new HashMap<>();
        for (String file : FILES) {
            try (InputStream in = Objects.requireNonNull(ShippedItems.class.getClassLoader()
                    .getResourceAsStream("mekanized/pack/configuration/" + file), file)) {
                Map<String, Object> root = new Yaml().load(in);
                Map<String, Object> items = (Map<String, Object>) root.getOrDefault("items", Map.of());
                items.forEach((id, value) -> {
                    Object behaviors = ((Map<String, Object>) value).get("behaviors");
                    if (!(behaviors instanceof List<?> list)) return;
                    Map<String, Map<String, Object>> byType = all.computeIfAbsent(id.substring(id.indexOf(':') + 1), k -> new HashMap<>());
                    for (Object entry : list) {
                        Map<String, Object> behavior = (Map<String, Object>) entry;
                        byType.put(String.valueOf(behavior.get("type")), behavior);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all;
    }
}
