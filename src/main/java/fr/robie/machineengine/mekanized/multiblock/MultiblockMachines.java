package fr.robie.machineengine.mekanized.multiblock;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.resource.match.ResourcePredicate;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.MekanizedSettings;
import fr.robie.machineengine.mekanized.machine.ChemistryMachines;
import fr.robie.machineengine.mekanized.machine.ConvertingBehavior;
import fr.robie.machineengine.mekanized.machine.MachineParts;
import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;

/**
 * Phase 5a (MEKANIZED_PLAN.md): the Dynamic Tank and the Induction Matrix, with Mekanism's numbers:
 * 350,000 mB of fluid or 16,000,000 mB of chemical per block inside a tank; induction cells of 8 GJ to
 * 4 TJ, providers of 256 kJ/t to 131 MJ/t.
 */
public final class MultiblockMachines {
    private static final ResourcePredicate IS_ENERGY = ResourcePredicate.type(MekanizedContent.ENERGY);
    private static final ResourcePredicate IS_CHEMICAL = ResourcePredicate.category(MekanizedContent.CHEMICAL);
    private static final ResourcePredicate IS_FLUID = ResourcePredicate.category(MekanizedContent.FLUID);
    private static final Set<Key> AIR = Set.of(Key.key("minecraft", "air"), Key.key("minecraft", "cave_air"), Key.key("minecraft", "void_air"));
    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    private static final long[] CELLS = {8_000_000_000L, 64_000_000_000L, 512_000_000_000L, 4_000_000_000_000L};
    private static final long[] PROVIDERS = {256_000L, 2_048_000L, 16_384_000L, 131_072_000L};

    public static final Multiblocks.Type DYNAMIC_TANK = new Multiblocks.Type(key("dynamic_tank"), key("dynamic_tank"),
            Set.of(key("dynamic_tank"), key("dynamic_valve"), key("structural_glass")), AIR::contains,
            structure -> Map.of("fluid", structure.volume() * MekanizedSettings.tankFluidPerBlock,
                    "chemical", structure.volume() * MekanizedSettings.tankChemicalPerBlock));

    public static final Multiblocks.Type INDUCTION_MATRIX = new Multiblocks.Type(key("induction_matrix"), key("induction_casing"),
            Set.of(key("induction_casing"), key("induction_port"), key("structural_glass")),
            block -> AIR.contains(block) || block.value().endsWith("_induction_cell") || block.value().endsWith("_induction_provider"),
            structure -> Map.of("energy", sum(structure, "_induction_cell", CELLS)));

    /** Mekanism's evaporating recipes: water to brine, brine to liquid lithium. */
    public static final RecipeType<ProcessingRecipe> EVAPORATING =
            RecipeType.of(key("evaporating"), ProcessingRecipe.class);

    /**
     * A 4x4 tower, 3 to 18 blocks high, open at the top, with exactly one controller. The input tank
     * holds 16,000 mB per level, the output 10,000 mB.
     */
    public static final Multiblocks.Type THERMAL_EVAPORATION = new Multiblocks.Type(key("thermal_evaporation"),
            key("thermal_evaporation_block"),
            Set.of(key("thermal_evaporation_block"), key("thermal_evaporation_valve"), key("thermal_evaporation_controller")),
            AIR::contains,
            structure -> Map.of("input", structure.height() * MekanizedSettings.evaporationTankPerLevel, "output", 10_000L),
            4, true, key("thermal_evaporation_controller"));

    private MultiblockMachines() {
    }

    /** The energy cells hold, or providers give per tick, in a structure. */
    static long sum(Multiblocks.Structure structure, String suffix, long[] perTier) {
        long total = 0;
        for (int i = 0; i < TIERS.length; i++) total += structure.count(key(TIERS[i] + suffix)) * perTier[i];
        return total;
    }

    public static void register(RegistrationContext context) {
        context.recipeTypes().register(EVAPORATING);
        List<Multiblocks.Type> tower = List.of(THERMAL_EVAPORATION);
        // The tower's casing: the anchor evaporates; bucket slots for both tanks.
        context.machineTypes().register(MachineType.builder(key("thermal_evaporation_block"))
                .storage(StorageDefinition.tank("input", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("output", 1).filter(IS_FLUID))
                .storage(StorageDefinition.slots("bucket_in", 1, 16).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("output_bucket", 1, 16).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()))
                .behavior(ConvertingBehavior.of(
                        MemberBehavior.frame(tower, new Evaporation(EVAPORATING), Evaporation.EVERY_TICKS),
                        new ConvertingBehavior.Conversion("bucket_in", "input",
                                ChemistryMachines.BUCKET_FILLING, "bucket_out"),
                        new ConvertingBehavior.Conversion("output_bucket", "output",
                                ChemistryMachines.BUCKET_DRAINING, "bucket_out")))
                .build());
        context.machineTypes().register(MachineType.builder(key("thermal_evaporation_valve"))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .behavior(MemberBehavior.valve(tower, List.of("input"), List.of("output")))
                .build());
        context.machineTypes().register(MachineType.builder(key("thermal_evaporation_controller"))
                .behavior(MemberBehavior.frame(tower))
                .build());
        List<Multiblocks.Type> tank = List.of(DYNAMIC_TANK);
        List<Multiblocks.Type> matrix = List.of(INDUCTION_MATRIX);
        // The tank's casing: the anchor holds the fluid or chemical, and has bucket slots.
        context.machineTypes().register(MachineType.builder(key("dynamic_tank"))
                .storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID))
                .storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL))
                .storage(StorageDefinition.slots("bucket_in", 1, 16).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("bucket_out", 1, 16).filter(Items.anyItem()))
                .behavior(MachineParts.withBuckets(MemberBehavior.frame(tank), "fluid"))
                .build());
        context.machineTypes().register(valve("dynamic_valve", tank, List.of("fluid", "chemical"), true)
                .port(PortDefinition.builder("fluid", "fluid").filter(IS_FLUID).group("fluid").build())
                .port(PortDefinition.builder("chemical", "chemical").filter(IS_CHEMICAL).group("chemical").build())
                .build());
        context.machineTypes().register(MachineType.builder(key("structural_glass"))
                .behavior(MemberBehavior.frame(List.of(DYNAMIC_TANK, INDUCTION_MATRIX)))
                .build());
        context.machineTypes().register(MachineType.builder(key("induction_casing"))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .behavior(MemberBehavior.frame(matrix))
                .build());
        context.machineTypes().register(MachineType.builder(key("induction_port"))
                .storage(StorageDefinition.tank("energy", 1).filter(IS_ENERGY))
                .port(PortDefinition.builder("energy", "energy").filter(IS_ENERGY).group("energy").build())
                .data(MemberBehavior.PORT_MODE)
                .behavior(MemberBehavior.port(matrix, "energy", structure -> sum(structure, "_induction_provider", PROVIDERS)))
                .build());
    }

    private static MachineType.Builder valve(String name, List<Multiblocks.Type> types, List<String> storages, boolean exclusive) {
        MachineType.Builder builder = MachineType.builder(key(name));
        // Empty storages for the ports, so pipes and tubes connect; the valve shows the anchor's.
        if (storages.contains("fluid")) builder.storage(StorageDefinition.tank("fluid", 1).filter(IS_FLUID));
        if (storages.contains("chemical")) builder.storage(StorageDefinition.tank("chemical", 1).filter(IS_CHEMICAL));
        return builder.behavior(MemberBehavior.valve(types, storages, exclusive));
    }
}
