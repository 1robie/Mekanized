package fr.robie.mekanized.machine;

import fr.robie.machineengine.api.world.WorldPosition;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The text of Mekanized's line-based data files ({@code field|field|...}, one record per line): how each
 * kind of record is written and read. The platform ({@code LineStore}) does the file work.
 */
public final class SaveLines {
    private SaveLines() {
    }

    // -- the format ---------------------------------------------------------------------------------

    /**
     * Hands each line, split on {@code |}, to {@code record}; lines without exactly {@code fields} fields are
     * skipped. A line that fails to parse ends the reading with that exception.
     */
    public static void read(@NotNull Iterable<String> lines, int fields, @NotNull Consumer<String[]> record) {
        for (String line : lines) {
            String[] parts = line.split("\\|");
            if (parts.length != fields) continue;
            record.accept(parts);
        }
    }

    /** The position written as four fields of {@code parts}, starting at {@code from}. */
    public static @NotNull WorldPosition readPosition(@NotNull String[] parts, int from) {
        return WorldPosition.of(Key.key(parts[from]), Integer.parseInt(parts[from + 1]), Integer.parseInt(parts[from + 2]),
                Integer.parseInt(parts[from + 3]));
    }

    /** Appends {@code world|x|y|z} (no trailing separator). */
    public static @NotNull StringBuilder appendPosition(@NotNull StringBuilder text, @NotNull WorldPosition position) {
        return text.append(position.world().asString()).append('|').append(position.position().x()).append('|')
                .append(position.position().y()).append('|').append(position.position().z());
    }

    // -- chunk loaders: world|x|y|z|radius ----------------------------------------------------------

    public static @NotNull String chunkLoader(@NotNull WorldPosition position, int radius) {
        return appendPosition(new StringBuilder(), position).append('|').append(radius).append('\n').toString();
    }

    /** A chunk loader line read ({@value #CHUNK_LOADER_FIELDS} fields). */
    public static void readChunkLoader(@NotNull String[] parts) {
        ChunkLoaders.loaders().put(readPosition(parts, 0), Integer.parseInt(parts[4]));
    }

    public static final int CHUNK_LOADER_FIELDS = 5;

    // -- radiation sources: world|x|y|z|strength ----------------------------------------------------

    public static @NotNull String radiationSource(@NotNull WorldPosition position, double strength) {
        return appendPosition(new StringBuilder(), position).append('|')
                .append(String.format(Locale.ROOT, "%.9g", strength)).append('\n').toString();
    }

    public static void readRadiationSource(@NotNull String[] parts) {
        Radiation.sources().put(readPosition(parts, 0), Double.parseDouble(parts[4]));
    }

    public static final int RADIATION_FIELDS = 5;

    // -- teleporters: frequency|world|x|y|z|formed --------------------------------------------------

    /** The frequency loses any {@code |}, which would split it. */
    public static @NotNull String teleporter(@NotNull WorldPosition position, @NotNull Frequencies.Teleporter teleporter) {
        StringBuilder text = new StringBuilder().append(teleporter.frequency().replace("|", "")).append('|');
        return appendPosition(text, position).append('|').append(teleporter.formed()).append('\n').toString();
    }

    public static void readTeleporter(@NotNull String[] parts) {
        Frequencies.teleporters().put(readPosition(parts, 1), new Frequencies.Teleporter(parts[0], Boolean.parseBoolean(parts[5])));
    }

    public static final int TELEPORTER_FIELDS = 6;

    // -- entangloporter buffers: frequency(base64)|channel|k or i|payload|amount ---------------------

    /** One stored resource: an item (its bytes in base64) or a chemical or fluid (its resource key). */
    public record Stored(@NotNull String frequency, @NotNull String channel, boolean item, @NotNull String payload, long amount) {
    }

    public static final int ENTANGLOPORTER_FIELDS = 5;

    public static @NotNull String entangloporter(@NotNull Stored stored) {
        String name = Base64.getEncoder().encodeToString(stored.frequency().getBytes(StandardCharsets.UTF_8));
        return name + '|' + stored.channel() + '|' + (stored.item() ? "i" : "k") + '|' + stored.payload() + '|' + stored.amount() + '\n';
    }

    public static @NotNull Stored readEntangloporter(@NotNull String[] parts) {
        String frequency = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        return new Stored(frequency, parts[1], parts[2].equals("i"), parts[3], Long.parseLong(parts[4]));
    }
}
