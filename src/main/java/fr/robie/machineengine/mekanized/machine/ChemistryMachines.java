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
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.MekanizedSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.CHEMICAL_CONVERSION;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.ENERGY_CONVERSION;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.ENERGY_UPGRADE;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.IS_CHEMICAL;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.SPEED;

/**
 * Chemical machines, chemical and fluid tanks, pressurized tubes,
 * mechanical pipes and the electric pump. Mekanism's numbers (Joules, mB); recipe durations and
 * per-tick energy come from the recipe files.
 */
public final class ChemistryMachines {
    // -- recipe types ----------------------------------------------------------------------------
    public static final RecipeType<ProcessingRecipe> OXIDIZING = recipeType("oxidizing");
    public static final RecipeType<ProcessingRecipe> CHEMICAL_INFUSING = recipeType("chemical_infusing");
    public static final RecipeType<ProcessingRecipe> INJECTING = recipeType("injecting");
    public static final RecipeType<ProcessingRecipe> DISSOLUTION = recipeType("dissolution");
    public static final RecipeType<ProcessingRecipe> WASHING = recipeType("washing");
    public static final RecipeType<ProcessingRecipe> CRYSTALLIZING = recipeType("crystallizing");
    public static final RecipeType<ProcessingRecipe> SEPARATING = recipeType("separating");
    public static final RecipeType<ProcessingRecipe> CONDENSING = recipeType("condensing");
    public static final RecipeType<ProcessingRecipe> DECONDENSING = recipeType("decondensing");
    /** Bucket slots: a full bucket into the tank, an empty one filled from it. */
    public static final RecipeType<ProcessingRecipe> BUCKET_FILLING = recipeType("bucket_filling");
    public static final RecipeType<ProcessingRecipe> BUCKET_DRAINING = recipeType("bucket_draining");
    public static final List<RecipeType<ProcessingRecipe>> RECIPE_TYPES = List.of(OXIDIZING, CHEMICAL_INFUSING, INJECTING,
            DISSOLUTION, WASHING, CRYSTALLIZING, SEPARATING, CONDENSING, DECONDENSING, BUCKET_FILLING, BUCKET_DRAINING);

    static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);

    // -- transport: rates are Mekanism's pull amounts per tick -----------------------------------
    public static final NetworkType CHEMICAL_NETWORK = NetworkType.of(key("chemical"), NetworkBehavior.distributing());
    public static final NetworkType FLUID_NETWORK = NetworkType.of(key("fluid"), NetworkBehavior.distributing());
    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    public static final List<TransportType> TUBES = transports("pressurized_tube", CHEMICAL_NETWORK, IS_CHEMICAL,
            MekanizedSettings.tubeRates);
    public static final List<TransportType> PIPES = transports("mechanical_pipe", FLUID_NETWORK, IS_FLUID,
            MekanizedSettings.pipeRates);

    private ChemistryMachines() {
    }

    private static RecipeType<ProcessingRecipe> recipeType(String name) {
        return RecipeType.of(key(name), ProcessingRecipe.class);
    }

    private static List<TransportType> transports(String family, NetworkType network, ResourcePredicate filter, long... rates) {
        List<TransportType> transports = new ArrayList<>();
        for (int i = 0; i < TIERS.length; i++) {
            transports.add(TransportType.of(key(TIERS[i] + "_" + family), network, rates[i], filter));
        }
        return List.copyOf(transports);
    }

    static void register(RegistrationContext context) {
        RECIPE_TYPES.forEach(context.recipeTypes()::register);
        context.networkTypes().register(CHEMICAL_NETWORK);
        context.networkTypes().register(FLUID_NETWORK);
        TUBES.forEach(context.transportTypes()::register);
        PIPES.forEach(context.transportTypes()::register);
        machines().forEach(context.machineTypes()::register);
    }

    static List<MachineType> machines() {
        List<MachineType> machines = new ArrayList<>();
        // Item to chemical.
        machines.add(BasicMachines.base("chemical_oxidizer", 20_000)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(OXIDIZING)))
                .storage(chemicalTank("output", 10_000, IS_CHEMICAL))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(output("output", IS_CHEMICAL))
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(process(OXIDIZING, List.of("input"))))
                .build());
        // Two chemicals to one: left and right tanks on their sides, output at the front.
        ResourcePredicate infused = IS_CHEMICAL.and(RecipeFilters.usedBy(CHEMICAL_INFUSING));
        machines.add(BasicMachines.base("chemical_infuser", 80_000)
                .storage(chemicalTank("left", 10_000, infused))
                .storage(chemicalTank("right", 10_000, infused))
                .storage(chemicalTank("output", 10_000, IS_CHEMICAL))
                .port(input("left", IS_CHEMICAL, RelativeSide.LEFT))
                .port(input("right", IS_CHEMICAL, RelativeSide.RIGHT))
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.FRONT).group("chemical").build())
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(process(CHEMICAL_INFUSING, List.of("left", "right"))))
                .build());
        // Item and a chemical per tick to an item (4x ore processing: shards).
        machines.add(BasicMachines.chemicalMachine("chemical_injection_chamber", INJECTING, 160_000, 210, false).build());
        // Item and sulfuric acid per tick to a slurry.
        machines.add(BasicMachines.base("chemical_dissolution_chamber", 160_000)
                .storage(StorageDefinition.slots("input", 1, 64).filter(RecipeFilters.inputOf(DISSOLUTION)))
                .storage(chemicalTank("chemical", 10_000, IS_CHEMICAL.and(RecipeFilters.costOf(DISSOLUTION))))
                .storage(chemicalTank("output", 10_000, IS_CHEMICAL))
                .port(PortDefinition.builder("input", "input").direction(PortDirection.INPUT).group("items").build())
                .port(input("chemical", IS_CHEMICAL))
                .port(output("output", IS_CHEMICAL))
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(ProcessingBehavior.of(DISSOLUTION,
                        new ProcessBinding(List.of("input"), "output", List.of("energy", "chemical")))))
                .build());
        // Dirty slurry and water to clean slurry.
        machines.add(withBuckets(BasicMachines.base("chemical_washer", 80_000), "fluid", 10_000, true, false,
                IS_FLUID.and(RecipeFilters.usedBy(WASHING)), true)
                .storage(chemicalTank("input", 10_000, IS_CHEMICAL.and(RecipeFilters.usedBy(WASHING))))
                .storage(chemicalTank("output", 10_000, IS_CHEMICAL))
                .port(input("input", IS_CHEMICAL))
                .port(output("output", IS_CHEMICAL))
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(process(WASHING, List.of("input", "fluid")), buckets("fluid", true, false)))
                .build());
        // Clean slurry (or brine, lithium) to an item.
        machines.add(BasicMachines.base("chemical_crystallizer", 20_000)
                .storage(chemicalTank("input", 10_000, IS_CHEMICAL.and(RecipeFilters.usedBy(CRYSTALLIZING))))
                .storage(StorageDefinition.slots("output", 1, 64).filter(Items.anyItem()))
                .port(input("input", IS_CHEMICAL))
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(process(CRYSTALLIZING, List.of("input"))))
                .build());
        // Fluid to two chemicals (water to hydrogen and oxygen).
        machines.add(withBuckets(BasicMachines.base("electrolytic_separator", 160_000), "input", 24_000, true, false,
                IS_FLUID.and(RecipeFilters.usedBy(SEPARATING)), true)
                .storage(StorageDefinition.slots("output", 2, 2_400).filter(IS_CHEMICAL))
                .port(output("output", IS_CHEMICAL))
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(process(SEPARATING, List.of("input")), buckets("input", true, false)))
                .build());
        // Chemical to fluid or back, by mode: the chemical side (left) and fluid side (right) swap roles.
        machines.add(withBuckets(BasicMachines.base("rotary_condensentrator", 20_000), "fluid", 10_000, true, true, IS_FLUID, false)
                .storage(chemicalTank("chemical", 10_000, IS_CHEMICAL))
                .port(PortDefinition.builder("chemical", "chemical").direction(PortDirection.INPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.LEFT).group("chemical").build())
                .port(PortDefinition.builder("fluid", "fluid").direction(PortDirection.OUTPUT).filter(IS_FLUID)
                        .sides(RelativeSide.RIGHT).group("fluid").build())
                .data(ChemistryBehaviors.MODE)
                .upgradeSlots(2, ChemistryMachines::upgrade)
                .behavior(withEnergy(ChemistryBehaviors.rotary(
                        ProcessingBehavior.of(CONDENSING, new ProcessBinding(List.of("chemical"), "fluid", List.of("energy"))),
                        ProcessingBehavior.of(DECONDENSING, new ProcessBinding(List.of("fluid"), "chemical", List.of("energy")))),
                        buckets("fluid", true, true)))
                .build());
        // Pumps water or lava source blocks from below: 100 J/t for one block a second.
        machines.add(withBuckets(BasicMachines.base("electric_pump", 40_000), "fluid", 10_000, false, true, IS_FLUID, false)
                .port(PortDefinition.builder("fluid", "fluid").direction(PortDirection.OUTPUT).filter(IS_FLUID)
                        .sides(RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP).group("fluid").build())
                .upgradeSlots(2, upgrade -> upgrade(upgrade) || upgrade.equals(MekanizedRegistry.FILTER_UPGRADE))
                .behavior(withEnergy(ChemistryBehaviors.pump("fluid", "energy", 2_000, 20, 32), buckets("fluid", false, true)))
                .build());
        // Tanks: input on every side but the front, output through the front (fluid tanks: in on top, out at the bottom).
        long[][] chemicalTanks = {{64_000, 1_000}, {256_000, 16_000}, {1_024_000, 128_000}, {8_192_000, 512_000}};
        long[][] fluidTanks = {{32_000, 1_000}, {64_000, 4_000}, {128_000, 16_000}, {256_000, 64_000}};
        for (int i = 0; i < TIERS.length; i++) {
            machines.add(chemicalTankBlock(TIERS[i] + "_chemical_tank", chemicalTanks[i][0], chemicalTanks[i][1], false));
            machines.add(fluidTankBlock(TIERS[i] + "_fluid_tank", fluidTanks[i][0], fluidTanks[i][1], false));
        }
        machines.add(chemicalTankBlock("creative_chemical_tank", Long.MAX_VALUE / 4, 512_000, true));
        machines.add(fluidTankBlock("creative_fluid_tank", Long.MAX_VALUE / 4, 64_000, true));
        return machines;
    }

    private static boolean upgrade(UpgradeType upgrade) {
        return MekanizedRegistry.standardUpgrade(upgrade);
    }

    private static StorageDefinition chemicalTank(String id, long capacity, ResourcePredicate filter) {
        return StorageDefinition.tank(id, capacity).filter(filter);
    }

    private static PortDefinition input(String storage, ResourcePredicate filter, RelativeSide... sides) {
        PortDefinition.Builder port = PortDefinition.builder(storage, storage).direction(PortDirection.INPUT).filter(filter)
                .group(groupOf(filter));
        if (sides.length > 0) port.sides(sides[0], Arrays.copyOfRange(sides, 1, sides.length));
        return port.build();
    }

    private static PortDefinition output(String storage, ResourcePredicate filter) {
        return PortDefinition.builder(storage, storage).direction(PortDirection.OUTPUT).filter(filter).group(groupOf(filter)).build();
    }

    /** The side configuration group of a port carrying {@code filter}'s resources. */
    static String groupOf(ResourcePredicate filter) {
        if (filter == MekanizedRegistry.IS_ENERGY) return "energy";
        if (filter == IS_CHEMICAL) return "chemical";
        if (filter == IS_FLUID) return "fluid";
        return "items";
    }

    private static MachineBehavior.Factory process(RecipeType<ProcessingRecipe> recipes, List<String> inputs) {
        return ProcessingBehavior.of(recipes, new ProcessBinding(inputs, "output", List.of("energy")));
    }

    /** The energy item slot of {@link BasicMachines#base}, then the given conversion slots; auto-eject. */
    private static MachineBehavior.Factory withEnergy(MachineBehavior.Factory inner, ConvertingBehavior.Conversion... more) {
        List<ConvertingBehavior.Conversion> conversions = new ArrayList<>(List.of(more));
        conversions.addFirst(new ConvertingBehavior.Conversion("energy_slot", "energy", ENERGY_CONVERSION));
        return EjectBehavior.of(ConvertingBehavior.of(inner, conversions.toArray(ConvertingBehavior.Conversion[]::new)));
    }

    /**
     * A fluid tank with bucket slots: {@code bucket_in} (a full bucket empties into the tank when
     * {@code fill}, an empty one is filled from it when {@code drain}) and {@code bucket_out}; with
     * {@code inputPort}, pipes fill the tank from every side.
     */
    private static MachineType.Builder withBuckets(MachineType.Builder builder, String tank, long capacity, boolean fill, boolean drain,
                                                   ResourcePredicate accepts, boolean inputPort) {
        ResourcePredicate buckets = fill && drain ? RecipeFilters.inputOf(BUCKET_FILLING).or(RecipeFilters.inputOf(BUCKET_DRAINING))
                : fill ? RecipeFilters.inputOf(BUCKET_FILLING) : RecipeFilters.inputOf(BUCKET_DRAINING);
        builder.storage(StorageDefinition.tank(tank, capacity).filter(accepts))
                .storage(StorageDefinition.slots("bucket_in", 1, 16).filter(buckets))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()));
        if (inputPort) builder.port(input(tank, IS_FLUID));
        return builder;
    }

    private static ConvertingBehavior.Conversion[] buckets(String tank, boolean fill, boolean drain) {
        List<ConvertingBehavior.Conversion> conversions = new ArrayList<>();
        if (fill) conversions.add(new ConvertingBehavior.Conversion("bucket_in", tank, BUCKET_FILLING, "bucket_out"));
        if (drain) conversions.add(new ConvertingBehavior.Conversion("bucket_in", tank, BUCKET_DRAINING, "bucket_out"));
        return conversions.toArray(ConvertingBehavior.Conversion[]::new);
    }

    private static MachineType chemicalTankBlock(String name, long capacity, long output, boolean creative) {
        return MachineType.builder(key(name))
                .storage(chemicalTank("chemical", capacity, IS_CHEMICAL))
                .storage(StorageDefinition.slots("chemical_slot", 1, 64).filter(RecipeFilters.inputOf(CHEMICAL_CONVERSION)))
                .port(PortDefinition.builder("in", "chemical").direction(PortDirection.INPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM).group("chemical").build())
                .port(PortDefinition.builder("out", "chemical").direction(PortDirection.OUTPUT).filter(IS_CHEMICAL)
                        .sides(RelativeSide.FRONT).ratePerTick(output).group("chemical").build())
                .behavior(ConvertingBehavior.of(ChemistryBehaviors.tank("chemical", RelativeSide.FRONT, output, creative),
                        new ConvertingBehavior.Conversion("chemical_slot", "chemical", CHEMICAL_CONVERSION)))
                .build();
    }

    private static MachineType fluidTankBlock(String name, long capacity, long output, boolean creative) {
        return MachineType.builder(key(name))
                .storage(StorageDefinition.tank("fluid", capacity).filter(IS_FLUID))
                .storage(StorageDefinition.slots("bucket_in", 1, 16)
                        .filter(RecipeFilters.inputOf(BUCKET_FILLING).or(RecipeFilters.inputOf(BUCKET_DRAINING))))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()))
                .port(PortDefinition.builder("in", "fluid").direction(PortDirection.INPUT).filter(IS_FLUID)
                        .sides(RelativeSide.TOP, RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT).group("fluid").build())
                .port(PortDefinition.builder("out", "fluid").direction(PortDirection.OUTPUT).filter(IS_FLUID)
                        .sides(RelativeSide.BOTTOM).ratePerTick(output).group("fluid").build())
                .behavior(ConvertingBehavior.of(ChemistryBehaviors.tank("fluid", RelativeSide.BOTTOM, output, creative),
                        buckets("fluid", true, true)))
                .build();
    }
}
