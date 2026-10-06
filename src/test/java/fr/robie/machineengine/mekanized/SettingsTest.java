package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.EnergyItems;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.GearSettings;
import fr.robie.machineengine.mekanized.machine.QioSettings;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gameplay settings of {@code config.yml}: the defaults are the numbers that were hard-coded before
 * (so nothing changes out of the box), the shipped file says the same, an overridden value is used, and a
 * bad value is clamped or replaced with a warning.
 */
@ExtendWith(MekanizedTestKit.ResetStatics.class)
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

    // -- qio --------------------------------------------------------------------------------------

    @Test
    void qioDefaultsAreTheOldNumbers() {
        assertArrayEquals(new long[]{16_000, 128_000, 1_048_000, 16_000_000}, QioSettings.driveItems);
        assertArrayEquals(new long[]{128, 256, 1_024, 8_192}, QioSettings.driveKinds);
        assertEquals(1_024, QioSettings.arrayKinds);
        assertEquals(64, QioSettings.ioRate);
    }

    @Test
    void theShippedConfigKeepsTheQioDefaults() throws IOException {
        loadShipped();
        qioDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void qioSettingsAreReadAndValidated() {
        load("qio:\n  drive-items: [1, 2, 3, 4]\n  importer-exporter-rate: 8\n");
        assertArrayEquals(new long[]{1, 2, 3, 4}, QioSettings.driveItems);
        assertEquals(8, QioSettings.ioRate);
        load("qio:\n  drive-kinds: [1, 2, 3]\n  array-kinds: 0\n  drive-items: [1, 2, 3, -4]\n");
        assertArrayEquals(new long[]{128, 256, 1_024, 8_192}, QioSettings.driveKinds, "three numbers: the defaults");
        assertArrayEquals(new long[]{1, 2, 3, 4}, QioSettings.driveItems, "a negative one: the values before stay");
        assertEquals(1, QioSettings.arrayKinds);
        assertEquals(3, this.warnings.size(), this.warnings.toString());
    }

    // -- gear -------------------------------------------------------------------------------------

    @Test
    void gearDefaultsAreTheOldNumbers() {
        // Capacities: what EnergyItems.CAPACITY and GearItems.CHEMICALS held.
        assertEquals(1_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("energy_tablet")));
        assertEquals(1_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("atomic_disassembler")));
        assertEquals(1_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("portable_teleporter")));
        assertEquals(16_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("meka_tool")));
        assertEquals(120_000L, EnergyItems.baseCapacity(MekanizedContent.key("electric_bow")));
        assertEquals(64_000L, EnergyItems.baseCapacity(MekanizedContent.key("free_runners")));
        assertEquals(64_000L, EnergyItems.baseCapacity(MekanizedContent.key("free_runners_armored")));
        for (String piece : new String[]{"helmet", "bodyarmor", "pants", "boots"}) {
            assertEquals(16_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("mekasuit_" + piece)));
        }
        assertEquals(0L, EnergyItems.baseCapacity(MekanizedContent.key("crusher")), "not an energy item");
        assertEquals(0L, EnergyItems.baseCapacity(Key.key("other", "meka_tool")), "only items of Mekanized");
        assertEquals(64_000_000L, EnergyItems.capacity(MekanizedContent.key("meka_tool"), "silk_touch_unit,energy_unit"), "energy unit: x4");
        assertEquals(16_000_000L, EnergyItems.capacity(MekanizedContent.key("meka_tool"), "silk_touch_unit"));
        for (String item : new String[]{"jetpack", "jetpack_armored", "flamethrower", "scuba_tank", "mekasuit_bodyarmor"}) {
            assertEquals(24_000L, GearSettings.chemicalCapacity(item), item);
        }
        assertEquals(64_000L, GearSettings.chemicalCapacity("canteen"));
        assertEquals(0L, GearSettings.chemicalCapacity("crusher"));
        // Tools.
        assertEquals(100, GearSettings.toolEnergyPerBlock);
        assertEquals(64, GearSettings.veinLimit);
        assertEquals(256, GearSettings.veinLimitWithUnit);
        assertEquals(500, GearSettings.shearEnergy);
        assertEquals(2_000, GearSettings.disassemblerHitEnergy);
        assertEquals(20.0, GearSettings.disassemblerDamage);
        assertEquals(3_000, GearSettings.mekaToolHitEnergy);
        assertEquals(10.0, GearSettings.mekaToolDamage);
        assertEquals(30.0, GearSettings.mekaToolAmplifiedDamage);
        assertEquals(120, GearSettings.bowEnergyPerShot);
        assertEquals(100, GearSettings.teleportRange);
        assertEquals(5, GearSettings.flamethrowerHydrogen);
        assertEquals(2.0, GearSettings.flamethrowerDamage);
        // Jetpack and scuba.
        assertEquals(1, GearSettings.jetpackHydrogenPerTick);
        assertEquals(0.15, GearSettings.jetpackThrust);
        assertEquals(0.5, GearSettings.jetpackMaxSpeed);
        assertEquals(0.3, GearSettings.jetpackWalkSpeed);
        assertEquals(0.45, GearSettings.jetpackSprintSpeed);
        assertEquals(20, GearSettings.scubaOxygen);
        // Modules.
        assertEquals(50, GearSettings.fallEnergyPerDamage);
        assertEquals(20, GearSettings.magnetEnergyPerItem);
        assertEquals(8, GearSettings.magnetRange);
        assertEquals(2_000, GearSettings.breathingEnergy);
        assertEquals(200, GearSettings.visionEnergy);
        assertEquals(2_500, GearSettings.nutritionEnergy);
        assertEquals(2_000, GearSettings.purificationEnergy);
        assertEquals(20_000, GearSettings.flightEnergy);
        assertEquals(2_000, GearSettings.locomotiveEnergy);
        assertEquals(200, GearSettings.hydraulicEnergy);
        assertEquals(500, GearSettings.repulsorEnergy);
        assertEquals(200, GearSettings.gyroscopicEnergy);
        assertEquals(100, GearSettings.servoMinEnergy);
        assertEquals(2_000, GearSettings.solarEnergy);
        assertEquals(10_000, GearSettings.geothermalEnergy);
        assertEquals(20_000, GearSettings.distributionEnergy);
    }

    @Test
    void theShippedConfigKeepsTheGearDefaults() throws IOException {
        loadShipped();
        gearDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void gearCapacitiesAndCostsAreRead() {
        load("gear:\n  energy-capacity:\n    meka-tool: 5000\n    mekasuit: 7000\n    free-runners: 900\n"
                + "  energy-unit-factor: 2\n  chemical-capacity:\n    jetpack: 100\n  tools:\n    vein-limit: 10\n"
                + "    meka-tool-amplified-damage: 44.5\n  modules:\n    solar-recharging-energy: 1\n");
        assertEquals(5_000L, EnergyItems.baseCapacity(MekanizedContent.key("meka_tool")));
        assertEquals(10_000L, EnergyItems.capacity(MekanizedContent.key("meka_tool"), "energy_unit"), "the configured factor");
        assertEquals(7_000L, EnergyItems.baseCapacity(MekanizedContent.key("mekasuit_boots")), "a group sets all its pieces");
        assertEquals(900L, EnergyItems.baseCapacity(MekanizedContent.key("free_runners_armored")));
        assertEquals(1_000_000L, EnergyItems.baseCapacity(MekanizedContent.key("energy_tablet")), "untouched");
        assertEquals(100L, GearSettings.chemicalCapacity("mekasuit_bodyarmor"));
        assertEquals(24_000L, GearSettings.chemicalCapacity("flamethrower"), "untouched");
        assertEquals(10, GearSettings.veinLimit);
        assertEquals(44.5, GearSettings.mekaToolAmplifiedDamage);
        assertEquals(1, GearSettings.solarEnergy);
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void badGearValuesAreClampedOrReplaced() {
        load("gear:\n  energy-capacity:\n    meka-tool: 0\n    electric-bow: lots\n  tools:\n    teleport-range: 5000\n"
                + "    vein-limit: 0\n    bow-energy-per-shot: -3\n  jetpack:\n    thrust: fast\n    max-speed: 99\n");
        assertEquals(1L, EnergyItems.baseCapacity(MekanizedContent.key("meka_tool")), "at least 1 J");
        assertEquals(120_000L, EnergyItems.baseCapacity(MekanizedContent.key("electric_bow")), "not a number: the default");
        assertEquals(256, GearSettings.teleportRange);
        assertEquals(1, GearSettings.veinLimit);
        assertEquals(0, GearSettings.bowEnergyPerShot);
        assertEquals(0.15, GearSettings.jetpackThrust);
        assertEquals(2.0, GearSettings.jetpackMaxSpeed);
        assertEquals(7, this.warnings.size(), this.warnings.toString());
    }
}
