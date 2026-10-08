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
 * What the shipped CraftEngine block configs give their machines and transports, read without a server
 * (D34): {@code storages:}, {@code options:} and {@code rate:} of each block's behavior.
 */
final class ShippedBlocks {
    private static final List<String> FILES = List.of("automation.yml", "chemistry.yml", "endgame.yml", "extras.yml", "gear.yml",
            "logistics.yml", "machines.yml", "materials.yml", "multiblocks.yml", "reactors.yml", "utility.yml");
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
        for (String file : FILES) {
            try (InputStream in = Objects.requireNonNull(ShippedBlocks.class.getClassLoader()
                    .getResourceAsStream("mekanized/pack/configuration/" + file), file)) {
                Map<String, Object> root = new Yaml().load(in);
                Map<String, Object> blocks = (Map<String, Object>) root.getOrDefault("blocks", Map.of());
                blocks.forEach((id, value) -> {
                    if (((Map<String, Object>) value).get("behavior") instanceof Map<?, ?> behavior) {
                        all.put(id.substring(id.indexOf(':') + 1), (Map<String, Object>) behavior);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all;
    }
}
