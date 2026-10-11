package fr.robie.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.mekanized.machine.ChunkLoaders;
import fr.robie.mekanized.machine.Frequencies;
import fr.robie.mekanized.machine.Radiation;
import fr.robie.mekanized.machine.SaveLines;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The lines of Mekanized's data files, written and read back without any file. */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class SaveLinesTest {
    private static final WorldPosition AT = WorldPosition.of(Key.key("minecraft", "the_nether"), -12, 70, 345);

    private static String[] split(String line) {
        return line.stripTrailing().split("\\|");
    }

    @Test
    void aPositionIsWorldAndThreeCoordinates() {
        StringBuilder text = new StringBuilder();
        SaveLines.appendPosition(text, AT);
        assertEquals("minecraft:the_nether|-12|70|345", text.toString());
        assertEquals(AT, SaveLines.readPosition(split("x|minecraft:the_nether|-12|70|345"), 1), "read from a later field");
    }

    @Test
    void readingKeepsOnlyLinesWithTheRightFieldCount() {
        List<String> seen = new ArrayList<>();
        SaveLines.read(List.of("a|b|c", "a|b", "a|b|c|d", "", "x|y|z"), 3, parts -> seen.add(String.join(",", parts)));
        assertEquals(List.of("a,b,c", "x,y,z"), seen);
    }

    @Test
    void aBadLineStopsTheReading() {
        List<String> seen = new ArrayList<>();
        assertThrows(NumberFormatException.class, () -> SaveLines.read(List.of("1", "two", "3"), 1, parts -> {
            Integer.parseInt(parts[0]);
            seen.add(parts[0]);
        }));
        assertEquals(List.of("1"), seen);
    }

    @Test
    void chunkLoadersRoundTrip() {
        String line = SaveLines.chunkLoader(AT, 2);
        assertEquals("minecraft:the_nether|-12|70|345|2\n", line);
        SaveLines.readChunkLoader(split(line));
        assertEquals(2, ChunkLoaders.loaders().get(AT));
    }

    @Test
    void radiationSourcesRoundTrip() {
        String line = SaveLines.radiationSource(AT, 0.015);
        assertEquals("minecraft:the_nether|-12|70|345|0.0150000000\n", line);
        SaveLines.readRadiationSource(split(line));
        assertEquals(0.015, Radiation.sources().get(AT), 1e-12);
    }

    @Test
    void teleportersRoundTripAndLoseTheSeparatorInTheirName() {
        String line = SaveLines.teleporter(AT, new Frequencies.Teleporter("al|pha", true));
        assertEquals("alpha|minecraft:the_nether|-12|70|345|true\n", line, "a pipe would split the name");
        SaveLines.readTeleporter(split(line));
        assertEquals(new Frequencies.Teleporter("alpha", true), Frequencies.teleporters().get(AT));
        SaveLines.readTeleporter(split(SaveLines.teleporter(FakeWorld.at(1, 2, 3), new Frequencies.Teleporter("beta", false))));
        assertEquals(new Frequencies.Teleporter("beta", false), Frequencies.teleporters().get(FakeWorld.at(1, 2, 3)));
    }

    @Test
    void entangloporterBuffersRoundTripWithAnyFrequencyName() {
        SaveLines.Stored fluid = new SaveLines.Stored("pipe|name and spaces é", "fluid", false, "minecraft:water", 4_000);
        String line = SaveLines.entangloporter(fluid);
        assertEquals(5, split(line).length, "the name is base64: no separator inside");
        assertEquals(fluid, SaveLines.readEntangloporter(split(line)));
        SaveLines.Stored items = new SaveLines.Stored("alpha", "items", true, "AAEC/w==", 7);
        assertEquals(items, SaveLines.readEntangloporter(split(SaveLines.entangloporter(items))));
    }
}
