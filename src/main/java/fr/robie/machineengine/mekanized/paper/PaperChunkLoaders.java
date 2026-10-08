package fr.robie.machineengine.mekanized.paper;

import fr.robie.machineengine.api.world.WorldPosition;
import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.paper.persist.LineStore;
import fr.robie.machineengine.mekanized.machine.SaveLines;
import net.kyori.adventure.key.Key;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Chunk tickets for {@link ChunkLoaders} (anchor upgrades, dimensional stabilizers), saved in
 * {@code plugins/Mekanized/chunk_loaders.txt} so the chunks load again when the server starts.
 * Tickets change on the global thread's bookkeeping, then on the thread of each chunk's region.
 */
public final class PaperChunkLoaders {
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

    public static void install(Plugin plugin) {
        PaperChunkLoaders loaders = new PaperChunkLoaders(plugin);
        loaders.load();
        ChunkLoaders.onChange(() -> PaperWorld.scheduler().run(() -> {
            loaders.save();
            loaders.sync();
        }));
        PaperWorld.scheduler().run(loaders::sync);
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
        World world = PaperWorld.world(chunk.world());
        if (world == null) return false;
        PaperWorld.scheduler().run(world, chunk.x(), chunk.z(), () -> {
            if (add) world.addPluginChunkTicket(chunk.x(), chunk.z(), this.plugin);
            else world.removePluginChunkTicket(chunk.x(), chunk.z(), this.plugin);
        });
        return true;
    }

    private void load() {
        LineStore.load(this.plugin, this.file, SaveLines.CHUNK_LOADER_FIELDS, SaveLines::readChunkLoader);
    }

    private void save() {
        StringBuilder text = new StringBuilder();
        ChunkLoaders.loaders().forEach((position, radius) -> text.append(SaveLines.chunkLoader(position, radius)));
        LineStore.write(this.plugin, this.file, text);
    }
}
