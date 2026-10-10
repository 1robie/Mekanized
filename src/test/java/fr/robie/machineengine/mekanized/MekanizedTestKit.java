package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.api.config.BlockOptions;
import fr.robie.machineengine.api.data.DataMap;
import fr.robie.machineengine.api.data.DataValue;
import fr.robie.machineengine.api.definition.DefinitionFile;
import fr.robie.machineengine.api.error.Result;
import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.ItemResources;
import fr.robie.machineengine.api.item.Items;
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
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.Entangloporters;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;
import fr.robie.machineengine.mekanized.machine.UtilityBehaviors;
import fr.robie.machineengine.api.multiblock.Multiblocks;
import fr.robie.machineengine.mekanized.machine.QioDrives;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.test.TestEngine;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What every Mekanized test needs: an engine with the content and its definitions (read from
 * the jar the way the plugin installs them), stand-in items identified by id, and the usual machine
 * helpers. Annotate a test class with {@code @ExtendWith(MekanizedTestKit.ResetStatics.class)} so the
 * static state (settings, hooks, listeners, registries of loaders) starts and ends each test clean.
 */
public final class MekanizedTestKit {
    public static final WorldPosition HERE = FakeWorld.at(0, 64, 0);

    /** What the plugin installs, tags first. */
    private static final List<String> DEFINITIONS = manifest().definitions().stream()
            .sorted(Comparator.comparing((String name) -> name.startsWith("recipes_")).thenComparing(name -> name)).toList();

    private MekanizedTestKit() {
    }

    // -- the engine -------------------------------------------------------------------------------

    /** A started engine with the Mekanized addon and every definition. */
    public static CoreMachineEngine engine() {
        return TestEngine.builder()
                .addon(AddonRegistration.builder("Mekanized", "1.0.0").register(MekanizedContent::register).build())
                .definitions(MekanizedTestKit::definitions, new DefinitionCompiler.ItemResolver() {
                    @Override
                    public Result<ResourceVariant> item(Key id) {
                        return Result.ok(MekanizedTestKit.item(id.asString()));
                    }
                })
                .start();
    }

    /** What the block config of the Mekanized block {@code name} would give its machine (D34): set it with {@link FakeWorld#setOptions}. */
    public static BlockOptions options(String name, Map<String, ?> options) {
        return BlockOptions.of(MekanizedContent.key(name).asString(), options, null, message -> {
            throw new AssertionError(message);
        });
    }

    public static MachineType type(CoreMachineEngine engine, String name) {
        return engine.registries().registry(Registries.MACHINE_TYPE).getOrThrow(MekanizedContent.key(name));
    }

    public static MachineInstanceImpl place(CoreMachineEngine engine, FakeWorld world, String name) {
        return world.place(type(engine, name), HERE, Direction.NORTH);
    }

    // -- stand-in resources -----------------------------------------------------------------------

    public static ResourceVariant item(String id) {
        return Items.variant(new ItemData(Key.key(id), id.getBytes(StandardCharsets.UTF_8)));
    }

    /** A chemical (gas, slurry, infuse type...) registered by Mekanized, found through the engine's registry. */
    public static ResourceStack chemical(CoreMachineEngine engine, String name, long amount) {
        return engine.registries().registry(Registries.RESOURCE_TYPE).getOrThrow(MekanizedContent.key(name)).stack(amount);
    }

    public static ResourceStack fluid(String name, long amount) {
        return Objects.requireNonNull(MekanizedContent.fluid(name), name).stack(amount);
    }

    // -- filling and reading machines -------------------------------------------------------------

    public static long insert(MachineInstanceImpl machine, String storage, ResourceVariant variant, long amount) {
        try (Transaction transaction = machine.services().transactions().openOuter()) {
            long inserted = machine.context().storage(storage).insert(variant, amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** Inserts one unit of {@code variant}; how many went in (0 or 1). */
    public static long insert(MachineInstanceImpl machine, String storage, ResourceVariant variant) {
        return insert(machine, storage, variant, 1);
    }

    /** Inserts {@code stack} and asserts that all of it fits. */
    public static void fill(MachineInstanceImpl machine, String storage, ResourceStack stack) {
        assertEquals(stack.amount(), insert(machine, storage, stack.variant(), stack.amount()), stack + " fits in " + storage);
    }

    /** Everything in {@code storage}. */
    public static long amount(MachineInstanceImpl machine, String storage) {
        return machine.inspect().storages().get(storage).stream().mapToLong(StorageView::amount).sum();
    }

    public static long capacity(MachineInstanceImpl machine, String storage) {
        return machine.inspect().storages().get(storage).stream().mapToLong(StorageView::capacity).sum();
    }

    public static long energy(MachineInstanceImpl machine) {
        return amount(machine, "energy");
    }

    /** The amount of one variant in {@code storage}. */
    public static long amountOf(MachineInstanceImpl machine, String storage, ResourceVariant variant) {
        return machine.inspect().storages().get(storage).stream().filter(view -> variant.equals(view.variant()))
                .mapToLong(StorageView::amount).sum();
    }

    /** The amount of the stand-in item {@code itemId} in {@code storage}. */
    public static long amount(MachineInstanceImpl machine, String storage, String itemId) {
        return amountOf(machine, storage, item(itemId));
    }

    /** The amount of the chemical or fluid type {@code type} (a Mekanized key path) in {@code storage}. */
    public static long amountOfType(MachineInstanceImpl machine, String storage, String type) {
        return machine.inspect().storages().get(storage).stream()
                .filter(view -> view.variant() != null && view.variant().type().key().equals(MekanizedContent.key(type)))
                .mapToLong(StorageView::amount).sum();
    }

    /** Ticks {@code ticks}, keeping the machines' energy buffers full. */
    public static void powered(FakeWorld world, int ticks, MachineInstanceImpl... machines) {
        for (int done = 0; done < ticks; done += 20) {
            for (MachineInstanceImpl machine : machines) {
                long free = machine.inspect().storages().get("energy").stream().mapToLong(view -> view.capacity() - view.amount()).sum();
                if (free > 0) fill(machine, "energy", MekanizedContent.ENERGY.stack(free));
            }
            world.tick(Math.min(20, ticks - done));
        }
    }

    // -- structures -------------------------------------------------------------------------------

    /** The walls of a box from (0, 64, 0), {@code size} blocks per side, of {@code casing}; {@code faces} overrides some. */
    public static void box(CoreMachineEngine engine, FakeWorld world, int size, String casing, Map<WorldPosition, String> faces) {
        box(engine, world, size, size, size, casing, faces);
    }

    /**
     * A box from (0, 64, 0) of {@code sx} x {@code sy} x {@code sz} blocks: the walls are {@code casing}, and every
     * position of {@code blocks} (walls or inside) gets the block named there.
     */
    public static void box(CoreMachineEngine engine, FakeWorld world, int sx, int sy, int sz, String casing, Map<WorldPosition, String> blocks) {
        for (int x = 0; x < sx; x++) for (int y = 64; y < 64 + sy; y++) for (int z = 0; z < sz; z++) {
            WorldPosition at = FakeWorld.at(x, y, z);
            boolean wall = x == 0 || x == sx - 1 || y == 64 || y == 63 + sy || z == 0 || z == sz - 1;
            String block = blocks.getOrDefault(at, wall ? casing : null);
            if (block != null) world.place(type(engine, block), at, Direction.NORTH);
        }
    }

    // -- the definitions, read from the jar like the plugin installs them -------------------------

    private static ContentManifest manifest() {
        try {
            return ContentManifest.load(MekanizedTestKit.class.getClassLoader());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<DefinitionFile> definitions() {
        List<DefinitionFile> files = new ArrayList<>();
        for (String name : DEFINITIONS) {
            try (InputStream in = MekanizedTestKit.class.getClassLoader().getResourceAsStream("mekanized/definitions/" + name)) {
                if (in == null) throw new IllegalStateException("missing definitions " + name);
                Map<String, Object> yaml = new Yaml().load(in);
                files.add(new DefinitionFile("definitions/mekanized/" + name, (DataMap) toData(yaml)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
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
            case List<?> list -> DataValue.list(list.stream().map(MekanizedTestKit::toData).toList());
            case Boolean bool -> DataValue.of(bool);
            case Double number -> DataValue.of(number);
            case Number number -> DataValue.of(number.longValue());
            default -> DataValue.of(String.valueOf(value));
        };
    }

    // -- energy items: the charge lives in the stand-in item's bytes ("id|joules") ----------------

    /** The charge of a stand-in item (0 when it has none). */
    public static long chargeOf(ResourceVariant item) {
        String text = new String(item.components().get(Items.DATA).data(), StandardCharsets.UTF_8);
        return text.contains("|") ? Long.parseLong(text.substring(text.indexOf('|') + 1)) : 0;
    }

    /**
     * Installs a headless {@link ItemResources.Access} for energy: capacities from the shipped item
     * configs ({@link ShippedItems}); {@link ResetStatics} removes it again.
     */
    public static void installChargeAccess() {
        ItemResources.setAccess(new ItemResources.Access() {
            @Override
            public long capacity(ResourceVariant item, Key resource) {
                return ShippedItems.capacity(item.components().get(Items.DATA).item().value(), resource.asString());
            }

            @Override
            public long amount(ResourceVariant item, Key resource) {
                return chargeOf(item);
            }

            @Override
            public ResourceVariant withAmount(ResourceVariant item, Key resource, long energy) {
                Key id = item.components().get(Items.DATA).item();
                return Items.variant(new ItemData(id, (id.asString() + "|" + energy).getBytes(StandardCharsets.UTF_8)));
            }
        });
    }

    // -- static state -----------------------------------------------------------------------------

    /**
     * Resets the static state a test can touch, before and after each test: the settings holders, the hooks
     * ({@link UtilityBehaviors#setCrafting}, {@link UtilityBehaviors#setItemFactory}, {@link ItemResources#setAccess}),
     * the platform listeners of {@link Frequencies} and {@link ChunkLoaders}, and the registries of
     * frequencies, structures, chunk loaders, buffers and radiation sources.
     */
    public static final class ResetStatics implements BeforeEachCallback, AfterEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            reset();
        }

        @Override
        public void afterEach(ExtensionContext context) {
            reset();
        }

        public static void reset() {
            TeleporterSettings.reset();
            ChunkLoadingSettings.reset();
            RadiationSettings.reset();
            DigitalMinerSettings.reset();
            QioSettings.reset();
            QioDrives.setAccess(null);
            UtilityBehaviors.setCrafting(null);
            UtilityBehaviors.setItemFactory(null);
            ItemResources.setAccess(null);
            fr.robie.machineengine.mekanized.machine.ModificationStation.setItems(null);
            Frequencies.onTeleportersChanged(null);
            ChunkLoaders.onChange(null);
            Frequencies.clear();
            Multiblocks.clear();
            ChunkLoaders.loaders().clear();
            Entangloporters.buffers().clear();
            Radiation.sources().clear();
        }
    }
}
