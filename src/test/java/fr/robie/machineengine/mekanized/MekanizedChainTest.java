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
    /** What the plugin installs, tags first. */
    private static final List<String> DEFINITIONS = MekanizedPlugin.ENABLED_DEFINITIONS.stream()
            .sorted(java.util.Comparator.comparing((String name) -> name.startsWith("recipes_")).thenComparing(name -> name)).toList();

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

    // -- Phase 3: chemicals and fluids ----------------------------------------------------------

    @Test
    void theChemicalChainGivesFiveCrystalsPerOre() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl dissolution = world.place(type("chemical_dissolution_chamber"), FakeWorld.at(0, 64, 0), Direction.NORTH);
        fill(dissolution, "energy", MekanizedContent.ENERGY.stack(160_000));
        fill(dissolution, "chemical", chemical("sulfuric_acid", 1_000));
        fill(dissolution, "input", item("mekanized:osmium_ore").stack(1));
        world.tick(101);
        assertEquals(1_000, amountOfType(dissolution, "output", "dirty_osmium"), "1 osmium ore dissolves into 1000 mB of dirty slurry");
        assertEquals(1_000 - 100, amountOfType(dissolution, "chemical", "sulfuric_acid"), "1 mB of acid per tick");

        MachineInstanceImpl washer = world.place(type("chemical_washer"), FakeWorld.at(2, 64, 0), Direction.NORTH);
        fill(washer, "input", chemical("dirty_osmium", 1_000));
        fill(washer, "fluid", fluid("water", 10_000));
        powered(world, 1_001, washer);
        assertEquals(1_000, amountOfType(washer, "output", "clean_osmium"), "washing is one to one");
        assertEquals(10_000 - 5_000, amountOfType(washer, "fluid", "fluid/water"), "5 mB of water per mB of slurry");

        MachineInstanceImpl crystallizer = world.place(type("chemical_crystallizer"), FakeWorld.at(4, 64, 0), Direction.NORTH);
        fill(crystallizer, "input", chemical("clean_osmium", 1_000));
        powered(world, 5 * 200 + 5, crystallizer);
        assertEquals(5, amount(crystallizer, "output", "mekanized:crystal_osmium"), "200 mB of clean slurry per crystal");
    }

    @Test
    void anElectrolyticSeparatorSplitsWater() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl separator = place(world, "electrolytic_separator");
        fill(separator, "energy", MekanizedContent.ENERGY.stack(160_000));
        fill(separator, "input", fluid("water", 200));
        world.tick(110);
        assertEquals(200, amountOfType(separator, "output", "hydrogen"), "2 mB of water give 2 mB of hydrogen");
        assertEquals(100, amountOfType(separator, "output", "oxygen"), "and 1 mB of oxygen");
        assertEquals(0, amountOfType(separator, "input", "fluid/water"));
    }

    @Test
    void aRotaryCondensentratorGoesBothWays() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl rotary = place(world, "rotary_condensentrator");
        fill(rotary, "energy", MekanizedContent.ENERGY.stack(20_000));
        fill(rotary, "chemical", chemical("oxygen", 50));
        world.tick(60);
        assertEquals(50, amountOfType(rotary, "fluid", "fluid/oxygen"), "condensing: chemical to fluid");
        assertEquals(fr.robie.machineengine.api.machine.PortDirection.OUTPUT, rotary.context().portDirection("fluid"));

        rotary.context().set(fr.robie.machineengine.mekanized.machine.ChemistryBehaviors.MODE, "decondensing");
        rotary.wake(fr.robie.machineengine.api.machine.WakeReason.CONFIG_CHANGED);
        world.tick(60);
        assertEquals(50, amountOfType(rotary, "chemical", "oxygen"), "decondensing: fluid back to chemical");
        assertEquals(fr.robie.machineengine.api.machine.PortDirection.INPUT, rotary.context().portDirection("fluid"),
                "the fluid side became the input");
        assertEquals(fr.robie.machineengine.api.machine.PortDirection.OUTPUT, rotary.context().portDirection("chemical"));
    }

    @Test
    void bucketsFillAndEmptyFluidTanks() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = place(world, "basic_fluid_tank");
        fill(tank, "bucket_in", item("minecraft:water_bucket").stack(1));
        world.tick(2);
        assertEquals(1_000, amountOfType(tank, "fluid", "fluid/water"), "the bucket empties into the tank");
        assertEquals(1, amount(tank, "bucket_out", "minecraft:bucket"), "and comes back empty");

        // The player takes the empty bucket and puts it back in.
        try (Transaction transaction = tank.services().transactions().openOuter()) {
            assertEquals(1, tank.context().storage("bucket_out").extract(item("minecraft:bucket"), 1, transaction));
            transaction.commit();
        }
        fill(tank, "bucket_in", item("minecraft:bucket").stack(1));
        world.tick(2);
        assertEquals(0, amountOfType(tank, "fluid", "fluid/water"), "an empty bucket takes 1000 mB");
        assertEquals(1, amount(tank, "bucket_out", "minecraft:water_bucket"));
    }

    @Test
    void anElectricPumpTakesSourceBlocksBelowIt() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl pump = place(world, "electric_pump");
        fill(pump, "energy", MekanizedContent.ENERGY.stack(40_000));
        Key water = Key.key("minecraft", "water");
        // Flowing water below the pump, a source next to it.
        world.setFluid(FakeWorld.at(0, 63, 0), new fr.robie.machineengine.api.world.WorldFluid(water, false));
        world.setFluid(FakeWorld.at(1, 63, 0), new fr.robie.machineengine.api.world.WorldFluid(water, true));
        world.tick(2);
        assertEquals(1_000, amountOfType(pump, "fluid", "fluid/water"), "one source block is 1000 mB");
        assertEquals(null, world.fluidAt(FakeWorld.at(1, 63, 0)), "the source block is gone");
        assertEquals(40_000 - 2_000, energy(pump), "2 kJ per block");
        world.tick(100);
        assertEquals(1_000, amountOfType(pump, "fluid", "fluid/water"), "only flowing water is left: nothing more to take");
    }

    @Test
    void pressurizedTubesCarryChemicals() {
        FakeWorld world = FakeWorld.singleRegion(this.engine);
        MachineInstanceImpl tank = world.place(type("basic_chemical_tank"), FakeWorld.at(0, 64, 0), Direction.EAST);
        fill(tank, "chemical", chemical("brine", 30));
        world.placeTransport(fr.robie.machineengine.mekanized.machine.ChemistryMachines.TUBES.get(0), FakeWorld.at(1, 64, 0));
        MachineInstanceImpl crystallizer = world.place(type("chemical_crystallizer"), FakeWorld.at(2, 64, 0), Direction.NORTH);
        powered(world, 2 * 200 + 10, crystallizer);
        assertEquals(0, amountOfType(tank, "chemical", "brine"), "the tube emptied the tank's front");
        assertEquals(2, amount(crystallizer, "output", "mekanized:salt"), "15 mB of brine per salt");
    }

    /** Ticks {@code ticks}, keeping the machines' energy buffers full. */
    private static void powered(FakeWorld world, int ticks, MachineInstanceImpl... machines) {
        for (int done = 0; done < ticks; done += 20) {
            for (MachineInstanceImpl machine : machines) {
                long free = machine.inspect().storages().get("energy").stream().mapToLong(view -> view.capacity() - view.amount()).sum();
                if (free > 0) fill(machine, "energy", MekanizedContent.ENERGY.stack(free));
            }
            world.tick(Math.min(20, ticks - done));
        }
    }

    private ResourceStack fluid(String name, long amount) {
        return java.util.Objects.requireNonNull(MekanizedContent.fluid(name), name).stack(amount);
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
