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
    private static final java.util.Set<String> ENABLED_CONFIGURATION = java.util.Set.of();

    @Override
    public void onLoad() {
        MachineEngine.register(AddonRegistration.builder(getName(), getPluginMeta().getVersion())
                .requiresApi("[0.1,1.0)")
                .register(MekanizedContent::register)
                .build());
        installCraftEnginePack();
    }

    /**
     * Copies {@code mekanized/pack/} to {@code plugins/CraftEngine/resources/mekanized/}. Imported
     * assets ({@code resourcepack/}) are refreshed on every start; configuration files are installed
     * once, so server owners' edits are kept.
     */
    private void installCraftEnginePack() {
        Path target = getDataFolder().toPath().getParent().resolve("CraftEngine/resources/mekanized");
        int copied = 0;
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(GENERATED + "pack/")) continue;
                String relative = name.substring((GENERATED + "pack/").length());
                Path destination = target.resolve(relative);
                boolean asset = relative.startsWith("resourcepack/");
                if (!asset && (!ENABLED_CONFIGURATION.contains(relative) || Files.exists(destination))) continue;
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

    private void copyResource(String resource, Path destination, boolean replace) throws IOException {
        if (!replace && Files.exists(destination)) return;
        try (InputStream in = getResource(resource)) {
            if (in == null) throw new IOException("missing resource " + resource);
            Files.createDirectories(destination.getParent());
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
