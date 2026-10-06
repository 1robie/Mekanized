package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.ProcessingBehavior;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.process.ProcessBinding;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.world.RelativeSide;
import java.util.ArrayList;
import java.util.List;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.*;

/**
 * Mekanism's basic machines, generators and energy cubes, and the builders other machine families start from
 * ({@link #base}, {@link #generator}, {@link #chemicalMachine}).
 */
final class BasicMachines {
    private BasicMachines() {
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
                .port(PortDefinition.builder("extra", "extra").direction(PortDirection.INPUT).group("items").build())
                .build());
        // Chemical machines: a tank fed by its item slot, used per tick (compressor, purification)
        // or as an input (infuser's infuse types).
        machines.add(chemicalMachine("osmium_compressor", COMPRESSING, 80_000, 210, false).build());
        machines.add(chemicalMachine("purification_chamber", PURIFYING, 80_000, 210, false).build());
        machines.add(chemicalMachine("metallurgic_infuser", METALLURGIC_INFUSING, 20_000, 1_000, true).build());
        // Generators.
        machines.add(generator("heat_generator", 160_000)
                .storage(StorageDefinition.slots("fuel", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("fuel", "fuel").direction(PortDirection.INPUT).group("items").build())
                .data(EnergyBehaviors.BURN)
                .behavior(EnergyBehaviors.fuelGenerator("fuel", 200, EnergyBehaviors.FURNACE_FUELS))
                .build());
        machines.add(generator("bio_generator", 160_000)
                .storage(StorageDefinition.slots("fuel", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("fuel", "fuel").direction(PortDirection.INPUT).group("items").build())
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
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .upgradeSlots(2, MekanizedRegistry::standardUpgrade)
                .behavior(MachineParts.powered(process));
    }

    /** Item in, item out, a chemical tank used per tick or as an input, fed by tubes or its item slot. */
    static MachineType.Builder chemicalMachine(String name, RecipeType<ProcessingRecipe> recipes, long energy, long tank,
                                               boolean chemicalIsInput) {
        // The tank takes only what the recipes use: an input, or a per-tick cost.
        ResourcePredicate chemicals = IS_CHEMICAL.and(chemicalIsInput ? RecipeFilters.usedBy(recipes) : RecipeFilters.costOf(recipes));
        ProcessBinding binding = chemicalIsInput
                ? new ProcessBinding(List.of("input", "chemical"), "output", List.of("energy"))
                : new ProcessBinding(List.of("input"), "output", List.of("energy", "chemical"));
        return base(name, energy)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(recipes)))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .storage(StorageDefinition.tank("chemical", tank).filter(chemicals))
                .storage(StorageDefinition.slots("chemical_slot", 1, 64).filter(RecipeFilters.inputOf(CHEMICAL_CONVERSION)))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .port(PortDefinition.builder("chemical", "chemical").direction(PortDirection.INPUT).filter(IS_CHEMICAL).group("chemical").build())
                .upgradeSlots(2, chemicalIsInput ? MekanizedRegistry::standardUpgrade : MekanizedRegistry::chemicalUpgrade)
                .behavior(EjectBehavior.of(ConvertingBehavior.of(ProcessingBehavior.of(recipes, binding),
                        new ConvertingBehavior.Conversion("chemical_slot", "chemical", CHEMICAL_CONVERSION),
                        new ConvertingBehavior.Conversion("energy_slot", "energy", ENERGY_CONVERSION))));
    }

    /**
     * Energy buffer (input from every side) and the slot that turns redstone into energy. Conversion
     * slots have no port: what hoppers and pipes insert goes to the input (side configuration, Phase 4).
     */
    static MachineType.Builder base(String name, long energy) {
        return MachineType.builder(key(name))
                .data(EjectBehavior.EJECT)
                .storage(StorageDefinition.tank("energy", energy).filter(IS_ENERGY).capacityModifier(ENERGY_CAPACITY))
                .storage(StorageDefinition.slots("energy_slot", 1, 64)
                        .filter(RecipeFilters.inputOf(ENERGY_CONVERSION).or(EnergyItems.IS_ENERGY_ITEM)))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY).group("energy").build());
    }

    /** Energy buffer exposed as an output on every side. */
    static MachineType.Builder generator(String name, long energy) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("energy", energy).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").direction(PortDirection.OUTPUT).filter(IS_ENERGY).group("energy").build());
    }

    private static MachineType cube(String name, long capacity, long output, boolean creative) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("energy", capacity).filter(IS_ENERGY))
                .storage(StorageDefinition.slots("charge", 1, 1).filter(EnergyItems.IS_ENERGY_ITEM))
                .port(PortDefinition.builder("in", "energy").direction(PortDirection.INPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM).group("energy").build())
                .port(PortDefinition.builder("out", "energy").direction(PortDirection.OUTPUT).filter(IS_ENERGY)
                        .sides(RelativeSide.FRONT).ratePerTick(output).group("energy").build())
                .behavior(EnergyBehaviors.cube(output, creative))
                .build();
    }
}
