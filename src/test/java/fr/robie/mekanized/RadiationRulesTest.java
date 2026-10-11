package fr.robie.mekanized;

import fr.robie.mekanized.machine.Radiation;
import fr.robie.mekanized.machine.RadiationSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

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
    void shieldingPiecesAddUpToAllAtMost() {
        assertEquals(0, Radiation.shielding(List.of()), 1e-15);
        assertEquals(0.25, Radiation.shielding(List.of(0.25)), 1e-15);
        assertEquals(1.0, Radiation.shielding(List.of(0.25, 0.25, 0.25, 0.25)), 1e-15, "four pieces stop everything");
        assertEquals(0.1 + 0.1 + 0.1, Radiation.shielding(List.of(0.1, 0.1, 0.1)), 0, "added up piece by piece");
        assertEquals(1.0, Radiation.shielding(List.of(0.6, 0.6)), 0, "never more than all of it");
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
