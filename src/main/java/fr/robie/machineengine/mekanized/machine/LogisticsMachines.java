package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.addon.RegistrationContext;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineBehavior;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.machine.PortDefinition;
import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.machine.StorageDefinition;
import fr.robie.machineengine.api.network.NetworkBehavior;
import fr.robie.machineengine.api.network.NetworkType;
import fr.robie.machineengine.api.network.TransportType;
import fr.robie.machineengine.api.recipe.ProcessingRecipe;
import fr.robie.machineengine.api.recipe.RecipeType;
import fr.robie.machineengine.api.world.RelativeSide;
import fr.robie.machineengine.mekanized.MekanizedContent;
import fr.robie.machineengine.mekanized.MekanizedSettings;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static fr.robie.machineengine.mekanized.MekanizedContent.key;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.CHEMICAL_CONVERSION;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.ENERGY_CONVERSION;
import static fr.robie.machineengine.mekanized.machine.MekanizedRegistry.IS_CHEMICAL;

/**
 * Phase 4 (MEKANIZED_PLAN.md): logistical transporters, the logistical sorter and the factories, with
 * the tier installers' upgrade paths (machine to basic factory, then tier by tier).
 */
public final class LogisticsMachines {
    /** Items move in batches every 10 ticks, like Mekanism's transporters. */
    public static final NetworkType ITEM_NETWORK = NetworkType.of(key("items"), NetworkBehavior.distributing(10));
    public static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    private static final int[] LANES = {3, 5, 7, 9};
    public static final List<TransportType> TRANSPORTERS = transporters();

    /** A factory kind: its recipes, the machine it upgrades, and what that machine has besides items and energy. */
    public record Kind(String name, String machine, RecipeType<ProcessingRecipe> recipes, long energy,
                       long chemicalTank, boolean chemicalIsInput, boolean extraSlot, int outputsPerLane) {
    }

    public static final List<Kind> KINDS = List.of(
            new Kind("smelting", "energized_smelter", MekanizedRegistry.SMELTING, 20_000, 0, false, false, 1),
            new Kind("enriching", "enrichment_chamber", MekanizedRegistry.ENRICHING, 20_000, 0, false, false, 1),
            new Kind("crushing", "crusher", MekanizedRegistry.CRUSHING, 20_000, 0, false, false, 1),
            new Kind("compressing", "osmium_compressor", MekanizedRegistry.COMPRESSING, 80_000, 210, false, false, 1),
            new Kind("combining", "combiner", MekanizedRegistry.COMBINING, 40_000, 0, false, true, 1),
            new Kind("purifying", "purification_chamber", MekanizedRegistry.PURIFYING, 80_000, 210, false, false, 1),
            new Kind("injecting", "chemical_injection_chamber", ChemistryMachines.INJECTING, 160_000, 210, false, false, 1),
            new Kind("infusing", "metallurgic_infuser", MekanizedRegistry.METALLURGIC_INFUSING, 20_000, 1_000, true, false, 1),
            new Kind("sawing", "precision_sawmill", MekanizedRegistry.SAWING, 20_000, 0, false, false, 2));

    private LogisticsMachines() {
    }

    private static List<TransportType> transporters() {
        List<TransportType> transporters = new ArrayList<>();
        for (int i = 0; i < TIERS.length; i++) {
            // Items per batch (every 10 ticks), from the settings.
            transporters.add(TransportType.of(key(TIERS[i] + "_logistical_transporter"), ITEM_NETWORK,
                    MekanizedSettings.transporterRates[i], Items.anyItem()));
        }
        return List.copyOf(transporters);
    }

    static void register(RegistrationContext context) {
        context.networkTypes().register(ITEM_NETWORK);
        TRANSPORTERS.forEach(context.transportTypes()::register);
        machines().forEach(context.machineTypes()::register);
    }

    static List<MachineType> machines() {
        List<MachineType> machines = new ArrayList<>();
        machines.add(MachineType.builder(key("logistical_sorter"))
                .storage(StorageDefinition.slots("filter", 9, 1).filter(Items.anyItem()))
                .storage(StorageDefinition.slots("buffer", 1, 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("buffer", "buffer").direction(PortDirection.OUTPUT).sides(RelativeSide.FRONT).group("items").build())
                .behavior(SorterBehavior.of(10, 64))
                .build());
        for (int tier = 0; tier < TIERS.length; tier++) {
            for (Kind kind : KINDS) machines.add(factory(TIERS[tier], LANES[tier], kind));
        }
        return machines;
    }

    private static MachineType factory(String tier, int lanes, Kind kind) {
        MachineType.Builder builder = BasicMachines.base(tier + "_" + kind.name() + "_factory", kind.energy() * lanes)
                .storage(StorageDefinition.slots("output", lanes * kind.outputsPerLane(), 64).filter(Items.anyItem()))
                .port(PortDefinition.builder("output", "output").direction(PortDirection.OUTPUT).group("items").build())
                .upgradeSlots(2, kind.chemicalTank() > 0 && !kind.chemicalIsInput() ? MekanizedRegistry::chemicalUpgrade : MekanizedRegistry::standardUpgrade)
                .data(FactoryBehavior.PROGRESS);
        for (int lane = 0; lane < lanes; lane++) {
            String storage = FactoryBehavior.laneStorage(lane);
            builder.storage(StorageDefinition.slots(storage, 1, 64).filter(RecipeFilters.inputOf(kind.recipes())))
                    .port(PortDefinition.builder(storage, storage).direction(PortDirection.INPUT).group("items").build())
                    .data(FactoryBehavior.lanePercent(lane + 1));
        }
        List<String> secondary = new ArrayList<>();
        List<String> costs = new ArrayList<>(List.of("energy"));
        List<ConvertingBehavior.Conversion> conversions = new ArrayList<>(List.of(
                new ConvertingBehavior.Conversion("energy_slot", "energy", ENERGY_CONVERSION)));
        if (kind.chemicalTank() > 0) {
            var accepts = IS_CHEMICAL.and(kind.chemicalIsInput() ? RecipeFilters.usedBy(kind.recipes()) : RecipeFilters.costOf(kind.recipes()));
            builder.storage(StorageDefinition.tank("chemical", kind.chemicalTank() * lanes).filter(accepts))
                    .storage(StorageDefinition.slots("chemical_slot", 1, 64).filter(RecipeFilters.inputOf(CHEMICAL_CONVERSION)))
                    .port(PortDefinition.builder("chemical", "chemical").direction(PortDirection.INPUT).filter(IS_CHEMICAL).group("chemical").build());
            conversions.add(new ConvertingBehavior.Conversion("chemical_slot", "chemical", CHEMICAL_CONVERSION));
            (kind.chemicalIsInput() ? secondary : costs).add("chemical");
        }
        if (kind.extraSlot()) {
            builder.storage(StorageDefinition.slots("extra", 1, 64).filter(RecipeFilters.inputOf(kind.recipes())))
                    .port(PortDefinition.builder("extra", "extra").direction(PortDirection.INPUT).group("items").build());
            secondary.add("extra");
        }
        MachineBehavior.Factory factory = FactoryBehavior.of(kind.recipes(), lanes, secondary, costs);
        return builder.behavior(EjectBehavior.of(ConvertingBehavior.of(factory, conversions.toArray(ConvertingBehavior.Conversion[]::new))))
                .build();
    }

    /**
     * What a tier installer turns {@code machine} into: a basic installer turns a machine into its basic
     * factory, a higher one turns a factory of the tier below into its own tier. {@code null} otherwise.
     */
    public static @Nullable Key upgrade(@NotNull Key machine, @NotNull String installerTier) {
        if (!machine.namespace().equals(MekanizedContent.NAMESPACE)) return null;
        int tier = List.of(TIERS).indexOf(installerTier);
        if (tier < 0) return null;
        for (Kind kind : KINDS) {
            String from = tier == 0 ? kind.machine() : TIERS[tier - 1] + "_" + kind.name() + "_factory";
            if (machine.value().equals(from)) return key(installerTier + "_" + kind.name() + "_factory");
        }
        return null;
    }

    /** Lanes per factory tier, for menus. */
    public static final Map<String, Integer> LANES_BY_TIER = Map.of("basic", 3, "advanced", 5, "elite", 7, "ultimate", 9);
}
