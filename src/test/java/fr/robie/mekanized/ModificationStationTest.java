package fr.robie.mekanized;

import fr.robie.machineengine.api.item.ItemData;
import fr.robie.machineengine.api.item.Items;
import fr.robie.machineengine.api.machine.MachineAction;
import fr.robie.machineengine.api.resource.ResourceStack;
import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.transfer.StorageView;
import fr.robie.machineengine.core.lifecycle.CoreMachineEngine;
import fr.robie.machineengine.core.machine.MachineInstanceImpl;
import fr.robie.mekanized.machine.GearRules;
import fr.robie.mekanized.machine.ModificationStation;
import fr.robie.mekanized.machine.StationItems;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static fr.robie.mekanized.MekanizedTestKit.engine;
import static fr.robie.mekanized.MekanizedTestKit.fill;
import static fr.robie.mekanized.MekanizedTestKit.insert;
import static fr.robie.mekanized.MekanizedTestKit.powered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The modification station (D40): installing over time for energy, and the module list's clicks. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class ModificationStationTest {
    private static final Key CHEST = MekanizedContent.key("mekasuit_bodyarmor");
    private static final Key TOOL = MekanizedContent.key("meka_tool");
    private static final UUID PLAYER = UUID.randomUUID();

    /**
     * Stand-in items: gear carries {@code entries|energy} in its bytes; capacity is 1,000 J doubled per
     * energy unit level. Modules: energy unit (8 levels, body armour, not switchable), jetpack unit (1),
     * vein mining unit (the tool only).
     */
    private static final class FakeItems implements StationItems {
        private static final Map<String, ModuleItem> MODULES = Map.of(
                "energy_unit", new ModuleItem("energy_unit", 8, Set.of(CHEST), false),
                "jetpack_unit", new ModuleItem("jetpack_unit", 1, Set.of(CHEST), true),
                "vein_mining_unit", new ModuleItem("vein_mining_unit", 4, Set.of(TOOL), true));

        static ResourceVariant gear(Key id, List<String> entries, long energy) {
            return Items.variant(new ItemData(id, (String.join(",", entries) + "|" + energy).getBytes(StandardCharsets.UTF_8)));
        }

        static ResourceVariant module(String name) {
            return Items.variant(new ItemData(MekanizedContent.key("module_" + name), name.getBytes(StandardCharsets.UTF_8)));
        }

        private static String[] parts(ResourceVariant gear) {
            return new String(gear.components().get(Items.DATA).data(), StandardCharsets.UTF_8).split("\\|", -1);
        }

        static long energy(ResourceVariant gear) {
            return Long.parseLong(parts(gear)[1]);
        }

        private static Key id(ResourceVariant variant) {
            ItemData data = variant.components().get(Items.DATA);
            return data == null ? null : data.item();
        }

        private static long capacity(List<GearRules.Installed> modules) {
            return GearRules.scaled(1_000, 2, GearRules.installedLevel(modules, "energy_unit"));
        }

        private static ResourceVariant with(ResourceVariant gear, List<GearRules.Installed> modules) {
            return gear(id(gear), GearRules.format(modules), Math.min(energy(gear), capacity(modules)));
        }

        @Override
        public @Nullable List<GearRules.Installed> modules(@NotNull ResourceVariant gear) {
            Key id = id(gear);
            if (!CHEST.equals(id) && !TOOL.equals(id)) return null;
            String entries = parts(gear)[0];
            return GearRules.parse(entries.isEmpty() ? List.of() : Arrays.asList(entries.split(",")));
        }

        @Override
        public @Nullable ModuleItem module(@NotNull ResourceVariant item) {
            Key id = id(item);
            return id == null || !id.value().startsWith("module_") ? null : MODULES.get(id.value().substring("module_".length()));
        }

        @Override
        public @Nullable ModuleItem moduleNamed(@NotNull String name) {
            return MODULES.get(name);
        }

        @Override
        public @Nullable ResourceVariant moduleItem(@NotNull String name) {
            return MODULES.containsKey(name) ? module(name) : null;
        }

        @Override
        public @Nullable ResourceVariant install(@NotNull ResourceVariant gear, @NotNull String module) {
            List<GearRules.Installed> modules = GearRules.install(modules(gear), module, MODULES.get(module).maxLevel());
            return modules == null ? null : with(gear, modules);
        }

        @Override
        public @NotNull ResourceVariant remove(@NotNull ResourceVariant gear, @NotNull String module, int levels) {
            return with(gear, GearRules.remove(modules(gear), module, levels));
        }

        @Override
        public @Nullable ResourceVariant toggle(@NotNull ResourceVariant gear, @NotNull String module) {
            return MODULES.get(module).toggleable() ? with(gear, GearRules.toggle(modules(gear), module)) : null;
        }

        @Override
        public long energyLost(@NotNull ResourceVariant gear, @NotNull String module, int levels) {
            return GearRules.energyLost(energy(gear), capacity(GearRules.remove(modules(gear), module, levels)));
        }
    }

    private CoreMachineEngine engine;
    private FakeWorld world;
    private MachineInstanceImpl station;

    @BeforeEach
    void place() {
        ModificationStation.setItems(new FakeItems());
        this.engine = engine();
        this.world = FakeWorld.singleRegion(this.engine);
        this.station = MekanizedTestKit.place(this.engine, this.world, "modification_station");
    }

    private ResourceVariant gearIn() {
        return this.station.inspect().storages().get("gear").stream().map(StorageView::variant).filter(v -> v != null).findFirst().orElse(null);
    }

    private List<GearRules.Installed> installed() {
        return new FakeItems().modules(gearIn());
    }

    private MachineAction.Outcome click(MachineAction.ClickKind kind, int index) {
        return this.station.runAction(ModificationStation.ACTION, new MachineAction.Click(PLAYER, kind, index, null));
    }

    @Test
    void installsOneLevelAtATimeForEnergyAndTime() {
        assertEquals(1, insert(this.station, "gear", FakeItems.gear(CHEST, List.of(), 0)));
        assertEquals(2, insert(this.station, "module", FakeItems.module("energy_unit"), 2));
        powered(this.world, 20, this.station);
        assertEquals(List.of(), installed(), "not done after half the time");
        assertTrue(this.station.context().get(ModificationStation.PROGRESS) > 0);
        powered(this.world, 25, this.station);
        assertEquals(1, GearRules.installedLevel(installed(), "energy_unit"));
        assertEquals(1, MekanizedTestKit.amount(this.station, "module"), "one module used");
        powered(this.world, 45, this.station);
        assertEquals(2, GearRules.installedLevel(installed(), "energy_unit"));
        assertEquals(0, MekanizedTestKit.amount(this.station, "module"));
    }

    @Test
    void nothingIsInstalledWithoutEnergyOrWhenItCannotBe() {
        insert(this.station, "gear", FakeItems.gear(CHEST, List.of("jetpack_unit"), 0));
        insert(this.station, "module", FakeItems.module("vein_mining_unit"));
        powered(this.world, 60, this.station);
        assertEquals("This module does not fit this gear", this.station.context().get(ModificationStation.STATUS));
        assertEquals(1, MekanizedTestKit.amount(this.station, "module"), "kept");

        // At its max: a fresh station with a body armour that has its one jetpack level.
        CoreMachineEngine fresh = engine();
        FakeWorld world = FakeWorld.singleRegion(fresh);
        MachineInstanceImpl station = MekanizedTestKit.place(fresh, world, "modification_station");
        insert(station, "gear", FakeItems.gear(CHEST, List.of("jetpack_unit"), 0));
        insert(station, "module", FakeItems.module("jetpack_unit"));
        powered(world, 60, station);
        assertEquals("At its max level (1)", station.context().get(ModificationStation.STATUS));
        assertEquals(1, MekanizedTestKit.amount(station, "module"), "kept");
    }

    @Test
    void anUnpoweredStationWaits() {
        insert(this.station, "gear", FakeItems.gear(CHEST, List.of(), 0));
        insert(this.station, "module", FakeItems.module("jetpack_unit"));
        this.world.tick(60);
        assertEquals("Needs energy", this.station.context().get(ModificationStation.STATUS));
        assertEquals(List.of(), installed());
    }

    @Test
    void theListTakesLevelsOutAndSwitchesModules() {
        insert(this.station, "gear", FakeItems.gear(CHEST, List.of("jetpack_unit", "energy_unit:3"), 0));
        List<Map<String, String>> rows = this.station.list(ModificationStation.LIST);
        assertEquals(List.of("jetpack_unit", "energy_unit"), rows.stream().map(row -> row.get("name")).toList());
        assertEquals("3", rows.get(1).get("level"));
        assertEquals("8", rows.get(1).get("max"));
        assertEquals("on", rows.getFirst().get("state"));

        assertTrue(click(MachineAction.ClickKind.RIGHT, 0).done());
        assertEquals("off", this.station.list(ModificationStation.LIST).getFirst().get("state"), "switched off");
        MachineAction.Outcome energyUnit = click(MachineAction.ClickKind.RIGHT, 1);
        assertFalse(energyUnit.done(), "an energy unit cannot be switched off");
        assertNotNull(energyUnit.message());

        MachineAction.Outcome one = click(MachineAction.ClickKind.LEFT, 1);
        assertEquals(List.of(FakeItems.module("energy_unit").stack(1)), one.give());
        MachineAction.Outcome all = click(MachineAction.ClickKind.SHIFT, 1);
        assertEquals(List.of(new ResourceStack(FakeItems.module("energy_unit"), 2)), all.give());
        assertEquals(List.of(new GearRules.Installed("jetpack_unit", 1, false)), installed());
        assertFalse(click(MachineAction.ClickKind.LEFT, 5).done(), "no such row");
    }

    @Test
    void takingOutEnergyItWouldLoseAsksTwice() {
        // 2 levels: 4,000 J capacity, full; one level out leaves 2,000.
        insert(this.station, "gear", FakeItems.gear(CHEST, List.of("energy_unit:2"), 4_000));
        MachineAction.Outcome first = click(MachineAction.ClickKind.LEFT, 0);
        assertFalse(first.done(), "asks first");
        assertTrue(first.message() != null && first.give().isEmpty());
        assertEquals(2, GearRules.installedLevel(installed(), "energy_unit"));
        MachineAction.Outcome second = click(MachineAction.ClickKind.LEFT, 0);
        assertEquals(1, second.give().getFirst().amount(), "the same click again: taken out");
        assertEquals(2_000, FakeItems.energy(gearIn()));

        // Too late: more than confirm-seconds (5 s) after the first click, it asks again.
        assertFalse(click(MachineAction.ClickKind.LEFT, 0).done(), "the last level would lose energy too");
        this.world.tick(101);
        assertFalse(click(MachineAction.ClickKind.LEFT, 0).done(), "the second click came too late: asks again");
        this.world.tick(20);
        assertTrue(click(MachineAction.ClickKind.LEFT, 0).done(), "in time");
        assertEquals(0, GearRules.installedLevel(installed(), "energy_unit"));
    }

    @Test
    void anotherClickBetweenStartsOver() {
        insert(this.station, "gear", FakeItems.gear(CHEST, List.of("energy_unit:1"), 2_000));
        // Another click between the two starts over.
        assertFalse(click(MachineAction.ClickKind.LEFT, 0).done(), "asks");
        click(MachineAction.ClickKind.RIGHT, 0);
        assertFalse(click(MachineAction.ClickKind.LEFT, 0).done(), "a different click between: it asks again");
        assertEquals(1, GearRules.installedLevel(installed(), "energy_unit"));
    }

    @Test
    void theGearSlotTakesOnlyModularGear() {
        assertEquals(0, insert(this.station, "gear", MekanizedTestKit.item("minecraft:diamond_pickaxe")));
        assertEquals(0, insert(this.station, "module", MekanizedTestKit.item("minecraft:stone")));
        assertEquals(1, insert(this.station, "gear", FakeItems.gear(TOOL, List.of(), 0)));
        fill(this.station, "module", FakeItems.module("vein_mining_unit").stack(4));
        powered(this.world, 200, this.station);
        assertEquals(4, GearRules.installedLevel(installed(), "vein_mining_unit"), "up to its max, one module each");
    }

    @Test
    void theStationMenuShowsEveryModuleOnePieceTakes() {
        Map<String, Integer> perPiece = new java.util.TreeMap<>();
        for (String module : ShippedItems.withBehavior("mekanized:module")) {
            if (ShippedItems.option(module, "mekanized:module", "fits") instanceof List<?> fits) {
                fits.forEach(piece -> perPiece.merge(String.valueOf(piece), 1, Integer::sum));
            }
        }
        int most = perPiece.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        // StationMenu.ROWS (paper.menu) shows 15 rows.
        assertTrue(most >= 5 && most <= 15, "modules per piece: " + perPiece);
    }
}
