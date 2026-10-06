package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dose, shielding and thresholds of radiation: what the player side asks, with no player. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class RadiationRulesTest {
    @Test
    void aDoseFadesAndTakesInWhatIsNotShielded() {
        assertEquals(0.0, Radiation.nextDose(0, 0, 0), 1e-15);
        assertEquals(1.0 * 0.999, Radiation.nextDose(1.0, 0, 0), 1e-12, "no radiation: only the fade");
        assertEquals(3600.0 / 3600, Radiation.nextDose(0, 3600, 0), 1e-12, "a level is Sv/h: 3600 Sv/h is 1 Sv per second");
        assertEquals(0.5, Radiation.nextDose(0, 3600, 0.5), 1e-12, "half of it shielded");
        assertEquals(0.0, Radiation.nextDose(0, 3600, 1.0), 1e-12, "all of it shielded");
        RadiationSettings.doseDecayPerSecond = 0.5;
        assertEquals(0.5, Radiation.nextDose(1.0, 0, 0), 1e-12, "the configured fade");
    }

    @Test
    void eachShieldingPieceStopsAQuarterByDefault() {
        assertEquals(0, Radiation.shielding(0), 1e-15);
        assertEquals(0.25, Radiation.shielding(1), 1e-15);
        assertEquals(1.0, Radiation.shielding(4), 1e-15, "four pieces stop everything");
        RadiationSettings.shieldingPerPiece = 0.1;
        assertEquals(0.1 + 0.1 + 0.1, Radiation.shielding(3), 0, "added up piece by piece");
    }

    @Test
    void hazmatAndShieldedMekaSuitPiecesShield() {
        Set<String> none = Set.of();
        Set<String> shielded = Set.of("radiation_shielding_unit");
        assertTrue(Radiation.shields(0, "hazmat_mask", () -> none));
        assertTrue(Radiation.shields(3, "hazmat_boots", () -> none));
        assertFalse(Radiation.shields(0, "hazmat_boots", () -> none), "boots on the head do not count");
        assertFalse(Radiation.shields(1, "mekasuit_bodyarmor", () -> none), "a MekaSuit piece needs the unit");
        assertTrue(Radiation.shields(1, "mekasuit_bodyarmor", () -> shielded));
        assertFalse(Radiation.shields(2, "mekasuit_bodyarmor", () -> shielded), "wrong slot");
        assertFalse(Radiation.shields(0, null, () -> shielded), "nothing worn");
        assertFalse(Radiation.shields(0, "iron_helmet", () -> shielded));
    }

    @Test
    void doseThresholdsFollowTheSettings() {
        assertFalse(Radiation.causesSickness(0.99));
        assertTrue(Radiation.causesSickness(1));
        assertFalse(Radiation.causesDamage(9.99));
        assertTrue(Radiation.causesDamage(10));
        assertFalse(Radiation.isLethal(99.9));
        assertTrue(Radiation.isLethal(100));
        RadiationSettings.lethalDose = 5;
        assertTrue(Radiation.isLethal(5));
    }

    @Test
    void severityIsGreenYellowRed() {
        assertEquals(Radiation.Severity.LOW, Radiation.severity(0.099));
        assertEquals(Radiation.Severity.MEDIUM, Radiation.severity(0.1));
        assertEquals(Radiation.Severity.MEDIUM, Radiation.severity(0.99));
        assertEquals(Radiation.Severity.HIGH, Radiation.severity(1));
    }

    @Test
    void levelsAreWrittenInTheUnitThatKeepsThemReadable() {
        assertEquals("1.50 Sv/h", Radiation.format(1.5, "/h"));
        assertEquals("250.00 mSv", Radiation.format(0.25, ""));
        assertEquals("100.00 nSv/h", Radiation.format(100e-9, "/h"), "the background level");
    }
}
