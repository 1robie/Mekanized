package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.machine.MachineContext;
import fr.robie.machineengine.api.network.NetworkBehavior;
import fr.robie.machineengine.api.network.NetworkType;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.Recipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.registry.ReloadContext;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.api.upgrade.Modifier;
import fr.robie.machineengine.api.upgrade.ModifierType;
import fr.robie.machineengine.api.upgrade.StandardModifiers;
import fr.robie.machineengine.api.upgrade.UpgradeType;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.multiblock.MultiblockMachines;
import fr.robie.machineengine.mekanized.multiblock.ReactorMachines;
import java.util.List;
import java.util.function.Consumer;
import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * What every Mekanized machine shares and the registration of all its content: the recipe types, upgrades,
 * modifiers, the energy network and cables, the upgrade rules, and the order the machine families register in.
 * Numbers are Mekanism's defaults (energy in Joules, chemicals in mB); recipe durations and per-tick
 * energy come from the recipe files, where server owners can change them.
 */
public final class MekanizedRegistry {
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
    /**
     * Mekanism: each speed upgrade makes processes faster and costlier; machines whose recipes take a
     * single tick (washer, infuser, separator...) run one more operation per tick instead.
     */
    public static final UpgradeType SPEED = UpgradeType.of(key("upgrade_speed"), 8,
            Modifier.multiplyTotal(StandardModifiers.PROCESS_DURATION, -250),
            Modifier.multiplyTotal(StandardModifiers.PROCESS_COST, 333),
            Modifier.add(StandardModifiers.PROCESSES_PER_TICK, 1));
    /** A machine's energy buffer size, scaled by energy upgrades. */
    public static final ModifierType ENERGY_CAPACITY =
            ModifierType.nonNegative(key("energy_capacity"));
    /** Energy and chemical costs per tick, each lowered by its own upgrade. MachineEngine registers the energy one (its category). */
    public static final ModifierType ENERGY_COST = StandardModifiers.processCost(MekanizedContent.ENERGY_CATEGORY);
    public static final ModifierType CHEMICAL_COST = StandardModifiers.processCost(MekanizedContent.CHEMICAL);
    /** Mekanism: each energy upgrade lowers the energy a process costs and enlarges the energy buffer (+100 % of the base). */
    public static final UpgradeType ENERGY_UPGRADE = UpgradeType.of(key("upgrade_energy"), 8,
            Modifier.multiplyTotal(ENERGY_COST, -200),
            Modifier.addMultipliedBase(ENERGY_CAPACITY, 1_000));
    /** Mekanism: each chemical upgrade lowers the chemical used per tick (compressor, purification and injection chambers, their factories). */
    public static final UpgradeType CHEMICAL_UPGRADE = UpgradeType.of(key("upgrade_chemical"), 8, Modifier.multiplyTotal(CHEMICAL_COST, -200));
    /** Electric pump: pumps heavy water out of water (10 mB a block). */
    public static final UpgradeType FILTER_UPGRADE = UpgradeType.of(key("upgrade_filter"), 1);
    /** Keeps the machine's chunk loaded (Paper side: {@code ChunkLoaders}). */
    public static final UpgradeType ANCHOR_UPGRADE = UpgradeType.of(key("upgrade_anchor"), 1);
    /** Digital Miner: fills each mined block with stone. */
    public static final UpgradeType STONE_GENERATOR_UPGRADE = UpgradeType.of(key("upgrade_stone_generator"), 1);
    public static final List<UpgradeType> UPGRADES = List.of(SPEED, ENERGY_UPGRADE, CHEMICAL_UPGRADE, FILTER_UPGRADE, ANCHOR_UPGRADE,
            STONE_GENERATOR_UPGRADE);

    // -- energy transport ------------------------------------------------------------------------
    public static final NetworkType ENERGY_NETWORK = NetworkType.of(key("energy"), NetworkBehavior.distributing());
    static final ResourcePredicate IS_ENERGY = ResourcePredicate.type(MekanizedContent.ENERGY);
    static final ResourcePredicate IS_CHEMICAL = ResourcePredicate.category(MekanizedContent.CHEMICAL);
    /** Joules per tick, per tier: the default when a cable's block config has no {@code rate} (D34). */
    public static final long[] CABLE_RATES = {8_000, 128_000, 1_024_000, 8_192_000};
    public static final List<TransportType> CABLES = List.of(
            TransportType.of(key("basic_universal_cable"), ENERGY_NETWORK, CABLE_RATES[0], IS_ENERGY),
            TransportType.of(key("advanced_universal_cable"), ENERGY_NETWORK, CABLE_RATES[1], IS_ENERGY),
            TransportType.of(key("elite_universal_cable"), ENERGY_NETWORK, CABLE_RATES[2], IS_ENERGY),
            TransportType.of(key("ultimate_universal_cable"), ENERGY_NETWORK, CABLE_RATES[3], IS_ENERGY));

    /** The server's furnace recipes for the energized smelter, set by the platform (none in headless tests). */
    private static volatile Consumer<ReloadContext<Recipe>> furnaceRecipes;

    private MekanizedRegistry() {
    }

    public static void furnaceRecipes(Consumer<ReloadContext<Recipe>> provider) {
        furnaceRecipes = provider;
    }

    /** The upgrades every machine with upgrade slots takes: speed, energy, anchor. */
    public static boolean standardUpgrade(UpgradeType upgrade) {
        return upgrade.equals(SPEED) || upgrade.equals(ENERGY_UPGRADE) || upgrade.equals(ANCHOR_UPGRADE);
    }

    /** Machines using a chemical per tick also take chemical upgrades. */
    public static boolean chemicalUpgrade(UpgradeType upgrade) {
        return standardUpgrade(upgrade) || upgrade.equals(CHEMICAL_UPGRADE);
    }

    /** Whether {@code upgrade} is installed in the machine. */
    public static boolean installed(MachineContext context, UpgradeType upgrade) {
        return context.machine().upgrades().stream().anyMatch(installed -> installed.type().equals(upgrade) && installed.count() > 0);
    }

    private static RecipeType<ProcessingRecipe> recipeType(String name) {
        return RecipeType.of(key(name), ProcessingRecipe.class);
    }
    public static void register(RegistrationContext context) {
        RecipeFilters.attach(context);
        RECIPE_TYPES.forEach(context.recipeTypes()::register);
        context.modifierTypes().register(ENERGY_CAPACITY);
        context.modifierTypes().register(CHEMICAL_COST);
        UPGRADES.forEach(context.upgradeTypes()::register);
        context.networkTypes().register(ENERGY_NETWORK);
        CABLES.forEach(context.transportTypes()::register);
        BasicMachines.machines().forEach(context.machineTypes()::register);
        Consumer<ReloadContext<Recipe>> furnaces = furnaceRecipes;
        if (furnaces != null) context.recipes(furnaces::accept);
        ChemistryMachines.register(context);
        LogisticsMachines.register(context);
        ReactionAndPigmentMachines.register(context);
        UtilityMachines.register(context);
        HeatMachines.register(context);
        AutomationMachines.register(context);
        ChunkLoaders.register(context);
        Entangloporters.register(context);
        Radiation.register(context);
        Liquifier.register(context);
        ModificationStation.register(context);
        ReactorMachines.register(context);
        MultiblockMachines.register(context);
    }
}
