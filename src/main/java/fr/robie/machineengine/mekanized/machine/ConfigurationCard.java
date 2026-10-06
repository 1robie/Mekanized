package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.machine.PortDirection;
import fr.robie.machineengine.api.world.RelativeSide;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The text a configuration card holds: the machine type; then per port {@code port:id=DIRECTION:SIDES}
 * (sides joined with {@code +}), {@code eject=true|false} and {@code redstone=mode}, all joined with
 * {@code ;}. Reading is forgiving, like a card that was edited or written by an older version: a part
 * that cannot be read is skipped, and a port whose sides cannot be read keeps just its direction.
 */
public final class ConfigurationCard {
    /** One port of the card; {@code sides} is null when the card's sides could not be read. */
    public record PortSetting(@NotNull String port, @NotNull PortDirection direction, @Nullable Set<RelativeSide> sides) {
    }

    /** What a card says: its machine type, the ports in order, and the optional settings. */
    public record Card(@NotNull String type, @NotNull List<PortSetting> ports, @Nullable Boolean eject, @Nullable String redstone) {
    }

    private ConfigurationCard() {
    }

    /** The card text of a machine; {@code eject} and {@code redstone} are null when the machine has no such setting. */
    public static @NotNull String encode(@NotNull String type, @NotNull List<PortSetting> ports, @Nullable Boolean eject, @Nullable String redstone) {
        List<String> parts = new ArrayList<>();
        parts.add(type);
        for (PortSetting port : ports) {
            StringBuilder sides = new StringBuilder();
            if (port.sides() != null) {
                for (RelativeSide side : port.sides()) sides.append(sides.isEmpty() ? "" : "+").append(side.name());
            }
            parts.add("port:" + port.port() + "=" + port.direction().name() + ":" + sides);
        }
        if (eject != null) parts.add("eject=" + eject);
        if (redstone != null) parts.add("redstone=" + redstone);
        return String.join(";", parts);
    }

    /** Reads a card's text; later {@code eject} and {@code redstone} parts win over earlier ones. */
    public static @NotNull Card parse(@NotNull String text) {
        String[] parts = text.split(";");
        List<PortSetting> ports = new ArrayList<>();
        Boolean eject = null;
        String redstone = null;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            try {
                if (part.startsWith("port:")) {
                    String port = part.substring(5, part.indexOf('='));
                    String[] value = part.substring(part.indexOf('=') + 1).split(":", -1);
                    PortDirection direction = PortDirection.valueOf(value[0]);
                    Set<RelativeSide> sides;
                    try {
                        sides = EnumSet.noneOf(RelativeSide.class);
                        if (!value[1].isEmpty()) for (String side : value[1].split("\\+")) sides.add(RelativeSide.valueOf(side));
                    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                        sides = null;
                    }
                    ports.add(new PortSetting(port, direction, sides));
                } else if (part.startsWith("eject=")) {
                    eject = Boolean.parseBoolean(part.substring(6));
                } else if (part.startsWith("redstone=")) {
                    redstone = part.substring(9);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException ignored) {
                // an old or edited card: skip the part
            }
        }
        return new Card(parts[0], ports, eject, redstone);
    }
}
