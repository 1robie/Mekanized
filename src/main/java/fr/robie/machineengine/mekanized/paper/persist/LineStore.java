package fr.robie.machineengine.mekanized.paper.persist;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mekanized's line-based data files ({@code field|field|...}, one record per line). Saving takes the text
 * where the change happens and runs the disk work on one background thread, in order; {@link #flush} waits
 * for it when the plugin stops. Loading reads the file where it is called, once, at startup.
 */
public final class LineStore {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Mekanized file writer");
        thread.setDaemon(true);
        return thread;
    });

    private LineStore() {
    }

    /** Replaces {@code file} with {@code text}, in the background. */
    public static void write(Plugin plugin, Path file, CharSequence text) {
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

    /**
     * Reads {@code file} if it exists and hands each line, split on {@code |}, to {@code record}; lines
     * without exactly {@code fields} fields are skipped. A line that fails to parse stops the load and is logged.
     */
    public static void load(Plugin plugin, Path file, int fields, Consumer<String[]> record) {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != fields) continue;
                record.accept(parts);
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Could not read " + file + ": " + e.getMessage());
        }
    }

    /** Waits (up to 10 seconds) for the writes already asked for. */
    public static void flush() {
        try {
            WRITER.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // shutting down: nothing more to do
        }
    }
}
