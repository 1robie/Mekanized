package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;
import fr.robie.machineengine.test.FakeWorld;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gameplay settings of {@code config.yml}: the defaults are the numbers that were hard-coded before
 * (so nothing changes out of the box), the shipped file says the same, an overridden value is used, and a
 * bad value is clamped or replaced with a warning.
 */
class SettingsTest {
    private final List<String> warnings = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    SettingsTest() {
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                SettingsTest.this.warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @AfterEach
    void reset() {
        TeleporterSettings.reset();
        ChunkLoadingSettings.reset();
        RadiationSettings.reset();
        DigitalMinerSettings.reset();
    }

    private void load(String yaml) {
        MekanizedSettings.load(YamlConfiguration.loadConfiguration(new StringReader(yaml)), this.logger);
    }

    /** The config.yml shipped in the jar. */
    private void loadShipped() throws IOException {
        try (InputStream in = SettingsTest.class.getResourceAsStream("/config.yml")) {
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            MekanizedSettings.load(YamlConfiguration.loadConfiguration(reader), this.logger);
        }
    }

    // -- teleporter -------------------------------------------------------------------------------

    private static final Key OTHER_WORLD = Key.key("test", "elsewhere");

    @Test
    void teleporterDefaultsAreTheOldNumbers() {
        assertEquals(1_000, TeleporterSettings.baseCost);
        assertEquals(10, TeleporterSettings.costPerBlock);
        assertEquals(100_000, TeleporterSettings.otherWorldCost);
        assertEquals(3_000, TeleporterSettings.cooldownMs);
        assertEquals(1_000 + 500, Frequencies.cost(50.0));
        WorldPosition far = new WorldPosition(OTHER_WORLD, FakeWorld.at(0, 64, 0).position());
        assertEquals(100_000, Frequencies.cost(FakeWorld.at(0, 64, 0), far));
    }

    @Test
    void theShippedConfigKeepsTheTeleporterDefaults() throws IOException {
        loadShipped();
        teleporterDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void aTeleporterSettingIsUsed() {
        load("teleporter:\n  base-cost: 200\n  cost-per-block: 3\n  other-world-cost: 7000\n  cooldown-seconds: 0.5\n");
        assertEquals(200 + 30, Frequencies.cost(10.0));
        WorldPosition far = new WorldPosition(OTHER_WORLD, FakeWorld.at(0, 64, 0).position());
        assertEquals(7_000, Frequencies.cost(FakeWorld.at(0, 64, 0), far));
        assertEquals(500, TeleporterSettings.cooldownMs);
        assertTrue(this.warnings.isEmpty());
    }

    @Test
    void badTeleporterValuesAreClampedOrReplaced() {
        load("teleporter:\n  base-cost: -5\n  cost-per-block: lots\n  cooldown-seconds: 999999\n");
        assertEquals(0, TeleporterSettings.baseCost, "negative: the lowest allowed");
        assertEquals(10, TeleporterSettings.costPerBlock, "not a number: the default");
        assertEquals(3_600_000, TeleporterSettings.cooldownMs, "too long: the highest allowed");
        assertEquals(3, this.warnings.size(), this.warnings.toString());
    }

    // -- chunk loading ----------------------------------------------------------------------------

    @Test
    void chunkLoadingDefaultsAreTheOldNumbers() {
        assertEquals(2, ChunkLoadingSettings.maxRadius);
        assertEquals(500, ChunkLoadingSettings.energyPerChunk);
    }

    @Test
    void theShippedConfigKeepsTheChunkLoadingDefaults() throws IOException {
        loadShipped();
        chunkLoadingDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void chunkLoadingSettingsAreReadAndLimited() {
        load("chunk-loading:\n  max-radius: 3\n  energy-per-chunk: 250\n");
        assertEquals(3, ChunkLoadingSettings.maxRadius);
        assertEquals(250, ChunkLoadingSettings.energyPerChunk);
        load("chunk-loading:\n  max-radius: 40\n  energy-per-chunk: -1\n");
        assertEquals(8, ChunkLoadingSettings.maxRadius, "a 17 x 17 area at most");
        assertEquals(0, ChunkLoadingSettings.energyPerChunk);
        assertEquals(2, this.warnings.size(), this.warnings.toString());
    }

    // -- radiation --------------------------------------------------------------------------------

    @Test
    void radiationDefaultsAreTheOldNumbers() {
        assertEquals(100e-9, RadiationSettings.background);
        assertEquals(0.995, RadiationSettings.sourceDecayPerSecond);
        assertEquals(1e-6, RadiationSettings.minSource);
        assertEquals(0.999, RadiationSettings.doseDecayPerSecond);
        assertEquals(0.25, RadiationSettings.shieldingPerPiece);
        assertEquals(1, RadiationSettings.sicknessDose);
        assertEquals(10, RadiationSettings.damageDose);
        assertEquals(100, RadiationSettings.lethalDose);
        assertEquals(512_000, RadiationSettings.barrelCapacity);
        assertEquals(1, RadiationSettings.barrelDecay);
    }

    @Test
    void theShippedConfigKeepsTheRadiationDefaults() throws IOException {
        loadShipped();
        radiationDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void radiationSettingsAreReadAndLimited() {
        load("radiation:\n  lethal-dose: 50\n  barrel-decay-per-second: 4\n  source-decay-per-second: 0.9\n");
        assertEquals(50, RadiationSettings.lethalDose);
        assertEquals(4, RadiationSettings.barrelDecay);
        assertEquals(0.9, RadiationSettings.sourceDecayPerSecond);
        load("radiation:\n  source-decay-per-second: 1.5\n  shielding-per-piece: 0.5\n  barrel-capacity: 0\n  damage-dose: high\n");
        assertEquals(1, RadiationSettings.sourceDecayPerSecond, "a source cannot grow");
        assertEquals(0.25, RadiationSettings.shieldingPerPiece, "four pieces stop everything at most");
        assertEquals(1, RadiationSettings.barrelCapacity);
        assertEquals(10, RadiationSettings.damageDose, "not a number: the default");
        assertEquals(4, this.warnings.size(), this.warnings.toString());
    }

    // -- digital miner ----------------------------------------------------------------------------

    @Test
    void digitalMinerDefaultsAreTheOldNumbers() {
        assertEquals(2_000, DigitalMinerSettings.energyPerBlock);
        assertEquals(10, DigitalMinerSettings.ticksPerBlock);
        assertEquals(2_048, DigitalMinerSettings.scanPerTick);
        assertEquals(32, DigitalMinerSettings.maxRadius);
    }

    @Test
    void theShippedConfigKeepsTheDigitalMinerDefaults() throws IOException {
        loadShipped();
        digitalMinerDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void digitalMinerSettingsAreReadAndLimited() {
        load("digital-miner:\n  energy-per-block: 500\n  ticks-per-block: 4\n  max-radius: 16\n");
        assertEquals(500, DigitalMinerSettings.energyPerBlock);
        assertEquals(4, DigitalMinerSettings.ticksPerBlock);
        assertEquals(16, DigitalMinerSettings.maxRadius);
        load("digital-miner:\n  ticks-per-block: 0\n  max-radius: 5000\n  scan-per-tick: many\n");
        assertEquals(1, DigitalMinerSettings.ticksPerBlock, "at least a tick");
        assertEquals(128, DigitalMinerSettings.maxRadius);
        assertEquals(2_048, DigitalMinerSettings.scanPerTick, "not a number: the default");
        assertEquals(3, this.warnings.size(), this.warnings.toString());
    }
}
