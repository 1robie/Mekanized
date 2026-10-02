package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.resource.ResourceCategory;
import fr.robie.machineengine.api.resource.ResourceType;
import net.kyori.adventure.key.Key;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    private static final Map<String, ResourceType> FLUIDS = new ConcurrentHashMap<>();

    private MekanizedContent() {
    }

    public static Key key(String value) {
        return Key.key(NAMESPACE, value);
    }

    /** The fluid {@code mekanized:fluid/<name>}, e.g. {@code water}; {@code null} if there is none. */
    public static @Nullable ResourceType fluid(@NotNull String name) {
        return FLUIDS.get(name);
    }

    static void register(RegistrationContext context) {
        context.resourceCategories().register(ENERGY_CATEGORY);
        context.resourceTypes().register(ENERGY);
        context.resourceCategories().register(CHEMICAL);
        for (String chemical : lines("mekanized/chemicals.txt")) {
            context.resourceTypes().register(ResourceType.of(key(chemical), CHEMICAL));
        }
        context.resourceCategories().register(FLUID);
        for (String fluid : lines("mekanized/fluids.txt")) {
            ResourceType type = FLUIDS.computeIfAbsent(fluid, name -> ResourceType.of(key("fluid/" + name), FLUID));
            context.resourceTypes().register(type);
        }
        fr.robie.machineengine.mekanized.machine.MekanizedMachines.register(context);
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
