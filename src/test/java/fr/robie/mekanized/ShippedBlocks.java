package fr.robie.mekanized;

import java.util.HashMap;
import java.util.Map;

/**
 * What the shipped CraftEngine block configs give their machines and transports, read without a server
 * (D34): {@code storages:}, {@code options:} and {@code rate:} of each block's behavior, written out or made
 * by a config factory ({@link ShippedConfig}).
 */
final class ShippedBlocks {
    private static final String MACHINE_TEMPLATE = "machineengine:block/machine";
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
            Map<String, Object> behavior = block.get("behavior") instanceof Map<?, ?> map ? new HashMap<>((Map<String, Object>) map) : null;
            if (ShippedConfig.usesTemplate(block, MACHINE_TEMPLATE)) {
                // What machineengine:block/machine gives (the engine's templates.yml); the block's keys win.
                if (behavior == null) behavior = new HashMap<>();
                behavior.putIfAbsent("type", "machineengine:machine");
                behavior.putIfAbsent("machine", id);
                behavior.putIfAbsent("keep_contents", true);
            }
            if (behavior != null) all.put(id.substring(id.indexOf(':') + 1), behavior);
        });
        return all;
    }
}
