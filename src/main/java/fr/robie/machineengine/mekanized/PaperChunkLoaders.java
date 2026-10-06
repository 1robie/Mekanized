package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Chunk tickets for {@link ChunkLoaders} (anchor upgrades, dimensional stabilizers), saved in
 * {@code plugins/Mekanized/chunk_loaders.txt} so the chunks load again when the server starts.
 * Tickets change on the global thread's bookkeeping, then on the thread of each chunk's region.
 */
final class PaperChunkLoaders {
    private final Plugin plugin;
    private final Path file;
    /** Chunks holding a ticket: world, chunk x, chunk z (global region thread only). */
    private final Set<Chunk> ticketed = new HashSet<>();

    private record Chunk(Key world, int x, int z) {
    }

    private PaperChunkLoaders(Plugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("chunk_loaders.txt");
    }

    static void install(Plugin plugin) {
        PaperChunkLoaders loaders = new PaperChunkLoaders(plugin);
        loaders.load();
        ChunkLoaders.onChange(() -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
            loaders.save();
            loaders.sync();
        }));
        Bukkit.getGlobalRegionScheduler().execute(plugin, loaders::sync);
    }

    /** Adds the tickets now wanted and removes the others. */
    private void sync() {
        Set<Chunk> wanted = new HashSet<>();
        for (Map.Entry<WorldPosition, Integer> loader : ChunkLoaders.loaders().entrySet()) {
            WorldPosition at = loader.getKey();
            int cx = at.position().x() >> 4;
            int cz = at.position().z() >> 4;
            int radius = loader.getValue();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) wanted.add(new Chunk(at.world(), cx + dx, cz + dz));
            }
        }
        for (Chunk chunk : Set.copyOf(this.ticketed)) {
            if (!wanted.contains(chunk) && ticket(chunk, false)) this.ticketed.remove(chunk);
        }
        for (Chunk chunk : wanted) {
            if (!this.ticketed.contains(chunk) && ticket(chunk, true)) this.ticketed.add(chunk);
        }
    }

    private boolean ticket(Chunk chunk, boolean add) {
        World world = Bukkit.getWorld(new NamespacedKey(chunk.world().namespace(), chunk.world().value()));
        if (world == null) return false;
        Bukkit.getRegionScheduler().execute(this.plugin, world, chunk.x(), chunk.z(), () -> {
            if (add) world.addPluginChunkTicket(chunk.x(), chunk.z(), this.plugin);
            else world.removePluginChunkTicket(chunk.x(), chunk.z(), this.plugin);
        });
        return true;
    }

    private void load() {
        if (!Files.isRegularFile(this.file)) return;
        try {
            for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != 5) continue;
                WorldPosition position = WorldPosition.of(Key.key(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                ChunkLoaders.loaders().put(position, Integer.parseInt(parts[4]));
            }
        } catch (IOException | RuntimeException e) {
            this.plugin.getLogger().warning("Could not read " + this.file + ": " + e.getMessage());
        }
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        ChunkLoaders.loaders().forEach((position, radius) -> text.append(position.world().asString()).append('|')
                .append(position.position().x()).append('|').append(position.position().y()).append('|')
                .append(position.position().z()).append('|').append(radius).append('\n'));
        PaperFiles.write(this.plugin, this.file, text);
    }
}
