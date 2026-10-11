package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.ProcessingBehavior;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.process.ProcessBinding;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.mekanized.MekanizedContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.robie.mekanized.MekanizedContent.key;
import static fr.robie.mekanized.machine.MekanizedRegistry.ENERGY_CONVERSION;
import static fr.robie.mekanized.machine.MekanizedRegistry.IS_CHEMICAL;

/**
 * The remaining single-block machines (Pressurized Reaction Chamber,
 * Isotopic Centrifuge, Solar Neutron Activator, Antiprotonic Nucleosynthesizer, the painting machines)
 * and generators (gas-burning, wind, advanced solar).
 */
public final class ReactionAndPigmentMachines {
    public static final RecipeType<ProcessingRecipe> REACTION = recipeType("reaction");
    public static final RecipeType<ProcessingRecipe> CENTRIFUGING = recipeType("centrifuging");
    public static final RecipeType<ProcessingRecipe> ACTIVATING = recipeType("activating");
    public static final RecipeType<ProcessingRecipe> NUCLEOSYNTHESIZING = recipeType("nucleosynthesizing");
    public static final RecipeType<ProcessingRecipe> PAINTING = recipeType("painting");
    public static final RecipeType<ProcessingRecipe> PIGMENT_EXTRACTING = recipeType("pigment_extracting");
    public static final RecipeType<ProcessingRecipe> PIGMENT_MIXING = recipeType("pigment_mixing");
    private static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);

    /** Joules per mB the gas-burning generator gets from each fuel. */
    public static final Map<String, Long> GAS_FUELS = Map.of("hydrogen", 200L, "ethene", 1_500L);

    private ReactionAndPigmentMachines() {
    }

    private static RecipeType<ProcessingRecipe> recipeType(String name) {
        return RecipeType.of(key(name), ProcessingRecipe.class);
    }

    static void register(RegistrationContext context) {
        for (var type : List.of(REACTION, CENTRIFUGING, ACTIVATING, NUCLEOSYNTHESIZING, PAINTING, PIGMENT_EXTRACTING, PIGMENT_MIXING)) {
            context.recipeTypes().register(type);
        }
        machines().forEach(context.machineTypes()::register);
    }

    static List<MachineType> machines() {
        List<MachineType> machines = new ArrayList<>();
        // Item, fluid and chemical in; item and chemical out.
        machines.add(BasicMachines.base("pressurized_reaction_chamber", 100_000)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(REACTION)))
                .storage(StorageDefinition.tank("fluid", 10_000).filter(IS_FLUID.and(RecipeFilters.usedBy(REACTION))))
                .storage(StorageDefinition.tank("chemical", 10_000).filter(IS_CHEMICAL.and(RecipeFilters.usedBy(REACTION))))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .storage(StorageDefinition.tank("chemical_output", 10_000).filter(IS_CHEMICAL))
                .storage(StorageDefinition.slots("bucket_in", 1, 16).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()))
                .port(port("input", PortDirection.INPUT, Items.anyItem(), "items"))
                .port(port("fluid", PortDirection.INPUT, IS_FLUID, "fluid"))
                .port(port("chemical", PortDirection.INPUT, IS_CHEMICAL, "chemical"))
                .port(port("output", PortDirection.OUTPUT, Items.anyItem(), "items"))
                .port(port("chemical_output", PortDirection.OUTPUT, IS_CHEMICAL, "chemical"))
                .upgradeSlots(2, ReactionAndPigmentMachines::upgrade)
                .behavior(MachineParts.powered(MachineParts.withBuckets(ProcessingBehavior.of(REACTION,
                        new ProcessBinding(List.of("input", "fluid", "chemical"), "output", List.of("energy"), List.of("chemical_output"))), "fluid")))
                .build());
        // Chemical to chemical.
        machines.add(chemicalToChemical("isotopic_centrifuge", CENTRIFUGING, 80_000, true).build());
        machines.add(chemicalToChemical("solar_neutron_activator", ACTIVATING, 0, false)
                .behavior(sunlit(ProcessingBehavior.of(ACTIVATING, ProcessBinding.of("input", "output"))))
                .build());
        // Item and chemical to item.
        machines.add(itemAndChemical("antiprotonic_nucleosynthesizer", NUCLEOSYNTHESIZING, 1_000_000, 1_000).build());
        machines.add(itemAndChemical("painting_machine", PAINTING, 40_000, 4_000).build());
        // Item to pigment; two pigments to one.
        machines.add(BasicMachines.base("pigment_extractor", 40_000)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(PIGMENT_EXTRACTING)))
                .storage(StorageDefinition.tank("output", 1_000).filter(IS_CHEMICAL))
                .port(port("input", PortDirection.INPUT, Items.anyItem(), "items"))
                .port(port("output", PortDirection.OUTPUT, IS_CHEMICAL, "chemical"))
                .upgradeSlots(2, ReactionAndPigmentMachines::upgrade)
                .behavior(MachineParts.powered(ProcessingBehavior.of(PIGMENT_EXTRACTING, new ProcessBinding(List.of("input"), "output", List.of("energy")))))
                .build());
        ResourcePredicate mixed = IS_CHEMICAL.and(RecipeFilters.usedBy(PIGMENT_MIXING));
        machines.add(BasicMachines.base("pigment_mixer", 40_000)
                .storage(StorageDefinition.tank("left", 1_000).filter(mixed))
                .storage(StorageDefinition.tank("right", 1_000).filter(mixed))
                .storage(StorageDefinition.tank("output", 1_000).filter(IS_CHEMICAL))
                .port(PortDefinition.builder("left", "left").direction(PortDirection.INPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.LEFT).group("chemical").build())
                .port(PortDefinition.builder("right", "right").direction(PortDirection.INPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.RIGHT).group("chemical").build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.FRONT).group("chemical").build())
                .upgradeSlots(2, ReactionAndPigmentMachines::upgrade)
                .behavior(MachineParts.powered(ProcessingBehavior.of(PIGMENT_MIXING, new ProcessBinding(List.of("left", "right"), "output", List.of("energy")))))
                .build());
        // Generators.
        machines.add(BasicMachines.generator("gas_burning_generator", 100_000)
                .storage(StorageDefinition.tank("fuel", 18_000).filter(IS_CHEMICAL.and((variant, tags) ->
                        GAS_FUELS.containsKey(variant.type().key().value()))))
                .port(port("fuel", PortDirection.INPUT, IS_CHEMICAL, "chemical"))
                .behavior(EnergyBehaviors.gasBurning("fuel", GAS_FUELS))
                .build());
        machines.add(BasicMachines.generator("wind_generator", 200_000).behavior(EnergyBehaviors.wind(480)).build());
        machines.add(BasicMachines.generator("advanced_solar_generator", 200_000).behavior(EnergyBehaviors.solar(300)).build());
        return machines;
    }

    private static boolean upgrade(UpgradeType upgrade) {
        return MekanizedRegistry.standardUpgrade(upgrade);
    }

    private static PortDefinition port(String storage, PortDirection direction, ResourcePredicate filter, String group) {
        return PortDefinition.builder(storage, storage).direction(direction).filter(filter).group(group).build();
    }

    /** The energy item slot, auto-eject. */

    /** A chemical tank in, one out; with energy when {@code powered}. */
    private static MachineType.Builder chemicalToChemical(String name, RecipeType<ProcessingRecipe> recipes, long energy, boolean powered) {
        MachineType.Builder builder = powered ? BasicMachines.base(name, energy) : MachineType.builder(key(name));
        builder.storage(StorageDefinition.tank("input", 10_000).filter(IS_CHEMICAL.and(RecipeFilters.usedBy(recipes))))
                .storage(StorageDefinition.tank("output", 10_000).filter(IS_CHEMICAL))
                .port(port("input", PortDirection.INPUT, IS_CHEMICAL, "chemical"))
                .port(port("output", PortDirection.OUTPUT, IS_CHEMICAL, "chemical"));
        if (powered) {
            builder.upgradeSlots(2, ReactionAndPigmentMachines::upgrade)
                    .behavior(MachineParts.powered(ProcessingBehavior.of(recipes, new ProcessBinding(List.of("input"), "output", List.of("energy")))));
        }
        return builder;
    }

    /** An item and a chemical (antimatter, a pigment) in, an item out. */
    private static MachineType.Builder itemAndChemical(String name, RecipeType<ProcessingRecipe> recipes, long energy, long tank) {
        return BasicMachines.base(name, energy)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(recipes)))
                .storage(StorageDefinition.tank("chemical", tank).filter(IS_CHEMICAL.and(RecipeFilters.usedBy(recipes))))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .port(port("input", PortDirection.INPUT, Items.anyItem(), "items"))
                .port(port("chemical", PortDirection.INPUT, IS_CHEMICAL, "chemical"))
                .port(port("output", PortDirection.OUTPUT, Items.anyItem(), "items"))
                .upgradeSlots(2, ReactionAndPigmentMachines::upgrade)
                .behavior(MachineParts.powered(ProcessingBehavior.of(recipes,
                        new ProcessBinding(List.of("input", "chemical"), "output", List.of("energy")))));
    }

    /** Works only in daylight under open sky (the Solar Neutron Activator), looking again every 2 seconds. */
    private static MachineBehavior.Factory sunlit(MachineBehavior.Factory inner) {
        return type -> {
            MachineBehavior behavior = inner.create(type);
            return context -> {
                MachineEnvironment environment = context.environment();
                if (environment.daytime() && environment.skyVisible() && !environment.raining()) {
                    behavior.tick(context);
                    if (context.process().current() == null) context.wakeIn(40); // waiting for sun or input
                } else {
                    context.setActive(false);
                    context.wakeIn(40);
                }
            };
        };
    }
}
