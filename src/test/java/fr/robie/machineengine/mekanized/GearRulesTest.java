package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.machine.GearRules;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gear capacity and module rules, with no Bukkit item in sight. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class GearRulesTest {
    @Test
    void everyModuleHasAPlaceToGo() {
        assertEquals(31, GearRules.modules().size());
        assertEquals("mekasuit_helmet", GearRules.target("geiger_unit"));
        assertEquals("meka_tool", GearRules.target("vein_mining_unit"));
        assertEquals(GearRules.ANY_MEKASUIT, GearRules.target("energy_unit"));
        assertNull(GearRules.target("not_a_module"), "an unknown module does nothing");
        for (String module : GearRules.modules()) {
            String target = GearRules.target(module);
            assertTrue(target.equals(GearRules.ANY_MEKASUIT) || target.equals("meka_tool") || target.startsWith("mekasuit_"), module + " -> " + target);
        }
    }

    @Test
    void aTargetFitsItsGearOnly() {
        assertTrue(GearRules.fits("mekasuit_helmet", "mekasuit_helmet"));
        assertFalse(GearRules.fits("mekasuit_helmet", "mekasuit_boots"));
        assertFalse(GearRules.fits("meka_tool", null), "nothing in hand");
        assertTrue(GearRules.fits(GearRules.ANY_MEKASUIT, "mekasuit_pants"), "any MekaSuit piece takes an energy unit");
        assertFalse(GearRules.fits(GearRules.ANY_MEKASUIT, "meka_tool"));
        assertFalse(GearRules.fits(GearRules.ANY_MEKASUIT, null));
    }

    @Test
    void anyMekasuitPicksTheFirstPieceWithoutTheModule() {
        List<String> ids = List.of("mekasuit_helmet", "mekasuit_bodyarmor", "mekasuit_pants", "mekasuit_boots");
        List<Set<String>> modules = List.of(Set.of("energy_unit"), Set.of(), Set.of(), Set.of());
        assertEquals(1, GearRules.pickMekasuitPiece(4, ids::get, modules::get, "energy_unit"), "the helmet has it already");
        assertEquals(0, GearRules.pickMekasuitPiece(4, ids::get, modules::get, "geiger_unit"));
        List<Set<String>> all = List.of(Set.of("x"), Set.of("x"), Set.of("x"), Set.of("x"));
        assertEquals(0, GearRules.pickMekasuitPiece(4, ids::get, all::get, "x"), "all have it: the first, which says 'already installed'");
        List<String> none = java.util.Arrays.asList(null, "iron_helmet", null, "meka_tool");
        assertEquals(0, GearRules.pickMekasuitPiece(4, none::get, i -> Set.of(), "x"));
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
    void anEnergyUnitMultipliesTheCapacity() {
        assertEquals(1_000_000, GearRules.energyCapacity(1_000_000, Set.of(), 4));
        assertEquals(1_000_000, GearRules.energyCapacity(1_000_000, Set.of("silk_touch_unit", "fortune_unit"), 4));
        assertEquals(4_000_000, GearRules.energyCapacity(1_000_000, Set.of("silk_touch_unit", "energy_unit"), 4));
        assertEquals(Long.MAX_VALUE, GearRules.energyCapacity(Long.MAX_VALUE / 2, Set.of("energy_unit"), 4), "saturates");
        assertEquals(0, GearRules.energyCapacity(0, Set.of("energy_unit"), 4), "an item without energy stays without");
    }
}
