package fr.robie.machineengine.mekanized.paper.persist;

import fr.robie.machineengine.api.world.WorldPosition;
import net.kyori.adventure.key.Key;

/** The {@code world|x|y|z} text form of a position in the data files. */
public final class WorldPositions {
    private WorldPositions() {
    }

    /** The position written as four fields of {@code parts}, starting at {@code from}. */
    public static WorldPosition read(String[] parts, int from) {
        return WorldPosition.of(Key.key(parts[from]), Integer.parseInt(parts[from + 1]), Integer.parseInt(parts[from + 2]),
                Integer.parseInt(parts[from + 3]));
    }

    /** Appends {@code world|x|y|z} (no trailing separator). */
    public static StringBuilder append(StringBuilder text, WorldPosition position) {
        return text.append(position.world().asString()).append('|').append(position.position().x()).append('|')
                .append(position.position().y()).append('|').append(position.position().z());
    }
}
