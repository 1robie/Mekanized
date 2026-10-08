package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.data.DataBinary;
import fr.robie.machineengine.api.data.DataMap;
import fr.robie.machineengine.api.player.MachinePlayer;
import fr.robie.machineengine.core.player.CorePlayerData;
import fr.robie.machineengine.core.player.CorePlayers;
import fr.robie.machineengine.mekanized.machine.Radiation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What Mekanized keeps per player (D37). */
class PlayerDataTest {
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");

    @Test
    void mekanizedRegistersItsKeysAndSetting() {
        CorePlayers players = MekanizedTestKit.engine().players();
        assertSame(MekanizedPlayers.RADIATION_DOSE, players.key(MekanizedPlayers.RADIATION_DOSE.key()));
        assertSame(MekanizedPlayers.RADIATION_HUD, players.setting(MekanizedPlayers.RADIATION_HUD.key()));
        assertEquals("mekanized:radiation_dose", MekanizedPlayers.RADIATION_DOSE.pdcMigration().key());
    }

    @Test
    void theDoseIsSavedAndGoneOnDeathTheFlightMarkerStays() throws Exception {
        CorePlayers players = MekanizedTestKit.engine().players();
        CorePlayerData alex = players.join(ALEX, null, DataMap.EMPTY);
        alex.update(MekanizedPlayers.RADIATION_DOSE, dose -> Radiation.nextDose(dose, 36, 0));
        alex.set(MekanizedPlayers.SUIT_FLIGHT, true);
        alex.set(MekanizedPlayers.LAST_TELEPORT, 42L);

        MachinePlayer back = MekanizedTestKit.engine().players().join(ALEX, null, DataBinary.read(DataBinary.write(players.encode(alex))));
        assertEquals(0.01, back.get(MekanizedPlayers.RADIATION_DOSE), 1e-12);
        assertTrue(back.get(MekanizedPlayers.SUIT_FLIGHT));
        assertEquals(0L, back.get(MekanizedPlayers.LAST_TELEPORT), "a session value");

        players.died(alex);
        assertEquals(0.0, alex.get(MekanizedPlayers.RADIATION_DOSE));
        assertTrue(alex.get(MekanizedPlayers.SUIT_FLIGHT), "the server still lets them fly: Mekanized must remember why");
    }

    @Test
    void theDoseShowsInUnits() {
        assertEquals("10.00 mSv", MekanizedPlayers.RADIATION_DOSE.format(0.01));
    }

    @Test
    void theRadiationHudIsOnUnlessThePlayerTurnsItOff() {
        MachinePlayer alex = MekanizedTestKit.engine().players().join(ALEX, null, DataMap.EMPTY);
        assertTrue(alex.get(MekanizedPlayers.RADIATION_HUD));
        assertTrue(alex.set(MekanizedPlayers.RADIATION_HUD, "off").isOk());
        assertFalse(alex.get(MekanizedPlayers.RADIATION_HUD));
    }
}
