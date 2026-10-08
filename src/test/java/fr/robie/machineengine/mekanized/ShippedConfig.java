package fr.robie.machineengine.mekanized;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The block and item entries of the shipped CraftEngine configuration, read without a server, written
 * out ({@code blocks:}, {@code items:}) or made by a {@code config_factory} (docs/CE_TEMPLATES_PLAN.md).
 * A factory's blueprint is copied once per instance, with {@code ${name}} and {@code ${name:-default}}
 * replaced by the instance's values; {@code template:} references are not expanded, since templates
 * never hold a block's or item's numbers.
 */
final class ShippedConfig {
    static final List<String> FILES = List.of("automation.yml", "chemistry.yml", "endgame.yml", "extras.yml", "gear.yml",
            "logistics.yml", "machines.yml", "materials.yml", "multiblocks.yml", "reactors.yml", "utility.yml");
    private static final Pattern FACTORY = Pattern.compile("config_factor(y|ies)(#.*)?");
    private static final Pattern BLUEPRINT = Pattern.compile("blueprint|prototype|schema");
    private static final Pattern INSTANCES = Pattern.compile("instances?|inputs?");
    /** {@code ${name}} or {@code ${name:-default}}; the default may be quoted. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)(?::-(\"[^\"]*\"|'[^']*'|[^}]*))?}");

    private ShippedConfig() {
    }

    /** Calls {@code action} with the id (with namespace) and content of every entry of {@code section} in the shipped files. */
    static void forEach(String section, BiConsumer<String, Map<String, Object>> action) {
        for (String file : FILES) {
            try (InputStream in = Objects.requireNonNull(ShippedConfig.class.getClassLoader()
                    .getResourceAsStream("mekanized/pack/configuration/" + file), file)) {
                forEach(new Yaml().<Map<String, Object>>load(in), section, action);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Calls {@code action} with every entry of {@code section} in one parsed file. */
    @SuppressWarnings("unchecked")
    static void forEach(Map<String, Object> root, String section, BiConsumer<String, Map<String, Object>> action) {
        ((Map<String, Object>) root.getOrDefault(section, Map.of())).forEach((id, value) -> action.accept(id, (Map<String, Object>) value));
        root.forEach((key, value) -> {
            if (!FACTORY.matcher(key).matches()) return;
            Map<String, Object> factory = (Map<String, Object>) value;
            Map<String, Object> blueprint = (Map<String, Object>) find(factory, BLUEPRINT, key);
            Map<String, Object> entries = (Map<String, Object>) blueprint.getOrDefault(section, Map.of());
            for (Object instance : (List<Object>) find(factory, INSTANCES, key)) {
                Map<String, Object> arguments = (Map<String, Object>) instance;
                entries.forEach((id, entry) -> {
                    String resolvedId = String.valueOf(substitute(id, arguments));
                    Map<String, Object> resolved = (Map<String, Object>) substitute(entry, arguments);
                    for (String behavior : List.of("behavior", "behaviors")) {
                        if (resolved.containsKey(behavior) && String.valueOf(resolved.get(behavior)).contains("${")) {
                            throw new IllegalStateException(key + ": " + section + " " + resolvedId + " has a placeholder its instance does not give: "
                                    + resolved.get(behavior));
                        }
                    }
                    action.accept(resolvedId, resolved);
                });
            }
        });
    }

    private static Object find(Map<String, Object> factory, Pattern name, String factoryKey) {
        for (Map.Entry<String, Object> entry : factory.entrySet()) {
            if (name.matcher(entry.getKey()).matches()) return entry.getValue();
        }
        throw new IllegalStateException(factoryKey + " has no " + name.pattern());
    }

    @SuppressWarnings("unchecked")
    private static Object substitute(Object value, Map<String, Object> arguments) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> out.put(String.valueOf(substitute(k, arguments)), substitute(v, arguments)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object element : list) out.add(substitute(element, arguments));
            return out;
        }
        if (!(value instanceof String text)) return value;
        Matcher whole = PLACEHOLDER.matcher(text);
        if (whole.matches()) {
            // A value that is one placeholder keeps the argument's type (a number stays a number).
            Object resolved = resolve(whole, arguments);
            return resolved == null ? text : resolved;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object resolved = resolve(matcher, arguments);
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved == null ? matcher.group() : String.valueOf(resolved)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The placeholder's value, its default, or {@code null} if it has neither. */
    private static Object resolve(Matcher placeholder, Map<String, Object> arguments) {
        if (arguments.containsKey(placeholder.group(1))) return arguments.get(placeholder.group(1));
        String fallback = placeholder.group(2);
        if (fallback == null) return null;
        if (fallback.length() >= 2 && (fallback.startsWith("\"") && fallback.endsWith("\"") || fallback.startsWith("'") && fallback.endsWith("'"))) {
            return fallback.substring(1, fallback.length() - 1);
        }
        return fallback;
    }
}
