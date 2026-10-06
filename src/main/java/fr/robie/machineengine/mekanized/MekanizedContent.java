package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.resource.ResourceCategory;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mekanized's resources (docs/MEKANIZED_PLAN.md). Registered in Java, not in YAML definitions:
 * machines (Phase 2) need them when they register, and definitions run after every addon.
 *
 * <ul>
 *   <li>{@link #ENERGY}: energy in Joules;</li>
 *   <li>chemicals: gases, infuse types and slurries (Mekanism 1.21 unifies them), one resource type
 *       each in {@link #CHEMICAL}, from the imported {@code mekanized/chemicals.txt};</li>
 *   <li>fluids: {@code mekanized:fluid/<name>} in {@link #FLUID} (water, lava and Mekanism's fluids,
 *       from {@code mekanized/fluids.txt}), apart from the chemicals of the same name.</li>
 * </ul>
 *
 * Items, blocks, tags and recipes are CraftEngine content and YAML definitions installed by
 * {@link MekanizedPlugin}.
 */
public final class MekanizedContent {
    public static final String NAMESPACE = "mekanized";
    public static final ResourceCategory ENERGY_CATEGORY = ResourceCategory.builder(key("energy")).unit("J").build();
    public static final ResourceType ENERGY = ResourceType.of(key("energy"), ENERGY_CATEGORY);
    public static final ResourceCategory CHEMICAL = ResourceCategory.builder(key("chemical")).unit("mB").build();
    public static final ResourceCategory FLUID = ResourceCategory.builder(key("fluid")).unit("mB").build();
    /** Heat in Joules: heaters make it, thermodynamic conductors carry it, boilers use it (Phase 6 wave 3). */
    public static final ResourceCategory HEAT_CATEGORY = ResourceCategory.builder(key("heat")).unit("J").build();
    public static final ResourceType HEAT = ResourceType.of(key("heat"), HEAT_CATEGORY);
    private static final Map<String, ResourceType> FLUIDS = new ConcurrentHashMap<>();
    private static final Map<String, ResourceType> CHEMICALS = new ConcurrentHashMap<>();

    private MekanizedContent() {
    }

    public static Key key(String value) {
        return Key.key(NAMESPACE, value);
    }

    /** The fluid {@code mekanized:fluid/<name>}, e.g. {@code water}; {@code null} if there is none. */
    public static @Nullable ResourceType fluid(@NotNull String name) {
        return FLUIDS.get(name);
    }

    /** The chemical {@code mekanized:<name>}, e.g. {@code steam}; {@code null} if there is none. */
    public static @Nullable ResourceType chemical(@NotNull String name) {
        return CHEMICALS.get(name);
    }

    /** The fluid or chemical named by a resource key value: {@code fluid/<name>} or a chemical's name; {@code null} if there is none. */
    public static @Nullable ResourceType resourceType(@NotNull String value) {
        return value.startsWith("fluid/") ? fluid(value.substring("fluid/".length())) : chemical(value);
    }

    /** {@link #resourceType(String)} for the value of a resource key (the namespace is not checked). */
    public static @Nullable ResourceType resourceType(@NotNull Key key) {
        return resourceType(key.value());
    }

    /** What a player reads for a fluid or chemical named by a resource key value: {@code fluid/nutritional_paste} is "nutritional paste". */
    public static String displayName(@NotNull String value) {
        return value.replace("fluid/", "").replace('_', ' ');
    }

    public static String displayName(@NotNull ResourceType type) {
        return displayName(type.key().value());
    }

    static void register(RegistrationContext context) {
        context.resourceCategories().register(ENERGY_CATEGORY);
        context.resourceTypes().register(ENERGY);
        context.resourceCategories().register(HEAT_CATEGORY);
        context.resourceTypes().register(HEAT);
        context.resourceCategories().register(CHEMICAL);
        for (String chemical : lines("mekanized/chemicals.txt")) {
            context.resourceTypes().register(CHEMICALS.computeIfAbsent(chemical, name -> ResourceType.of(key(name), CHEMICAL)));
        }
        context.resourceCategories().register(FLUID);
        for (String fluid : lines("mekanized/fluids.txt")) {
            ResourceType type = FLUIDS.computeIfAbsent(fluid, name -> ResourceType.of(key("fluid/" + name), FLUID));
            context.resourceTypes().register(type);
        }
        MekanizedRegistry.register(context);
    }

    private static List<String> lines(String resource) {
        try (InputStream in = Objects.requireNonNull(MekanizedContent.class.getClassLoader().getResourceAsStream(resource),
                resource + " is missing from the jar");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
