package fr.robie.machineengine.mekanized;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generated files the plugin installs, as listed by the Gradle import in {@code manifest.yml}:
 * CraftEngine configuration files ({@code pack}, relative to the pack folder, e.g.
 * {@code configuration/machines.yml}) and MachineEngine definitions ({@code definitions}). Installing
 * what the manifest lists means a newly generated file cannot be forgotten in a list kept by hand.
 */
record GeneratedManifest(Set<String> pack, List<String> definitions) {
    /** Where the manifest is, in the jar and on the test classpath. */
    static final String RESOURCE = "mekanized/manifest.yml";

    /** Reads the manifest from the plugin's own resources; fails if the jar was built without one. */
    static GeneratedManifest load(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing resource " + RESOURCE);
            return parse(in);
        }
    }

    static GeneratedManifest parse(InputStream in) {
        Map<?, ?> yaml = new Yaml().load(in);
        return new GeneratedManifest(Set.copyOf(strings(yaml, "pack")), List.copyOf(strings(yaml, "definitions")));
    }

    private static List<String> strings(Map<?, ?> yaml, String key) {
        if (!(yaml.get(key) instanceof List<?> list)) throw new IllegalStateException("manifest.yml has no '" + key + "' list");
        return list.stream().map(String::valueOf).toList();
    }
}
