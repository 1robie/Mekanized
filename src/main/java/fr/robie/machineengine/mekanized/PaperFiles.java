package fr.robie.machineengine.mekanized;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Writes Mekanized's data files off the game threads: the text is taken where the change happens,
 * the disk work runs on one background thread, in order. {@link #flush} waits for it when the plugin stops.
 */
final class PaperFiles {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Mekanized file writer");
        thread.setDaemon(true);
        return thread;
    });

    private PaperFiles() {
    }

    /** Replaces {@code file} with {@code text}, in the background. */
    static void write(Plugin plugin, Path file, CharSequence text) {
        String content = text.toString();
        WRITER.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save " + file + ": " + e.getMessage());
            }
        });
    }

    /** Waits (up to 10 seconds) for the writes already asked for. */
    static void flush() {
        try {
            WRITER.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // shutting down: nothing more to do
        }
    }
}
