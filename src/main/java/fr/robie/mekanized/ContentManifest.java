package fr.robie.mekanized;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The content files the plugin installs, as listed by the build in {@code manifest.yml} (the Gradle
 * task {@code contentManifest} reads {@code src/main/resources/mekanized}): CraftEngine configuration
 * files ({@code pack}, relative to the pack folder, e.g. {@code configuration/machines.yml}) and
 * MachineEngine definitions ({@code definitions}). A file added there cannot be forgotten in a list kept by hand.
 */
record ContentManifest(Set<String> pack, List<String> definitions) {
    /** Where the manifest is, in the jar and on the test classpath. */
    static final String RESOURCE = "mekanized/manifest.yml";

    /** Reads the manifest from the plugin's own resources; fails if the jar was built without one. */
    static ContentManifest load(ClassLoader loader) throws IOException {
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing resource " + RESOURCE);
            return parse(in);
        }
    }

    static ContentManifest parse(InputStream in) {
        Map<?, ?> yaml = new Yaml().load(in);
        return new ContentManifest(Set.copyOf(strings(yaml, "pack")), List.copyOf(strings(yaml, "definitions")));
    }

    private static List<String> strings(Map<?, ?> yaml, String key) {
        if (!(yaml.get(key) instanceof List<?> list)) throw new IllegalStateException("manifest.yml has no '" + key + "' list");
        return list.stream().map(String::valueOf).toList();
    }
}
