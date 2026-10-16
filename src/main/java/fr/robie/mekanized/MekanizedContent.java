package fr.robie.mekanized;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.resource.Energy;
import fr.robie.machineengine.api.resource.Fluids;
import fr.robie.machineengine.api.resource.ResourceCategory;
import fr.robie.machineengine.api.resource.ResourceType;
import fr.robie.machineengine.api.resource.fluid.FluidItems;
import fr.robie.machineengine.api.resource.fluid.FluidTexture;
import fr.robie.machineengine.api.resource.fluid.FluidType;
import fr.robie.mekanized.machine.MekanizedRegistry;
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
 * machines need them when they register, and definitions run after every addon.
 *
 * <ul>
 *   <li>{@link #ENERGY}: MachineEngine's energy ({@code machineengine:energy}, D29), in Joules by default;</li>
 *   <li>chemicals: gases, infuse types and slurries (Mekanism 1.21 unifies them), one resource type
 *       each in {@link #CHEMICAL}, from {@code mekanized/chemicals.txt};</li>
 *   <li>fluids, in MachineEngine's global {@link #FLUID} category: water and lava are MachineEngine's
 *       ({@code machineengine:fluid/water}); Mekanism's others are {@code mekanized:fluid/<name>}, from
 *       {@code mekanized/fluids.txt}, apart from the chemicals of the same name. They can be placed in the
 *       world, with buckets (D46).</li>
 * </ul>
 *
 * Items, blocks, tags and recipes are CraftEngine content and YAML definitions installed by
 * {@link MekanizedPlugin}.
 */
public final class MekanizedContent {
    public static final String NAMESPACE = "mekanized";
    public static final ResourceCategory ENERGY_CATEGORY = Energy.category();
    public static final ResourceType ENERGY = Energy.type();
    public static final ResourceCategory CHEMICAL = ResourceCategory.builder(key("chemical")).unit("mB").build();
    /** MachineEngine's global fluid category (D29): Mekanized's fluids flow through any fluid pipe or port. */
    public static final ResourceCategory FLUID = Fluids.category();
    /** Heat in Joules: heaters make it, thermodynamic conductors carry it, boilers use it. */
    public static final ResourceCategory HEAT_CATEGORY = ResourceCategory.builder(key("heat")).unit("J").build();
    /** Heat bars in menus: Mekanism's heat orange. Heat's type is created once, before config.yml is read, so this stays fixed. */
    private static final int HEAT_COLOR = 0xFF7E1F;
    public static final ResourceType HEAT = ResourceType.of(key("heat"), HEAT_CATEGORY, HEAT_COLOR);
    private static final Map<String, ResourceType> FLUIDS = new ConcurrentHashMap<>(Map.of("water", Fluids.water(), "lava", Fluids.lava()));
    private static final Map<String, ResourceType> CHEMICALS = new ConcurrentHashMap<>();
    private static final Map<String, FluidTexture> TEXTURES = new ConcurrentHashMap<>();
    /**
     * The colors set in {@code config.yml} ({@code colors}, {@code #RRGGBB} by resource key value, e.g. {@code steam} or
     * {@code fluid/brine}). Read by {@link MekanizedSettings#apply} before {@link #register} creates the types.
     */
    static volatile Map<String, String> colorOverrides = Map.of();

    private MekanizedContent() {
    }

    public static Key key(String value) {
        return Key.key(NAMESPACE, value);
    }

    /** The fluid named {@code name}, e.g. {@code water} (MachineEngine's) or {@code brine}; {@code null} if there is none. */
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
        // Energy, water and lava used to be Mekanized's: worlds and edited definitions may still name the old keys.
        context.resourceTypes().alias(key("energy"), Energy.KEY);
        context.resourceTypes().alias(key("fluid/water"), Fluids.WATER_KEY);
        context.resourceTypes().alias(key("fluid/lava"), Fluids.LAVA_KEY);
        context.modifierTypes().alias(key("process_cost/energy"), MekanizedRegistry.ENERGY_COST.key());
        MekanizedPlayers.register(context);
        context.resourceCategories().register(HEAT_CATEGORY);
        context.resourceTypes().register(HEAT);
        context.resourceCategories().register(CHEMICAL);
        for (String[] chemical : lines("mekanized/chemicals.txt")) {
            ResourceType type = CHEMICALS.computeIfAbsent(chemical[0], name -> typeOf(key(name), CHEMICAL, chemical));
            context.resourceTypes().register(type);
        }
        for (String[] fluid : lines("mekanized/fluids.txt")) {
            ResourceType type = FLUIDS.computeIfAbsent(fluid[0], name -> fluidOf(name, fluid));
            context.resourceTypes().register(type);
        }
        MekanizedRegistry.register(context);
    }

    /**
     * A chemical or fluid with its menu color: the {@code colors} setting of config.yml if it has one for the
     * resource, else the second column of its list (Mekanism's tint), else none.
     */
    private static ResourceType typeOf(Key key, ResourceCategory category, String[] line) {
        Integer color = ResourceType.parseColor(colorOverrides.get(key.value()));
        if (color == null && line.length > 1) color = ResourceType.parseColor(line[1]);
        return color == null ? ResourceType.of(key, category) : ResourceType.of(key, category, color);
    }

    /**
     * A Mekanism fluid, placeable in the world (D46): Mekanism's liquid texture (steam has its own) tinted
     * with the fluid's color, as Mekanism draws it, and its bucket item {@code mekanized:<name>_bucket}.
     * Server owners change any of it in {@code plugins/MachineEngine/fluids.yml}.
     */
    private static FluidType fluidOf(String name, String[] line) {
        Integer color = ResourceType.parseColor(colorOverrides.get("fluid/" + name));
        if (color == null && line.length > 1) color = ResourceType.parseColor(line[1]);
        String texture = name.equals("steam") ? "mek_liquid/steam" : "mek_liquid/liquid";
        FluidType.Builder builder = FluidType.builder(key("fluid/" + name)).placeable()
                .still(texture(texture))
                .flow(texture(texture + "_flow"))
                .frameTime(2)
                .bucket(new FluidItems.Bucket.Item(key(name + "_bucket")));
        if (color != null) builder.color(color).tint(color);
        return builder.build();
    }

    /** One object per texture: fluids sharing a texture share its tile in the pack (each keeps its tint). */
    private static FluidTexture texture(String path) {
        return TEXTURES.computeIfAbsent(path, ignored -> {
            Key texture = key(path);
            return (size, context) -> context.pack(texture, size);
        });
    }

    /** The lines of a list: a name, then optionally {@code #RRGGBB}. */
    private static List<String[]> lines(String resource) {
        try (InputStream in = Objects.requireNonNull(MekanizedContent.class.getClassLoader().getResourceAsStream(resource),
                resource + " is missing from the jar");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(line -> !line.isEmpty()).map(line -> line.split("\\s+")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
