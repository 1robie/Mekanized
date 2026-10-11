package fr.robie.mekanized;

import fr.robie.machineengine.api.resource.ResourceVariant;
import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.mekanized.machine.Frequencies;
import fr.robie.mekanized.machine.TeleporterSettings;
import fr.robie.machineengine.test.FakeWorld;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static fr.robie.mekanized.MekanizedTestKit.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Teleport cost and what a frequency's drive arrays hold together (the QIO dashboard's numbers). */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
class FrequenciesTest {
    private static final ResourceVariant DIRT = item("minecraft:dirt");
    private static final ResourceVariant STONE = item("minecraft:stone");
    private static final ResourceVariant IRON = item("minecraft:iron_ingot");

    @Test
    void aTeleportCostsABasePlusEachBlock() {
        WorldPosition a = FakeWorld.at(0, 64, 0);
        assertEquals(1_000, Frequencies.cost(a, a), "no distance: just the base");
        assertEquals(1_000 + 3 * 10, Frequencies.cost(a, FakeWorld.at(3, 64, 0)));
        assertEquals(1_000 + 5 * 10, Frequencies.cost(a, FakeWorld.at(3, 68, 0)), "3-4-5: the distance is straight, in three dimensions");
        assertEquals(Frequencies.cost(a, FakeWorld.at(3, 64, 4)), Frequencies.cost(FakeWorld.at(3, 64, 4), a), "the same both ways");
        assertEquals(1_000 + 1_507, Frequencies.cost(150.7), "10 J a block, cut to whole joules");
    }

    @Test
    void anotherWorldHasItsOwnCostAndTheSettingsApply() {
        WorldPosition a = FakeWorld.at(0, 64, 0);
        WorldPosition elsewhere = new WorldPosition(Key.key("test", "elsewhere"), a.position());
        assertEquals(100_000, Frequencies.cost(a, elsewhere), "the same coordinates, another world");
        TeleporterSettings.baseCost = 50;
        TeleporterSettings.costPerBlock = 2;
        TeleporterSettings.otherWorldCost = 7;
        assertEquals(50 + 20, Frequencies.cost(a, FakeWorld.at(10, 64, 0)));
        assertEquals(7, Frequencies.cost(a, elsewhere));
    }

    @Test
    void theDestinationIsAnotherFormedTeleporterOfTheFrequency() {
        WorldPosition a = FakeWorld.at(0, 64, 0), b = FakeWorld.at(10, 64, 0), c = FakeWorld.at(20, 64, 0);
        Frequencies.teleporters().put(a, new Frequencies.Teleporter("alpha", true));
        assertNull(Frequencies.destination(a, "alpha"), "no other one");
        Frequencies.teleporters().put(b, new Frequencies.Teleporter("alpha", false));
        assertNull(Frequencies.destination(a, "alpha"), "its frame is not complete");
        Frequencies.teleporters().put(c, new Frequencies.Teleporter("beta", true));
        assertNull(Frequencies.destination(a, "alpha"), "another frequency");
        Frequencies.teleporters().put(b, new Frequencies.Teleporter("alpha", true));
        assertEquals(b, Frequencies.destination(a, "alpha"));
    }

    @Test
    void theDashboardAddsUpTheArraysOfOneFrequency() {
        Frequencies.arrays().put(FakeWorld.at(0, 64, 0), new Frequencies.Array("alpha", Map.of(DIRT, 100L, STONE, 5L), 16_000, 105));
        Frequencies.arrays().put(FakeWorld.at(2, 64, 0), new Frequencies.Array("alpha", Map.of(DIRT, 50L, IRON, 300L), 16_000, 350));
        Frequencies.arrays().put(FakeWorld.at(4, 64, 0), new Frequencies.Array("beta", Map.of(STONE, 9_999L), 16_000, 9_999));

        Frequencies.Summary summary = Frequencies.summary("alpha");
        assertEquals(455, summary.stored());
        assertEquals(32_000, summary.capacity());
        assertEquals(List.of(Map.entry(IRON, 300L), Map.entry(DIRT, 150L), Map.entry(STONE, 5L)), summary.sorted(),
                "merged, most first");

        Frequencies.Summary none = Frequencies.summary("gamma");
        assertEquals(List.of(), none.sorted());
        assertEquals(0, none.stored());
    }

    @Test
    void theDashboardShowsAPageAtATime() {
        Map<ResourceVariant, Long> many = new java.util.HashMap<>();
        for (int i = 0; i < 100; i++) many.put(item("minecraft:item_" + i), 1_000L - i);
        Frequencies.arrays().put(FakeWorld.at(0, 64, 0), new Frequencies.Array("alpha", many, 1_000_000, 90_000));
        Frequencies.Summary summary = Frequencies.summary("alpha");
        assertEquals(45, summary.page(0, 45).size());
        assertEquals(45, summary.page(1, 45).size());
        assertEquals(10, summary.page(2, 45).size(), "the rest");
        assertEquals(List.of(), summary.page(3, 45), "past the end");
        assertEquals(item("minecraft:item_0"), summary.page(0, 45).getFirst().getKey(), "the most first");
        assertEquals(item("minecraft:item_45"), summary.page(1, 45).getFirst().getKey());
    }
}
