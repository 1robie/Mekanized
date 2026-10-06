package fr.robie.machineengine.mekanized.machine;

import fr.robie.machineengine.api.data.Codecs;
import fr.robie.machineengine.api.data.VersionedCodec;
import fr.robie.machineengine.api.machine.MachineDataKey;
import fr.robie.machineengine.mekanized.MekanizedContent;

/** Builders for the machine data keys of Mekanized, the keys several machine families share, and parsing of values typed into menus. */
public final class DataKeys {
    /** Whether a machine or structure was switched on in its menu. */
    public static final MachineDataKey<Boolean> RUNNING = bool("running", false);
    /** What a machine is doing, shown in its menu. */
    public static final MachineDataKey<String> STATUS = string("status", "Stopped");

    private DataKeys() {
    }

    /** A text value (settings typed in menus, modes, owners). */
    public static MachineDataKey<String> string(String id, String defaultValue) {
        return MachineDataKey.of(id, VersionedCodec.builder(MekanizedContent.key(id), Codecs.STRING).build(), defaultValue);
    }

    public static MachineDataKey<Boolean> bool(String id, boolean defaultValue) {
        return MachineDataKey.of(id, VersionedCodec.builder(MekanizedContent.key(id), Codecs.BOOL).build(), defaultValue);
    }

    public static MachineDataKey<Long> number(String id, long defaultValue) {
        return MachineDataKey.of(id, VersionedCodec.builder(MekanizedContent.key(id), Codecs.LONG).build(), defaultValue);
    }

    /** A counter that starts at 0. */
    public static MachineDataKey<Long> number(String id) {
        return number(id, 0L);
    }

    /** {@code value} as an int within {@code [min, max]}; {@code fallback} when it is not a number. */
    public static int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** {@code value} as a long within {@code [min, max]}; {@code fallback} when it is not a number. */
    public static long parseLong(String value, long fallback, long min, long max) {
        try {
            return Math.max(min, Math.min(max, Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** {@code value} as a double within {@code [min, max]}; {@code fallback} when it is not a number. */
    public static double parseDouble(String value, double fallback, double min, double max) {
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(value.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
