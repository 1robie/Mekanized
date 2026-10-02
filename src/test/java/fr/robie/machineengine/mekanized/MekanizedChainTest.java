package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.api.data.DataMap;
import fr.robie.machineengine.api.data.DataValue;
import fr.robie.machineengine.api.definition.DefinitionFile;
import fr.robie.machineengine.api.diagnostic.Diagnostic;
import fr.robie.machineengine.api.error.Result;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineEnvironment;
import fr.robie.machineengine.api.machine.MachineType;
import fr.robie.machineengine.api.registry.Registries;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.api.transfer.Transaction;
import fr.robie.machineengine.api.world.Direction;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.core.definition.DefinitionCompiler;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.machineengine.mekanized.machine.MekanizedMachines;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.test.TestEngine;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The imported content working end to end, headless: Mekanism's recipes and tags (as installed on a
 * server), Mekanized's machines and the MachineEngine core. Items are stand-ins identified by id.
 */
class MekanizedChainTest {
    private static final WorldPosition HERE = FakeWorld.at(0, 64, 0);
    private static final List<String> DEFINITIONS = List.of("tags.yml", "vanilla_aliases.yml", "recipes_enriching.yml",
            "recipes_crushing.yml", "recipes_combining.yml", "recipes_sawing.yml", "recipes_metallurgic_infusing.yml",
            "recipes_compressing.yml", "recipes_purifying.yml", "recipes_chemical_conversion.yml", "recipes_energy_conversion.yml");

    private static ResourceVariant item(String id) {
        return Items.variant(new ItemData(Key.key(id), id.getBytes(StandardCharsets.UTF_8)));
    }

    private final CoreMachineEngine engine = TestEngine.builder()
            .addon(AddonRegistration.builder("Mekanized", "1.0.0").register(MekanizedContent::register).build())
            .definitions(MekanizedChainTest::definitions, new DefinitionCompiler.ItemResolver() {
                @Override
                public Result<ResourceVariant> item(Key id) {
                    return Result.ok(MekanizedChainTest.item(id.asString()));
                }
            })
            .start();

    @Test
    void everyImportedRecipeAndTagLoads() {
        List<Diagnostic> problems = new ArrayList<>(this.engine.startupReport().errors());
        problems.addAll(this.engine.startupReport().warnings());
        assertTrue(problems.isEmpty(), "imported definitions load cleanly: " + this.engine.startupReport().format());
        assertTrue(this.engine.registries().registry(Registries.RECIPE).size() > 400, "recipes loaded");
    }

    @Test
    void anEnrichmentChamberDoublesOre() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(world, "enrichment_chamber");
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(201);
        assertEquals(2, amount(chamber, "output", "mekanized:dust_osmium"), "1 osmium ore gives 2 osmium dust");
        assertEquals(20_000 - 200 * 50, energy(chamber), "50 J per tick for 200 ticks");
    }

    @Test
    void machinesNeedEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(world, "enrichment_chamber");
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(400);
        assertEquals(0, amount(chamber, "output", "mekanized:dust_osmium"), "no energy, no dust");
        assertEquals(0, chamber.inspect().process() == null ? 0 : chamber.inspect().process().progress(), "and no progress");
    }

    @Test
    void aMetallurgicInfuserMakesInfusedAlloyFromRedstone() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl infuser = place(world, "metallurgic_infuser");
        fill(infuser, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(infuser, "chemical_slot", item("minecraft:redstone").stack(1));
        fill(infuser, "input", item("minecraft:copper_ingot").stack(1));
        world.tick(205);
        assertEquals(1, amount(infuser, "output", "mekanized:alloy_infused"), "copper ingot + 10 redstone infuse type: " + infuser.inspect());
    }

    @Test
    void slotsOnlyTakeWhatTheirRecipesUse() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl infuser = place(world, "metallurgic_infuser");
        assertEquals(0, insert(infuser, "input", item("minecraft:redstone")), "redstone is no infusing input");
        assertEquals(0, insert(infuser, "energy_slot", item("mekanized:osmium_ore")), "an ore gives no energy");
        assertEquals(1, insert(infuser, "chemical_slot", item("minecraft:redstone")), "redstone is an infuse source");
        assertEquals(1, insert(infuser, "input", item("minecraft:copper_ingot")));
    }

    private static long insert(MachineInstanceImpl machine, String storage, ResourceVariant variant) {
        try (Transaction transaction = machine.services().transactions().openOuter()) {
            long inserted = machine.context().storage(storage).insert(variant, 1, transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Test
    void speedUpgradesMakeItFasterAndCostlier() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(world, "enrichment_chamber");
        world.runInRegionOf(HERE, () -> assertTrue(chamber.installUpgrade(MekanizedMachines.SPEED, 2).isOk()));
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(114);
        assertEquals(2, amount(chamber, "output", "mekanized:dust_osmium"), "200 x 0.75 x 0.75 = 112 ticks");
    }

    @Test
    void aPurificationChamberTriplesOreWithOxygenFromItsTank() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(world, "purification_chamber");
        fill(chamber, "energy", MekanizedContent.ENERGY.stack(80_000));
        fill(chamber, "chemical", chemical("oxygen", 210));
        fill(chamber, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(201);
        assertEquals(3, amount(chamber, "output", "mekanized:clump_osmium"), "1 ore gives 3 clumps");
        assertEquals(10, amountOfType(chamber, "chemical", "oxygen"), "1 mB of oxygen per tick");
    }

    @Test
    void redstoneInTheEnergySlotBecomesEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl chamber = place(world, "crusher");
        fill(chamber, "energy_slot", item("minecraft:redstone").stack(1));
        world.tick(2);
        assertEquals(10_000, energy(chamber), "Mekanism: 10 kJ per redstone (vanilla redstone is in c:dusts/redstone)");
    }

    @Test
    void generatorsMakeEnergy() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl heat = place(world, "heat_generator");
        fill(heat, "fuel", item("minecraft:coal").stack(1));
        world.tick(11);
        assertTrue(energy(heat) >= 10 * 200, "200 J per tick while coal burns: " + energy(heat));

        WorldPosition there = FakeWorld.at(10, 64, 0);
        world.setEnvironment(new MachineEnvironment(true, true, false, 15));
        MachineInstanceImpl solar = world.place(type("solar_generator"), there, Direction.NORTH);
        world.tick(41);
        assertTrue(energy(solar) >= 2 * 20 * 50, "50 J per tick in the sun: " + energy(solar));
        assertTrue(solar.isActive());
    }

    // ------------------------------------------------------------------------------------------

    private MachineType type(String name) {
        return this.engine.registries().registry(Registries.MACHINE_TYPE).getOrThrow(MekanizedContent.key(name));
    }

    private MachineInstanceImpl place(FakeWorld world, String name) {
        return world.place(type(name), HERE, Direction.NORTH);
    }

    private ResourceStack chemical(String name, long amount) {
        return this.engine.registries().registry(Registries.RESOURCE_TYPE).getOrThrow(MekanizedContent.key(name)).stack(amount);
    }

    private static void fill(MachineInstanceImpl machine, String storage, ResourceStack stack) {
        try (Transaction transaction = machine.services().transactions().openOuter()) {
            long inserted = machine.context().storage(storage).insert(stack.variant(), stack.amount(), transaction);
            assertEquals(stack.amount(), inserted, stack + " fits in " + storage);
            transaction.commit();
        }
    }

    private static long energy(MachineInstanceImpl machine) {
        return machine.inspect().storages().get("energy").stream().mapToLong(StorageView::amount).sum();
    }

    private static long amount(MachineInstanceImpl machine, String storage, String itemId) {
        ResourceVariant expected = item(itemId);
        return machine.inspect().storages().get(storage).stream().filter(view -> expected.equals(view.variant()))
                .mapToLong(StorageView::amount).sum();
    }

    private static long amountOfType(MachineInstanceImpl machine, String storage, String chemical) {
        return machine.inspect().storages().get(storage).stream()
                .filter(view -> view.variant() != null && view.variant().type().key().equals(MekanizedContent.key(chemical)))
                .mapToLong(StorageView::amount).sum();
    }

    // -- the generated definitions, read from the jar like the plugin installs them ---------------

    private static List<DefinitionFile> definitions() {
        List<DefinitionFile> files = new ArrayList<>();
        for (String name : DEFINITIONS) {
            try (InputStream in = MekanizedChainTest.class.getClassLoader().getResourceAsStream("mekanized/definitions/" + name)) {
                if (in == null) throw new IllegalStateException("missing generated definitions " + name);
                Map<String, Object> yaml = new Yaml().load(in);
                files.add(new DefinitionFile("definitions/mekanized/" + name, (DataMap) toData(yaml)));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return files;
    }

    private static DataValue toData(Object value) {
        return switch (value) {
            case Map<?, ?> map -> {
                Map<String, DataValue> entries = new LinkedHashMap<>();
                map.forEach((key, element) -> entries.put(String.valueOf(key), toData(element)));
                yield DataMap.of(entries);
            }
            case List<?> list -> DataValue.list(list.stream().map(MekanizedChainTest::toData).toList());
            case Boolean bool -> DataValue.of(bool);
            case Double number -> DataValue.of(number);
            case Number number -> DataValue.of(number.longValue());
            default -> DataValue.of(String.valueOf(value));
        };
    }
}
