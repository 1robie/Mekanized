package fr.robie.machineengine.mekanized;

import java.util.HashMap;
import java.util.Map;

/**
 * What the shipped CraftEngine block configs give their machines and transports, read without a server
 * (D34): {@code storages:}, {@code options:} and {@code rate:} of each block's behavior, written out or made
 * by a config factory ({@link ShippedConfig}).
 */
final class ShippedBlocks {
    /** Block id (without namespace) -> its behavior section. */
    static final Map<String, Map<String, Object>> BEHAVIORS = load();

    private ShippedBlocks() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> behavior(String id) {
        return BEHAVIORS.getOrDefault(id, Map.of());
    }

    /** The block's {@code storages.<storage>.capacity}, or {@code null}. */
    @SuppressWarnings("unchecked")
    static Long capacity(String id, String storage) {
        Map<String, Object> storages = (Map<String, Object>) behavior(id).get("storages");
        Object capacity = storages == null ? null : ((Map<String, Object>) storages.getOrDefault(storage, Map.of())).get("capacity");
        return capacity == null ? null : ((Number) capacity).longValue();
    }

    /** The block's {@code options.<key>}, or {@code null}. */
    @SuppressWarnings("unchecked")
    static Object option(String id, String key) {
        Map<String, Object> options = (Map<String, Object>) behavior(id).get("options");
        return options == null ? null : options.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> load() {
        Map<String, Map<String, Object>> all = new HashMap<>();
        ShippedConfig.forEach("blocks", (id, block) -> {
            if (block.get("behavior") instanceof Map<?, ?> behavior) {
                all.put(id.substring(id.indexOf(':') + 1), (Map<String, Object>) behavior);
            }
        });
        return all;
    }
}
