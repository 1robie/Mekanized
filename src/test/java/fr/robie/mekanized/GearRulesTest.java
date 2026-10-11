package fr.robie.mekanized;

import fr.robie.mekanized.machine.GearRules;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Gear capacity and module rules, with no Bukkit item in sight. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class GearRulesTest {
    private static final Key HELMET = MekanizedContent.key("mekasuit_helmet");
    private static final Key BOOTS = MekanizedContent.key("mekasuit_boots");
    private static final Key TOOL = MekanizedContent.key("meka_tool");

    @Test
    void aModuleGoesIntoTheFirstFittingPieceBelowItsMaxLevel() {
        List<Key> worn = java.util.Arrays.asList(HELMET, null, null, BOOTS, TOOL);
        List<Integer> energy = List.of(8, 0, 0, 2, 0);
        assertEquals(3, GearRules.pick(worn, energy::get, Set.of(HELMET, BOOTS), 8), "the helmet is at its max");
        assertEquals(0, GearRules.pick(worn, energy::get, Set.of(HELMET, BOOTS), 9), "a higher max: the helmet takes more");
        List<Integer> none = List.of(0, 0, 0, 0, 0);
        assertEquals(4, GearRules.pick(worn, none::get, Set.of(TOOL), 4), "the Meka-Tool in the off hand");
        List<Integer> full = List.of(1, 0, 0, 1, 0);
        assertEquals(0, GearRules.pick(worn, full::get, Set.of(HELMET, BOOTS), 1), "all at max: the first, which says so");
        assertEquals(-1, GearRules.pick(worn, none::get, Set.of(MekanizedContent.key("mekasuit_pants")), 1), "not worn");
    }

    @Test
    void everyModuleItemSaysWhereItFits() {
        assertEquals(List.of("mekanized:mekasuit_helmet", "mekanized:mekasuit_bodyarmor", "mekanized:mekasuit_pants",
                "mekanized:mekasuit_boots"), ShippedItems.option("module_energy_unit", "mekanized:module", "fits"), "any MekaSuit piece");
        assertEquals(List.of("mekanized:meka_tool"), ShippedItems.option("module_vein_mining_unit", "mekanized:module", "fits"));
        assertEquals(List.of("mekanized:mekasuit_helmet"), ShippedItems.option("module_geiger_unit", "mekanized:module", "fits"));
    }

    @Test
    void storedEntriesReadOldAndNewGear() {
        List<GearRules.Installed> modules = GearRules.parse(List.of("vein_mining_unit", "energy_unit:3", "fortune_unit:2:off",
                "", "broken:x", "energy_unit:5"));
        assertEquals(List.of(new GearRules.Installed("vein_mining_unit", 1, true), new GearRules.Installed("energy_unit", 3, true),
                new GearRules.Installed("fortune_unit", 2, false), new GearRules.Installed("broken", 1, true)), modules,
                "a bare name is level 1 and on (gear from before levels); a broken level is 1; a name stored twice counts once");
        assertEquals(List.of("vein_mining_unit:1", "energy_unit:3", "fortune_unit:2:off", "broken:1"), GearRules.format(modules));
        assertEquals(3, GearRules.level(modules, "energy_unit"));
        assertEquals(0, GearRules.level(modules, "fortune_unit"), "off does nothing");
        assertEquals(2, GearRules.installedLevel(modules, "fortune_unit"));
        assertEquals(Set.of("vein_mining_unit", "energy_unit", "broken"), GearRules.enabled(modules));
    }

    @Test
    void levelsGoUpToTheMaxAndComeOutOneByOne() {
        List<GearRules.Installed> modules = GearRules.install(List.of(), "energy_unit", 2);
        modules = GearRules.install(modules, "energy_unit", 2);
        assertEquals(2, GearRules.installedLevel(modules, "energy_unit"));
        assertNull(GearRules.install(modules, "energy_unit", 2), "at its max");
        modules = GearRules.install(modules, "geiger_unit", 1);
        assertEquals(List.of("energy_unit:2", "geiger_unit:1"), GearRules.format(modules), "new modules go last");
        assertEquals(List.of("energy_unit:1", "geiger_unit:1"), GearRules.format(GearRules.remove(modules, "energy_unit", 1)));
        assertEquals(List.of("geiger_unit:1"), GearRules.format(GearRules.remove(modules, "energy_unit", 9)), "all levels out: gone");
        assertEquals(modules, GearRules.remove(modules, "jetpack_unit", 1), "not installed: unchanged");
        List<GearRules.Installed> off = GearRules.toggle(modules, "geiger_unit");
        assertEquals(List.of("energy_unit:2", "geiger_unit:1:off"), GearRules.format(off));
        assertEquals(modules, GearRules.toggle(off, "geiger_unit"), "and back on");
        assertEquals(List.of("energy_unit:3", "geiger_unit:1:off"), GearRules.format(GearRules.install(off, "energy_unit", 8)),
                "a module keeps its switch when it gains a level");
    }

    @Test
    void levelsScaleNumbers() {
        assertEquals(16, GearRules.byLevel(16, 32, 0, 4));
        assertEquals(20, GearRules.byLevel(16, 32, 1, 4));
        assertEquals(32, GearRules.byLevel(16, 32, 4, 4));
        assertEquals(32, GearRules.byLevel(16, 32, 1, 1), "a one-level module gives it all");
        assertEquals(32, GearRules.byLevel(16, 32, 9, 4), "past the max: the max");
        assertEquals(500, GearRules.energyLost(1_500, 1_000));
        assertEquals(0, GearRules.energyLost(900, 1_000));
    }

    @Test
    void moduleEnchantmentsLeaveTheGearsOwnAlone() {
        // The tool has Fortune I of its own; three fortune units give III, and I comes back when they go.
        var first = GearRules.syncEnchantments(Map.of("fortune", 1), Map.of(), Map.of("fortune", 3));
        assertEquals(Map.of("fortune", 3), first.levels());
        assertEquals(Map.of("fortune", new GearRules.Enchanted(3, 1)), first.recorded());
        var gone = GearRules.syncEnchantments(Map.of("fortune", 3), first.recorded(), Map.of());
        assertEquals(Map.of("fortune", 1), gone.levels());
        assertEquals(Map.of(), gone.recorded());
        // Silk touch on a tool without it: removed when the unit goes.
        var silk = GearRules.syncEnchantments(Map.of(), Map.of(), Map.of("silk_touch", 1));
        assertEquals(0, GearRules.syncEnchantments(Map.of("silk_touch", 1), silk.recorded(), Map.of()).levels().get("silk_touch"));
        // Changed since (an anvil gave V): that is the tool's own now.
        assertEquals(Map.of("fortune", 5), GearRules.syncEnchantments(Map.of("fortune", 5), first.recorded(), Map.of()).levels());
        // A module giving less than the tool has: the tool keeps its own.
        assertEquals(Map.of("fortune", 4), GearRules.syncEnchantments(Map.of("fortune", 4), Map.of(), Map.of("fortune", 2)).levels());
        List<String> stored = GearRules.Enchanted.format(first.recorded());
        assertEquals(List.of("fortune|3|1"), stored);
        assertEquals(first.recorded(), GearRules.Enchanted.parse(stored));
    }

    @Test
    void aCapacityFactorMultipliesTheCapacityOncePerLevel() {
        assertEquals(1_000_000, GearRules.scaled(1_000_000, 2, 0));
        assertEquals(1_000_000, GearRules.scaled(1_000_000, 1, 8));
        assertEquals(8_000_000, GearRules.scaled(1_000_000, 2, 3));
        assertEquals(Long.MAX_VALUE, GearRules.scaled(Long.MAX_VALUE / 2, 4, 2), "saturates");
        assertEquals(0, GearRules.scaled(0, 2, 8), "an item without energy stays without");
    }

    @Test
    void moduleLevelsFollowMekanism() {
        assertEquals(8, ShippedItems.option("module_energy_unit", "mekanized:module", "max-level"));
        assertEquals(2, ShippedItems.option("module_energy_unit", "mekanized:module", "capacity-factor"));
        assertEquals(3, ShippedItems.option("module_fortune_unit", "mekanized:module", "max-level"));
        assertEquals(1, ShippedItems.option("module_fortune_unit", "mekanized:module", "enchantment-level"));
        assertEquals(4, ShippedItems.option("module_vein_mining_unit", "mekanized:module", "max-level"));
    }
}
