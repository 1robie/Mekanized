package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.machine.GearRules;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
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
    void aModuleGoesIntoTheFirstFittingPieceWithoutIt() {
        List<Key> worn = java.util.Arrays.asList(HELMET, null, null, BOOTS, TOOL);
        List<Set<String>> modules = List.of(Set.of("energy_unit"), Set.of(), Set.of(), Set.of(), Set.of());
        assertEquals(3, GearRules.pick(worn, modules::get, Set.of(HELMET, BOOTS), "energy_unit"), "the helmet has it already");
        assertEquals(0, GearRules.pick(worn, modules::get, Set.of(HELMET, BOOTS), "geiger_unit"));
        assertEquals(4, GearRules.pick(worn, modules::get, Set.of(TOOL), "vein_mining_unit"), "the Meka-Tool in the off hand");
        List<Set<String>> all = List.of(Set.of("x"), Set.of(), Set.of(), Set.of("x"), Set.of());
        assertEquals(0, GearRules.pick(worn, all::get, Set.of(HELMET, BOOTS), "x"), "all have it: the first, which says 'already installed'");
        assertEquals(-1, GearRules.pick(worn, modules::get, Set.of(MekanizedContent.key("mekasuit_pants")), "x"), "not worn");
    }

    @Test
    void everyModuleItemSaysWhereItFits() {
        assertEquals(List.of("mekanized:mekasuit_helmet", "mekanized:mekasuit_bodyarmor", "mekanized:mekasuit_pants",
                "mekanized:mekasuit_boots"), ShippedItems.option("module_energy_unit", "mekanized:module", "fits"), "any MekaSuit piece");
        assertEquals(List.of("mekanized:meka_tool"), ShippedItems.option("module_vein_mining_unit", "mekanized:module", "fits"));
        assertEquals(List.of("mekanized:mekasuit_helmet"), ShippedItems.option("module_geiger_unit", "mekanized:module", "fits"));
    }

    @Test
    void theModuleListKeepsInstallOrder() {
        assertEquals(List.of("b_unit", "a_unit"), List.copyOf(GearRules.parseModules("b_unit,a_unit")));
        assertEquals(Set.of(), GearRules.parseModules(null));
        assertEquals(Set.of(), GearRules.parseModules(""));
        assertEquals("b_unit,a_unit", GearRules.formatModules(List.of("b_unit", "a_unit")));
        assertNull(GearRules.formatModules(List.of()), "an empty list is not stored");
    }

    @Test
    void aCapacityFactorMultipliesTheCapacity() {
        assertEquals(1_000_000, GearRules.scaled(1_000_000, 1));
        assertEquals(4_000_000, GearRules.scaled(1_000_000, 4));
        assertEquals(Long.MAX_VALUE, GearRules.scaled(Long.MAX_VALUE / 2, 4), "saturates");
        assertEquals(0, GearRules.scaled(0, 4), "an item without energy stays without");
    }
}
