package fr.robie.machineengine.mekanized.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Item names and lore lines: Minecraft italicises them unless told not to. */
public final class Lore {
    private Lore() {
    }

    /** A gray, non-italic line. */
    public static Component line(String text) {
        return line(text, NamedTextColor.GRAY);
    }

    /** A non-italic line of {@code color}. */
    public static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
