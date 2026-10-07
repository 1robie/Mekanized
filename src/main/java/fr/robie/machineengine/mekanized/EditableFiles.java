package fr.robie.machineengine.mekanized;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.CRC32;

/**
 * Installs content files server owners may edit (recipes, menus, block configs): a new version
 * replaces the installed file only if nobody changed it since Mekanized wrote it. Edited files are
 * kept (with a warning), so updates never overwrite a server's changes. Checksums of what was
 * installed live in {@code plugins/Mekanized/installed.properties}.
 */
public final class EditableFiles implements AutoCloseable {
    private final Plugin plugin;
    private final Path index;
    private final Properties installed = new Properties();
    private final Path root;

    EditableFiles(Plugin plugin) {
        this.plugin = plugin;
        this.index = plugin.getDataFolder().toPath().resolve("installed.properties");
        this.root = plugin.getDataFolder().toPath().getParent();
        if (Files.exists(this.index)) {
            try (InputStream in = Files.newInputStream(this.index)) {
                this.installed.load(in);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not read " + this.index + ": " + e.getMessage());
            }
        }
    }

    /** @return whether the file was written */
    public boolean install(Path destination, byte[] content) throws IOException {
        String key = this.root.relativize(destination).toString().replace('\\', '/');
        long fresh = crc(content);
        if (Files.exists(destination)) {
            long current = crc(Files.readAllBytes(destination));
            if (current == fresh) {
                this.installed.setProperty(key, Long.toHexString(fresh));
                return false;
            }
            String recorded = this.installed.getProperty(key);
            if (recorded == null || current != Long.parseLong(recorded, 16)) {
                this.plugin.getLogger().warning(key + " was edited: kept as is, not updated (delete it to get the new default)");
                return false;
            }
        }
        Files.createDirectories(destination.getParent());
        Files.write(destination, content);
        this.installed.setProperty(key, Long.toHexString(fresh));
        return true;
    }

    private static long crc(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return crc.getValue();
    }

    @Override
    public void close() throws IOException {
        Files.createDirectories(this.index.getParent());
        try (OutputStream out = Files.newOutputStream(this.index)) {
            this.installed.store(out, "Checksums of the files Mekanized installed (do not edit)");
        }
    }
}
