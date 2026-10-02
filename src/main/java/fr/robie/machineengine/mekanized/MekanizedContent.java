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
import java.util.Objects;

/**
 * Mekanized's resources (docs/MEKANIZED_PLAN.md). Registered in Java, not in YAML definitions:
 * machines (Phase 2) need them when they register, and definitions run after every addon.
 *
 * <ul>
 *   <li>{@link #ENERGY}: energy in Joules;</li>
 *   <li>chemicals: gases, infuse types and slurries (Mekanism 1.21 unifies them), one resource type
 *       each in {@link #CHEMICAL}, from the imported {@code mekanized/chemicals.txt}.</li>
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

    private MekanizedContent() {
    }

    public static Key key(String value) {
        return Key.key(NAMESPACE, value);
    }

    static void register(RegistrationContext context) {
        context.resourceCategories().register(ENERGY_CATEGORY);
        context.resourceTypes().register(ENERGY);
        context.resourceCategories().register(CHEMICAL);
        for (String chemical : chemicals()) {
            context.resourceTypes().register(ResourceType.of(key(chemical), CHEMICAL));
        }
        fr.robie.machineengine.mekanized.machine.MekanizedMachines.register(context);
    }

    static List<String> chemicals() {
        try (InputStream in = Objects.requireNonNull(MekanizedContent.class.getClassLoader().getResourceAsStream("mekanized/chemicals.txt"),
                "mekanized/chemicals.txt is missing from the jar");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
