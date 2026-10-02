package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.ProcessingBehavior;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.NetworkBehavior;
import fr.robie.machineengine.api.network.NetworkType;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.process.ProcessBinding;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.upgrade.Modifier;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;

import java.util.ArrayList;
import java.util.List;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Phase 2 machines (MEKANIZED_PLAN.md): Mekanism's basic machines, generators, energy cubes,
 * universal cables and upgrades. Numbers are Mekanism's defaults (energy in Joules, chemicals in mB);
 * recipe durations and per-tick energy come from the imported recipe files, where server owners can
 * change them.
 */
public final class MekanizedMachines {
    // -- recipe types ----------------------------------------------------------------------------
    public static final RecipeType<ProcessingRecipe> ENRICHING = recipeType("enriching");
    public static final RecipeType<ProcessingRecipe> CRUSHING = recipeType("crushing");
    public static final RecipeType<ProcessingRecipe> SMELTING = recipeType("smelting");
    public static final RecipeType<ProcessingRecipe> COMPRESSING = recipeType("compressing");
    public static final RecipeType<ProcessingRecipe> COMBINING = recipeType("combining");
    public static final RecipeType<ProcessingRecipe> METALLURGIC_INFUSING = recipeType("metallurgic_infusing");
    public static final RecipeType<ProcessingRecipe> PURIFYING = recipeType("purifying");
    public static final RecipeType<ProcessingRecipe> SAWING = recipeType("sawing");
    public static final RecipeType<ProcessingRecipe> CHEMICAL_CONVERSION = recipeType("chemical_conversion");
    public static final RecipeType<ProcessingRecipe> ENERGY_CONVERSION = recipeType("energy_conversion");
    public static final List<RecipeType<ProcessingRecipe>> RECIPE_TYPES = List.of(ENRICHING, CRUSHING, SMELTING, COMPRESSING,
            COMBINING, METALLURGIC_INFUSING, PURIFYING, SAWING, CHEMICAL_CONVERSION, ENERGY_CONVERSION);

    // -- upgrades: an item with the same id installs them (machineengine_upgrades button) --------
    /** Mekanism: each speed upgrade makes processes faster and costlier. */
    public static final UpgradeType SPEED = UpgradeType.of(key("upgrade_speed"), 8,
            Modifier.multiplyTotal(StandardModifiers.PROCESS_DURATION, -250),
            Modifier.multiplyTotal(StandardModifiers.PROCESS_COST, 333));
    /** Each energy upgrade lowers the energy a process costs. */
    public static final UpgradeType ENERGY_UPGRADE = UpgradeType.of(key("upgrade_energy"), 8,
            Modifier.multiplyTotal(StandardModifiers.PROCESS_COST, -200));

    // -- energy transport ------------------------------------------------------------------------
    public static final NetworkType ENERGY_NETWORK = NetworkType.of(key("energy"), NetworkBehavior.distributing());
    private static final ResourcePredicate IS_ENERGY = ResourcePredicate.type(MekanizedContent.ENERGY);
    private static final ResourcePredicate IS_CHEMICAL = ResourcePredicate.category(MekanizedContent.CHEMICAL);
    public static final List<TransportType> CABLES = List.of(
            TransportType.of(key("basic_universal_cable"), ENERGY_NETWORK, 8_000, IS_ENERGY),
            TransportType.of(key("advanced_universal_cable"), ENERGY_NETWORK, 128_000, IS_ENERGY),
            TransportType.of(key("elite_universal_cable"), ENERGY_NETWORK, 1_024_000, IS_ENERGY),
            TransportType.of(key("ultimate_universal_cable"), ENERGY_NETWORK, 8_192_000, IS_ENERGY));

    private MekanizedMachines() {
    }

    private static RecipeType<ProcessingRecipe> recipeType(String name) {
        return RecipeType.of(key(name), ProcessingRecipe.class);
    }

    public static void register(RegistrationContext context) {
        RecipeFilters.attach(context);
        RECIPE_TYPES.forEach(context.recipeTypes()::register);
        context.upgradeTypes().register(SPEED);
        context.upgradeTypes().register(ENERGY_UPGRADE);
        context.networkTypes().register(ENERGY_NETWORK);
        CABLES.forEach(context.transportTypes()::register);
        machines().forEach(context.machineTypes()::register);
        context.recipes(FurnaceRecipes::provide);
    }

    static List<MachineType> machines() {
        List<MachineType> machines = new ArrayList<>();
        // Single-input machines: item in, item out, energy per tick.
        machines.add(processing("enrichment_chamber", ENRICHING, 20_000, 1).build());
        machines.add(processing("crusher", CRUSHING, 20_000, 1).build());
        machines.add(processing("energized_smelter", SMELTING, 20_000, 1).build());
        machines.add(processing("precision_sawmill", SAWING, 20_000, 2).build());
        // Combiner: a second item input.
        machines.add(processing("combiner", COMBINING, 40_000, 1, "extra")
                .storage(StorageDefinition.slots("extra", 1, 64).filter(RecipeFilters.inputOf(COMBINING)))
                .port(PortDefinition.builder("extra", "extra").direction(PortDirection.INPUT).build())
                .build());
        // Chemical machines: a tank fed by its item slot, used per tick (compressor, purification)
        // or as an input (infuser's infuse types).
        machines.add(chemicalMachine("osmium_compressor", COMPRESSING, 80_000, 210, false));
        machines.add(chemicalMachine("purification_chamber", PURIFYING, 80_000, 210, false));
        machines.add(chemicalMachine("metallurgic_infuser", METALLURGIC_INFUSING, 20_000, 1_000, true));
        // Generators.
        machines.add(generator("heat_generator", 160_000)
                .storage(StorageDefinition.slots("fuel", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("fuel", "fuel").direction(PortDirection.INPUT).build())
                .data(EnergyBehaviors.BURN)
                .behavior(EnergyBehaviors.fuelGenerator("fuel", 200, EnergyBehaviors.FURNACE_FUELS))
                .build());
        machines.add(generator("bio_generator", 160_000)
                .storage(StorageDefinition.slots("fuel", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("fuel", "fuel").direction(PortDirection.INPUT).build())
                .data(EnergyBehaviors.BURN)
                .behavior(EnergyBehaviors.fuelGenerator("fuel", 350, EnergyBehaviors.BIO_FUELS))
                .build());
        machines.add(generator("solar_generator", 96_000).behavior(EnergyBehaviors.solar(50)).build());
        // Energy cubes: input on every side but the front, output through the front.
        long[][] cubes = {{4_000_000, 4_000}, {16_000_000, 16_000}, {64_000_000, 64_000}, {256_000_000, 256_000}};
        String[] tiers = {"basic", "advanced", "elite", "ultimate"};
        for (int i = 0; i < tiers.length; i++) machines.add(cube(tiers[i] + "_energy_cube", cubes[i][0], cubes[i][1], false));
        machines.add(cube("creative_energy_cube", Long.MAX_VALUE / 2, Long.MAX_VALUE / 4, true));
        return machines;
    }

    /** Item input, item output (with {@code outputSlots}), energy buffer, an energy item slot and upgrades. */
    private static MachineType.Builder processing(String name, RecipeType<ProcessingRecipe> recipes, long energy, int outputSlots,
                                                  String... extraInputs) {
        List<String> inputs = new ArrayList<>(List.of("input"));
        inputs.addAll(List.of(extraInputs));
        MachineBehavior.Factory process = ProcessingBehavior.of(recipes, new ProcessBinding(inputs, "output", List.of("energy")));
        return base(name, energy)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(recipes)))
                .storage(StorageDefinition.slots("output", outputSlots, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).build())
                .upgradeSlots(2, upgrade -> upgrade.equals(SPEED) || upgrade.equals(ENERGY_UPGRADE))
                .behavior(ConvertingBehavior.of(process,
                        new ConvertingBehavior.Conversion("energy_slot", "energy", ENERGY_CONVERSION)));
    }

    private static MachineType chemicalMachine(String name, RecipeType<ProcessingRecipe> recipes, long energy, long tank,
                                               boolean chemicalIsInput) {
        ProcessBinding binding = chemicalIsInput
                ? new ProcessBinding(List.of("input", "chemical"), "output", List.of("energy"))
                : new ProcessBinding(List.of("input"), "output", List.of("energy", "chemical"));
        return base(name, energy)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(recipes)))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .storage(StorageDefinition.tank("chemical", tank).filter(IS_CHEMICAL))
                .storage(StorageDefinition.slots("chemical_slot", 1, 64).filter(RecipeFilters.inputOf(CHEMICAL_CONVERSION)))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).build())
                .port(PortDefinition.builder("chemical", "chemical").direction(PortDirection.INPUT).filter(IS_CHEMICAL).build())
                .upgradeSlots(2, upgrade -> upgrade.equals(SPEED) || upgrade.equals(ENERGY_UPGRADE))
                .behavior(ConvertingBehavior.of(ProcessingBehavior.of(recipes, binding),
                        new ConvertingBehavior.Conversion("chemical_slot", "chemical", CHEMICAL_CONVERSION),
                        new ConvertingBehavior.Conversion("energy_slot", "energy", ENERGY_CONVERSION)))
                .build();
    }

    /**
     * Energy buffer (input from every side) and the slot that turns redstone into energy. Conversion
     * slots have no port: what hoppers and pipes insert goes to the input (side configuration, Phase 4).
     */
    private static MachineType.Builder base(String name, long energy) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("energy", energy).filter(IS_ENERGY))
                .storage(StorageDefinition.slots("energy_slot", 1, 64).filter(RecipeFilters.inputOf(ENERGY_CONVERSION)))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY).build());
    }

    /** Energy buffer exposed as an output on every side. */
    private static MachineType.Builder generator(String name, long energy) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("energy", energy).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.OUTPUT).filter(IS_ENERGY).build());
    }

    private static MachineType cube(String name, long capacity, long output, boolean creative) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("energy", capacity).filter(IS_ENERGY))
                .port(PortDefinition.builder("in", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM).build())
                .port(PortDefinition.builder("out", "energy").direction(PortDirection.OUTPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.FRONT).ratePerTick(output).build())
                .behavior(EnergyBehaviors.cube(output, creative))
                .build();
    }
}
