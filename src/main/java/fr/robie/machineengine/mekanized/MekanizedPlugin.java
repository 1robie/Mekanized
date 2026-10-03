package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.addon.AddonRegistration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Mekanized: Mekanism-style machines on MachineEngine (docs/MEKANIZED_PLAN.md). Inspired by
 * Mekanism; textures, models and recipes come from Mekanism by Aidan C. Brady under the MIT license
 * (LICENSE-MEKANISM.txt, shipped in the jar and in the CraftEngine pack). Not affiliated with Mekanism.
 *
 * <p>Registers its content in {@code onLoad} (D13) and installs its CraftEngine pack before
 * CraftEngine loads packs.</p>
 */
public final class MekanizedPlugin extends JavaPlugin {
    /** Inside the jar: what the Gradle import generated, under {@code mekanized/}. */
    private static final String GENERATED = "mekanized/";
    /**
     * Generated CraftEngine configuration files installed so far. Each plan phase adds the files whose
     * items and blocks exist; the others stay in the jar (they would only produce warnings).
     */
    private static final java.util.Set<String> ENABLED_CONFIGURATION = java.util.Set.of(
            // Phase 1: materials, ores, storage blocks, their names and recipes.
            "configuration/materials.yml", "configuration/lang.yml", "configuration/recipes_materials_shaped.yml",
            "configuration/recipes_materials_shapeless.yml", "configuration/recipes_materials_cooking.yml",
            // Phase 2: machines, generators, energy cubes, cables, upgrades and their crafting recipes.
            "configuration/machines.yml", "configuration/recipes_machines_shaped.yml",
            // Phase 3: chemical machines, tanks, tubes, pipes, the pump and their crafting recipes.
            "configuration/chemistry.yml", "configuration/recipes_chemistry_shaped.yml",
            // Phase 4: transporters, the logistical sorter, factories, tier installers, the configurator.
            "configuration/logistics.yml", "configuration/recipes_logistics_shaped.yml",
            // Phase 5a: the Dynamic Tank and the Induction Matrix.
            "configuration/multiblocks.yml", "configuration/recipes_multiblocks_shaped.yml",
            // Phase 6: the remaining single-block machines and generators.
            "configuration/endgame.yml", "configuration/recipes_endgame_shaped.yml");
    /** Generated MachineEngine definitions installed so far (same rule as {@link #ENABLED_CONFIGURATION}). */
    static final java.util.Set<String> ENABLED_DEFINITIONS = java.util.Set.of(
            // Phase 1: item and chemical tags, and the vanilla members of c: tags.
            "tags.yml", "vanilla_aliases.yml",
            // Phase 2: recipes of the basic machines, and the item -> energy / chemical conversions.
            "recipes_enriching.yml", "recipes_crushing.yml", "recipes_combining.yml", "recipes_sawing.yml",
            "recipes_metallurgic_infusing.yml", "recipes_compressing.yml", "recipes_purifying.yml",
            "recipes_chemical_conversion.yml", "recipes_energy_conversion.yml",
            // Phase 3: chemical machines (4x and 5x ore processing), the rotary condensentrator, bucket slots.
            "recipes_oxidizing.yml", "recipes_chemical_infusing.yml", "recipes_injecting.yml", "recipes_dissolution.yml",
            "recipes_washing.yml", "recipes_crystallizing.yml", "recipes_separating.yml", "recipes_condensing.yml",
            "recipes_decondensing.yml", "recipes_buckets.yml",
            // Phase 5a: the Thermal Evaporation Plant.
            "recipes_evaporating.yml",
            // Phase 6: reactions, late-game chemistry, painting.
            "recipes_reaction.yml", "recipes_centrifuging.yml", "recipes_activating.yml", "recipes_nucleosynthesizing.yml",
            "recipes_painting.yml", "recipes_pigment_extracting.yml", "recipes_pigment_mixing.yml");

    @Override
    public void onLoad() {
        // Every class now: if the jar is replaced while the server runs, nothing breaks later.
        fr.robie.machineengine.paper.ClassPreloader.preload(MekanizedPlugin.class, "fr/robie/machineengine/mekanized/");
        // Before anything builds the machines and transports from them.
        saveDefaultConfig();
        MekanizedSettings.load(getConfig(), getLogger());
        MachineEngine.register(AddonRegistration.builder(getName(), getPluginMeta().getVersion())
                .requiresApi("[0.1,1.0)")
                .register(MekanizedContent::register)
                .build());
        // Editable generated files are updated only while nobody changed them (EditableFiles).
        try (EditableFiles files = new EditableFiles(this)) {
            installCraftEnginePack(files);
            installDefinitions(files);
            MekanizedMenus.install(this, files);
        } catch (IOException e) {
            getLogger().warning("Could not record the installed Mekanized files: " + e.getMessage());
        }
    }

    /**
     * Copies the enabled generated definitions to {@code plugins/MachineEngine/definitions/mekanized/}:
     * server owners edit them there; edited files are never overwritten (delete one to get the default).
     */
    private void installDefinitions(EditableFiles files) {
        Path target = getDataFolder().toPath().getParent().resolve("MachineEngine/definitions/mekanized");
        for (String file : ENABLED_DEFINITIONS) {
            try (InputStream in = getResource(GENERATED + "definitions/" + file)) {
                if (in == null) throw new IOException("missing resource " + file);
                files.install(target.resolve(file), in.readAllBytes());
            } catch (IOException e) {
                getLogger().severe("Could not install the definitions file " + file + ": " + e.getMessage());
            }
        }
    }

    /** Menu names of Mekanized resources, translated on each player's client (Mekanism's language files). */
    private static String name(fr.robie.machineengine.api.resource.ResourceVariant variant) {
        var type = variant.type();
        if (type == MekanizedContent.ENERGY) return "Energy";
        String value = type.key().value();
        if (type.category() == MekanizedContent.CHEMICAL) return "<lang:chemical.mekanized." + value + ">";
        if (type.category() == MekanizedContent.FLUID) {
            String fluid = value.substring(value.indexOf('/') + 1);
            return fluid.equals("water") || fluid.equals("lava") ? "<lang:block.minecraft." + fluid + ">" : "<lang:block.mekanized." + fluid + ">";
        }
        return null;
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        fr.robie.machineengine.mekanized.machine.EnergyItems.setAccess(new PaperEnergyItems());
        fr.robie.machineengine.paper.ResourceNames.register(MekanizedPlugin::name);
        getServer().getPluginManager().registerEvents(new MekanizedTools(), this);
        MekanizedCommands commands = new MekanizedCommands(this);
        getLifecycleManager().registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                event -> event.registrar().register(commands.build(), "Mekanized commands"));
    }

    /**
     * Copies {@code mekanized/pack/} to {@code plugins/CraftEngine/resources/mekanized/}. Imported
     * assets ({@code resourcepack/}) are refreshed on every start; configuration files are installed
     * once, so server owners' edits are kept.
     */
    private void installCraftEnginePack(EditableFiles files) {
        Path target = getDataFolder().toPath().getParent().resolve("CraftEngine/resources/mekanized");
        int copied = 0;
        try (JarFile jar = new JarFile(getFile())) {
            // Imported assets are generated: start from an empty folder so files removed from the jar go too.
            deleteRecursively(target.resolve("resourcepack"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(GENERATED + "pack/")) continue;
                String relative = name.substring((GENERATED + "pack/").length());
                Path destination = target.resolve(relative);
                boolean asset = relative.startsWith("resourcepack/");
                if (!asset) {
                    if (!ENABLED_CONFIGURATION.contains(relative)) continue;
                    try (InputStream in = jar.getInputStream(entry)) {
                        if (files.install(destination, in.readAllBytes())) copied++;
                    }
                    continue;
                }
                Files.createDirectories(destination.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                copied++;
            }
            copyResource("pack.yml", target.resolve("pack.yml"), false);
            copyResource(GENERATED + "LICENSE-MEKANISM.txt", target.resolve("LICENSE-MEKANISM.txt"), true);
        } catch (IOException e) {
            getLogger().severe("Could not install the Mekanized CraftEngine pack: " + e.getMessage());
            return;
        }
        getLogger().info("Installed the Mekanized CraftEngine pack (" + copied + " files) in " + target);
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (var walk = Files.walk(folder)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private void copyResource(String resource, Path destination, boolean replace) throws IOException {
        if (!replace && Files.exists(destination)) return;
        try (InputStream in = getResource(resource)) {
            if (in == null) throw new IOException("missing resource " + resource);
            Files.createDirectories(destination.getParent());
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
