package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.ChunkLoadingSettings;
import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.machine.Frequencies;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.mekanized.machine.QioSettings;
import fr.robie.machineengine.mekanized.machine.RadiationSettings;
import fr.robie.machineengine.mekanized.machine.TeleporterSettings;
import fr.robie.machineengine.test.FakeWorld;
import fr.robie.machineengine.api.config.ConfigFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

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
    @TempDir
    Path folder;
    private Path file;

    @BeforeEach
    void setUpFile() {
        this.file = this.folder.resolve("config.yml");
    }

    SettingsTest() {
        this.logger.setUseParentHandlers(false);
        this.logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) SettingsTest.this.warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    /** Loads {@code yaml} as the server's config.yml, as the plugin does (missing keys are added to it). */
    private void load(String yaml) {
        try {
            Files.writeString(this.file, yaml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        MekanizedSettings.apply(ConfigFile.load(this.file, MekanizedConfig.SPEC, this.logger));
    }

    /** The config.yml a new server gets, generated from {@link MekanizedConfig#SPEC}. */
    private void loadShipped() throws IOException {
        Files.deleteIfExists(this.file);
        MekanizedSettings.apply(ConfigFile.load(this.file, MekanizedConfig.SPEC, this.logger));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(SettingsTest.class.getResourceAsStream(name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    // -- the file ---------------------------------------------------------------------------------

    /**
     * The config.yml a new server gets. A change to {@link MekanizedConfig} changes it: update
     * {@code config/mekanized-config.yml} in the test resources so the change shows up in review.
     */
    @Test
    void aNewServerGetsTheExpectedFile() throws IOException {
        loadShipped();
        assertEquals(resource("/config/mekanized-config.yml"), Files.readString(this.file, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    /** The config.yml Mekanized shipped before it was generated: every setting there, so it loads as it is. */
    @Test
    void theOldShippedFileLoadsUnchangedWithItsValues() throws IOException {
        String old = resource("/config/shipped-config-0.1.yml");
        load(old);
        assertEquals(old, Files.readString(this.file, StandardCharsets.UTF_8).replace("\r\n", "\n"), "nothing missing: not rewritten");
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
        gearDefaultsAreTheOldNumbersInTheItemConfig();
        qioDefaultsAreTheOldNumbers();
        // Its transport.* rates are kept in the file and ignored: the block configs hold them now (D34).
        assertEquals(8_000, MekanizedRegistry.CABLES.getFirst().ratePerTick());
    }

    @Test
    void aMissingSettingIsAddedToTheFile() throws IOException {
        load("teleporter:\n  base-cost: 200\n");
        String text = Files.readString(this.file, StandardCharsets.UTF_8);
        assertTrue(text.contains("base-cost: 200"), text);
        assertTrue(text.contains("cost-per-block: 10"), text);
        assertTrue(text.contains("# J of a teleport to another world"), text);
        assertEquals(200, TeleporterSettings.baseCost);
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
    }

    @Test
    void theShippedConfigKeepsTheChunkLoadingDefaults() throws IOException {
        loadShipped();
        chunkLoadingDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void chunkLoadingSettingsAreReadAndLimited() {
        load("chunk-loading:\n  max-radius: 3\n");
        assertEquals(3, ChunkLoadingSettings.maxRadius);
        load("chunk-loading:\n  max-radius: 40\n");
        assertEquals(8, ChunkLoadingSettings.maxRadius, "a 17 x 17 area at most");
        assertEquals(1, this.warnings.size(), this.warnings.toString());
    }

    // -- radiation --------------------------------------------------------------------------------

    @Test
    void radiationDefaultsAreTheOldNumbers() {
        assertEquals(100e-9, RadiationSettings.background);
        assertEquals(0.995, RadiationSettings.sourceDecayPerSecond);
        assertEquals(1e-6, RadiationSettings.minSource);
        assertEquals(0.999, RadiationSettings.doseDecayPerSecond);
        assertEquals(1, RadiationSettings.sicknessDose);
        assertEquals(10, RadiationSettings.damageDose);
        assertEquals(100, RadiationSettings.lethalDose);
    }

    @Test
    void theShippedConfigKeepsTheRadiationDefaults() throws IOException {
        loadShipped();
        radiationDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void radiationSettingsAreReadAndLimited() {
        load("radiation:\n  lethal-dose: 50\n  source-decay-per-second: 0.9\n");
        assertEquals(50, RadiationSettings.lethalDose);
        assertEquals(0.9, RadiationSettings.sourceDecayPerSecond);
        load("radiation:\n  source-decay-per-second: 1.5\n  damage-dose: high\n");
        assertEquals(1, RadiationSettings.sourceDecayPerSecond, "a source cannot grow");
        assertEquals(10, RadiationSettings.damageDose, "not a number: the default");
        assertEquals(2, this.warnings.size(), this.warnings.toString());
    }

    // -- digital miner ----------------------------------------------------------------------------

    @Test
    void digitalMinerDefaultsAreTheOldNumbers() {
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
        load("digital-miner:\n  max-radius: 16\n");
        assertEquals(16, DigitalMinerSettings.maxRadius);
        load("digital-miner:\n  max-radius: 5000\n  scan-per-tick: many\n");
        assertEquals(128, DigitalMinerSettings.maxRadius);
        assertEquals(2_048, DigitalMinerSettings.scanPerTick, "not a number: the default");
        assertEquals(2, this.warnings.size(), this.warnings.toString());
    }

    // -- qio --------------------------------------------------------------------------------------

    @Test
    void qioDefaultsAreTheOldNumbers() {
        assertEquals(1_024, QioSettings.arrayKinds);
    }

    @Test
    void theShippedConfigKeepsTheQioDefaults() throws IOException {
        loadShipped();
        qioDefaultsAreTheOldNumbers();
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }

    @Test
    void qioSettingsAreReadAndValidated() {
        load("qio:\n  array-kinds: 64\n");
        assertEquals(64, QioSettings.arrayKinds);
        load("qio:\n  array-kinds: 0\n");
        assertEquals(1, QioSettings.arrayKinds);
        assertEquals(1, this.warnings.size(), this.warnings.toString());
    }

    // -- gear -------------------------------------------------------------------------------------

    @Test
    void gearDefaultsAreTheOldNumbersInTheItemConfig() {
        // Capacities and every gear number: in the item configs now (D32), still what config.yml held.
        String energy = "machineengine:energy";
        assertEquals(1_000_000L, ShippedItems.capacity("energy_tablet", energy));
        assertEquals(1_000_000L, ShippedItems.capacity("atomic_disassembler", energy));
        assertEquals(1_000_000L, ShippedItems.capacity("portable_teleporter", energy));
        assertEquals(16_000_000L, ShippedItems.capacity("meka_tool", energy));
        assertEquals(120_000L, ShippedItems.capacity("electric_bow", energy));
        assertEquals(64_000L, ShippedItems.capacity("free_runners", energy));
        assertEquals(64_000L, ShippedItems.capacity("free_runners_armored", energy));
        for (String piece : new String[]{"helmet", "bodyarmor", "pants", "boots"}) {
            assertEquals(16_000_000L, ShippedItems.capacity("mekasuit_" + piece, energy));
            assertEquals("radiation_shielding_unit", ShippedItems.option("mekasuit_" + piece, "mekanized:radiation_shielding", "requires-module"));
        }
        assertEquals(0L, ShippedItems.capacity("crusher", energy), "not an energy item");
        for (String item : new String[]{"jetpack", "jetpack_armored", "flamethrower", "mekasuit_bodyarmor"}) {
            assertEquals(24_000L, ShippedItems.capacity(item, "mekanized:hydrogen"), item);
        }
        assertEquals(24_000L, ShippedItems.capacity("scuba_tank", "mekanized:oxygen"));
        assertEquals(64_000L, ShippedItems.capacity("canteen", "mekanized:fluid/nutritional_paste"));
        // Tools.
        String mining = "mekanized:mining_tool";
        for (String tool : new String[]{"atomic_disassembler", "meka_tool"}) {
            assertEquals(100, ShippedItems.option(tool, mining, "energy-per-block"), tool);
            assertEquals(64, ShippedItems.option(tool, mining, "vein-limit"), tool);
        }
        assertEquals(256, ShippedItems.option("meka_tool", mining, "vein-limit-with-unit"));
        assertEquals(2_000, ShippedItems.option("atomic_disassembler", mining, "hit-energy"));
        assertEquals(20.0, ShippedItems.option("atomic_disassembler", mining, "damage"));
        assertEquals(3_000, ShippedItems.option("meka_tool", mining, "hit-energy"));
        assertEquals(10.0, ShippedItems.option("meka_tool", mining, "damage"));
        assertEquals(30.0, ShippedItems.option("meka_tool", mining, "amplified-damage"));
        assertEquals(500, ShippedItems.option("meka_tool", "mekanized:meka_tool", "shear-energy"));
        assertEquals(100, ShippedItems.option("meka_tool", "mekanized:meka_tool", "teleport-range"));
        assertEquals(120, ShippedItems.option("electric_bow", "mekanized:electric_bow", "energy-per-shot"));
        assertEquals(5, ShippedItems.option("flamethrower", "mekanized:flamethrower", "hydrogen-per-use"));
        assertEquals(2.0, ShippedItems.option("flamethrower", "mekanized:flamethrower", "damage"));
        // Jetpack and breathing.
        for (String pack : new String[]{"jetpack", "jetpack_armored", "mekasuit_bodyarmor"}) {
            assertEquals(1, ShippedItems.option(pack, "mekanized:jetpack", "hydrogen-per-tick"), pack);
            assertEquals(0.15, ShippedItems.option(pack, "mekanized:jetpack", "thrust"), pack);
            assertEquals(0.5, ShippedItems.option(pack, "mekanized:jetpack", "max-speed"), pack);
            assertEquals(0.3, ShippedItems.option(pack, "mekanized:jetpack", "walk-speed"), pack);
            assertEquals(0.45, ShippedItems.option(pack, "mekanized:jetpack", "sprint-speed"), pack);
        }
        assertEquals("jetpack_unit", ShippedItems.option("mekasuit_bodyarmor", "mekanized:jetpack", "requires-module"));
        assertEquals(20, ShippedItems.option("scuba_tank", "mekanized:scuba_tank", "oxygen-per-refill"));
        for (String boots : new String[]{"free_runners", "free_runners_armored", "mekasuit_boots"}) {
            assertEquals(50, ShippedItems.option(boots, "mekanized:fall_absorber", "energy-per-damage"), boots);
        }
        for (String hazmat : new String[]{"hazmat_mask", "hazmat_gown", "hazmat_pants", "hazmat_boots"}) {
            assertEquals(0.25, ShippedItems.option(hazmat, "mekanized:radiation_shielding", "protection"), hazmat);
        }
        // Modules.
        Map<String, Integer> moduleEnergy = Map.ofEntries(Map.entry("magnetic_attraction_unit", 20),
                Map.entry("electrolytic_breathing_unit", 2_000), Map.entry("vision_enhancement_unit", 200),
                Map.entry("nutritional_injection_unit", 2_500), Map.entry("inhalation_purification_unit", 2_000),
                Map.entry("gravitational_modulating_unit", 20_000), Map.entry("locomotive_boosting_unit", 2_000),
                Map.entry("hydraulic_propulsion_unit", 200), Map.entry("hydrostatic_repulsor_unit", 500),
                Map.entry("gyroscopic_stabilization_unit", 200), Map.entry("motorized_servo_unit", 100),
                Map.entry("solar_recharging_unit", 2_000), Map.entry("geothermal_generator_unit", 10_000),
                Map.entry("charge_distribution_unit", 20_000));
        moduleEnergy.forEach((module, joules) ->
                assertEquals(joules, ShippedItems.option("module_" + module, "mekanized:module", "energy"), module));
        // Per level since D40 (Mekanism): 4 blocks a level (up to 4), x2 a level (up to 8), Fortune I a level (up to 3).
        assertEquals(4, ShippedItems.option("module_magnetic_attraction_unit", "mekanized:module", "range"));
        assertEquals(2, ShippedItems.option("module_energy_unit", "mekanized:module", "capacity-factor"), "energy unit: x2 a level");
        assertEquals(true, ShippedItems.option("module_elytra_unit", "mekanized:module", "glider"));
        assertEquals("minecraft:fortune", ShippedItems.option("module_fortune_unit", "mekanized:module", "enchantment"));
        assertEquals(1, ShippedItems.option("module_fortune_unit", "mekanized:module", "enchantment-level"));
        // Effects that were Java numbers, now in the item config (D40): the same values.
        assertEquals(400, ShippedItems.option("module_vision_enhancement_unit", "mekanized:module", "effect-duration"));
        assertEquals(1, ShippedItems.option("module_locomotive_boosting_unit", "mekanized:module", "effect-amplifier"));
        assertEquals(true, ShippedItems.option("module_locomotive_boosting_unit", "mekanized:module", "energy-per-level"));
        assertEquals(1, ShippedItems.option("module_hydraulic_propulsion_unit", "mekanized:module", "effect-amplifier"));
        assertEquals(2, ShippedItems.option("module_gyroscopic_stabilization_unit", "mekanized:module", "effect-amplifier"));
        assertEquals(true, ShippedItems.option("module_solar_recharging_unit", "mekanized:module", "energy-per-level"));
        assertEquals(0.6, ShippedItems.option("module_magnetic_attraction_unit", "mekanized:module", "pull-speed"));
        assertEquals(1.1, ShippedItems.option("module_motorized_servo_unit", "mekanized:module", "step-height"));
        assertEquals(9, ((List<?>) ShippedItems.option("module_inhalation_purification_unit", "mekanized:module", "removes-effects")).size());
    }

    @Test
    void oldGearSettingsAreIgnored() {
        load("gear:\n  energy-unit-factor: 2\n  tools:\n    vein-limit: 10\n");
        assertTrue(this.warnings.isEmpty(), this.warnings.toString());
    }
}
