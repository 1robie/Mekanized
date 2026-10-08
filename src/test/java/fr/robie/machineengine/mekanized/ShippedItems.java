package fr.robie.machineengine.mekanized;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the shipped CraftEngine item configs say about items, read without a server (D32): what they
 * hold ({@code machineengine:energy_item}, {@code machineengine:resource_item}) and their behaviors' options.
 */
final class ShippedItems {
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
        ShippedConfig.forEach("items", (id, item) -> {
            if (!(item.get("behaviors") instanceof List<?> list)) return;
            Map<String, Map<String, Object>> byType = all.computeIfAbsent(id.substring(id.indexOf(':') + 1), k -> new HashMap<>());
            for (Object entry : list) {
                Map<String, Object> behavior = (Map<String, Object>) entry;
                byType.put(String.valueOf(behavior.get("type")), behavior);
            }
        });
        return all;
    }
}
