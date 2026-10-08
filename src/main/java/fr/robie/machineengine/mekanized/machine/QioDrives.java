package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.mekanized.MekanizedContent;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * What a QIO drive item holds: its item config's {@code mekanized:qio_drive} behavior (D32, D34), read
 * through the lookup the platform installs ({@link #setAccess}). Without one (headless tests), the four
 * drives hold Mekanism's numbers.
 */
public final class QioDrives {
    /** Items, and kinds of item, a drive holds. */
    public record Limits(long items, long kinds) {
    }

    /** Mekanism's drives: base, hyper-dense, time-dilating, supermassive. */
    public static final Map<Key, Limits> DEFAULTS = Map.of(
            MekanizedContent.key("qio_drive_base"), new Limits(16_000, 128),
            MekanizedContent.key("qio_drive_hyper_dense"), new Limits(128_000, 256),
            MekanizedContent.key("qio_drive_time_dilating"), new Limits(1_048_000, 1_024),
            MekanizedContent.key("qio_drive_supermassive"), new Limits(16_000_000, 8_192));

    private static volatile Function<Key, @Nullable Limits> access = DEFAULTS::get;

    private QioDrives() {
    }

    /** What the item {@code item} holds as a drive, or {@code null} if it is no drive. */
    public static @Nullable Limits of(@NotNull Key item) {
        return access.apply(item);
    }

    /** Installs the platform's lookup; {@code null} goes back to {@link #DEFAULTS}. */
    public static void setAccess(@Nullable Function<Key, @Nullable Limits> value) {
        access = value == null ? DEFAULTS::get : value;
    }
}
