package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.paper.PaperTools;
import fr.robie.machineengine.mekanized.paper.PaperConfigurator;
import fr.robie.machineengine.mekanized.paper.PaperUtility;
import fr.robie.machineengine.mekanized.paper.PaperRadiation;
import fr.robie.machineengine.mekanized.paper.PaperChunkLoaders;
import fr.robie.machineengine.mekanized.paper.PaperEntangloporters;
import fr.robie.machineengine.mekanized.paper.PaperEnergyItems;
import fr.robie.machineengine.mekanized.paper.gear.GearTools;
import fr.robie.machineengine.mekanized.paper.gear.SuitTicker;
import fr.robie.machineengine.mekanized.paper.automation.PaperAutomation;
import fr.robie.machineengine.mekanized.paper.menu.MenuCatalog;
import fr.robie.machineengine.mekanized.paper.command.MekanizedCommands;

import fr.robie.machineengine.api.MachineEngine;
import fr.robie.machineengine.api.addon.AddonRegistration;
import fr.robie.machineengine.mekanized.machine.MekanizedRegistry;
import fr.robie.machineengine.mekanized.paper.PaperFurnaceRecipes;
import fr.robie.machineengine.mekanized.paper.PaperResourceNames;
import fr.robie.machineengine.mekanized.paper.persist.LineStore;
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
    /** Inside the jar: the content (src/main/resources/mekanized), under {@code mekanized/}. */
    private static final String CONTENT = "mekanized/";
    /**
     * The content files to install, as listed by the build ({@code mekanized/manifest.yml}): a file
     * added to the content is installed, with no list kept by hand to forget it in.
     */
    private ContentManifest manifest = new ContentManifest(java.util.Set.of(), java.util.List.of());

    @Override
    public void onLoad() {
        // Every class now: if the jar is replaced while the server runs, nothing breaks later.
        fr.robie.machineengine.api.paper.ClassPreloader.preload(MekanizedPlugin.class, "fr/robie/machineengine/mekanized/");
        // Before anything builds the machines and transports from them.
        MekanizedSettings.load(this);
        MekanizedRegistry.furnaceRecipes(PaperFurnaceRecipes::provide);
        MachineEngine.register(AddonRegistration.builder(getName(), getPluginMeta().getVersion())
                .requiresApi("[0.1,1.0)")
                .register(MekanizedContent::register)
                .build());
        try {
            this.manifest = ContentManifest.load(getClassLoader());
        } catch (IOException | RuntimeException e) {
            getLogger().severe("Could not read the list of Mekanized content files: " + e.getMessage());
        }
        // Editable content files are updated only while nobody changed them (EditableFiles).
        try (EditableFiles files = new EditableFiles(this)) {
            installCraftEnginePack(files);
            installDefinitions(files);
            MenuCatalog.install(this, files);
        } catch (IOException e) {
            getLogger().warning("Could not record the installed Mekanized files: " + e.getMessage());
        }
    }

    /**
     * Copies the definitions the manifest lists to {@code plugins/MachineEngine/definitions/mekanized/}:
     * server owners edit them there; edited files are never overwritten (delete one to get the default).
     */
    private void installDefinitions(EditableFiles files) {
        Path target = getDataFolder().toPath().getParent().resolve("MachineEngine/definitions/mekanized");
        for (String file : this.manifest.definitions()) {
            try (InputStream in = getResource(CONTENT + "definitions/" + file)) {
                if (in == null) throw new IOException("missing resource " + file);
                files.install(target.resolve(file), in.readAllBytes());
            } catch (IOException e) {
                getLogger().severe("Could not install the definitions file " + file + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        PaperEntangloporters.save();
        PaperRadiation.saveNow();
        LineStore.flush();
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void onEnable() {
        fr.robie.machineengine.mekanized.machine.EnergyItems.setAccess(new PaperEnergyItems());
        PaperUtility.install(this);
        PaperAutomation.install(this);
        SuitTicker.install(this);
        GearTools.install(this);
        PaperChunkLoaders.install(this);
        PaperEntangloporters.install(this);
        PaperRadiation.install(this);
        fr.robie.machineengine.api.paper.ResourceNames.register(PaperResourceNames::name);
        getServer().getPluginManager().registerEvents(new PaperConfigurator(), this);
        getServer().getPluginManager().registerEvents(new PaperTools(), this);
        MekanizedCommands commands = new MekanizedCommands(this);
        getLifecycleManager().registerEventHandler(io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                event -> event.registrar().register(commands.build(), "Mekanized commands"));
    }

    /**
     * Copies {@code mekanized/pack/} to {@code plugins/CraftEngine/resources/mekanized/}. Assets
     * ({@code resourcepack/}) are refreshed on every start; configuration files are installed
     * once, so server owners' edits are kept.
     */
    private void installCraftEnginePack(EditableFiles files) {
        Path target = getDataFolder().toPath().getParent().resolve("CraftEngine/resources/mekanized");
        int copied = 0;
        try (JarFile jar = new JarFile(getFile())) {
            // Assets are not editable: start from an empty folder so files removed from the jar go too.
            deleteRecursively(target.resolve("resourcepack"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(CONTENT + "pack/")) continue;
                String relative = name.substring((CONTENT + "pack/").length());
                Path destination = target.resolve(relative);
                boolean asset = relative.startsWith("resourcepack/");
                if (!asset) {
                    if (!this.manifest.pack().contains(relative)) continue;
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
            copyResource(CONTENT + "LICENSE-MEKANISM.txt", target.resolve("LICENSE-MEKANISM.txt"), true);
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
