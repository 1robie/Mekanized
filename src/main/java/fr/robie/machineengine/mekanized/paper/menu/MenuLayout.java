package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * A textured menu (docs/MENU_TEXTURES_PLAN.md): widgets placed by chest row and column, each adding its
 * buttons and the images its title draws (Mekanized's gui.yml). Bars are 3 slots long and their buttons
 * name their item from placeholders, so MachineEngine redraws them live; they need no zMenu update.
 */
final class MenuLayout {
    /** Where the tab icons go, as Mekanism's tabs on the right of its windows: the top right corner. */
    static final int UPGRADES = 8;
    static final int SIDE_CONFIG = 7;
    static final int REDSTONE = 6;

    /** Mekanism's slot frames: an input is red, an output blue, an extra input yellow, an energy slot green. */
    enum Kind {
        NORMAL("slot_normal"), INPUT("slot_input"), INPUT_2("slot_input_2"), OUTPUT("slot_output"), OUTPUT_2("slot_output_2"),
        EXTRA("slot_extra"), POWER("slot_power");

        final String image;

        Kind(String image) {
            this.image = image;
        }
    }

    /** A tall gauge's fill: Mekanism's chemical texture, or water's, tinted with the resource's color. */
    enum Tank {
        CHEMICAL("chemical", "<light_purple>"), FLUID("fluid", "<aqua>");

        final String shape;
        final String color;

        Tank(String shape, String color) {
            this.shape = shape;
            this.color = color;
        }
    }

    /** An image of the title, its left edge at {@code x} in the window. */
    private record Glyph(String image, int x, int width) {
    }

    private final String machine;
    private final int rows;
    private final Map<String, String> buttons = new LinkedHashMap<>();
    private final List<Glyph> glyphs = new ArrayList<>();
    private int sideSlot = SIDE_CONFIG;
    /** The window's name (MiniMessage); the machine's translated name if {@code null}. */
    private String name;

    MenuLayout(String machine, int rows) {
        if (rows < 3 || rows > 6) throw new IllegalArgumentException("rows: " + rows);
        this.machine = machine;
        this.rows = rows;
    }

    static int at(int row, int column) {
        return row * 9 + column;
    }

    /** The x of a slot's 18 px frame in the window. */
    private static int frameX(int column) {
        return 7 + 18 * column;
    }

    private void image(String image, int row, int column, int width) {
        this.glyphs.add(new Glyph("mekanized:gui/" + image + "_r" + row, frameX(column), width));
    }

    /** A storage slot: click to put the cursor item in or take items out. */
    MenuLayout slot(String name, int row, int column, Kind kind, String storage, int storageSlot, String label) {
        image(kind.image, row, column, 18);
        if (kind == Kind.POWER) image("overlay_power", row, column, 18);
        this.buttons.put(name, "    type: machineengine_slot\n    slot: " + at(row, column) + "\n    storage: " + storage
                + "\n    storage-slot: " + storageSlot + "\n    update: true\n" + item("craftengine:mekanized:gui/empty", "<gray>" + label));
        return this;
    }

    MenuLayout slot(String name, int row, int column, Kind kind, String storage, String label) {
        return slot(name, row, column, kind, storage, 1, label);
    }

    /** A fluid tank's bucket slots: buckets to fill or empty, and the buckets that come out. */
    MenuLayout buckets(int row, int inColumn, int outColumn) {
        return slot("bucket-in", row, inColumn, Kind.INPUT, "bucket_in", "Bucket (fills or empties)")
                .slot("bucket-out", row, outColumn, Kind.OUTPUT, "bucket_out", "Buckets out");
    }

    /** The energy item slot (redstone and energy items charge the machine). */
    MenuLayout energySlot(int row, int column) {
        return slot("energy-slot", row, column, Kind.POWER, "energy_slot", "Energy item (redstone: 10 kJ each)");
    }

    /**
     * A tall gauge from {@code topRow} down 3 rows: the storage's amount, tinted with what it holds.
     *
     * @param storageSlot 0 for the whole storage, else one of its slots (1-based)
     */
    MenuLayout tank(String name, int topRow, int column, Tank tank, String storage, int storageSlot, String label) {
        image("frame_tall_gauge", topRow, column, 18);
        String prefix = "%zmenu_machineengine_storage_" + storage + "_" + (storageSlot > 0 ? "slot_" + storageSlot + "_" : "");
        bar(name, vertical(topRow, column), tank.shape, cells(prefix + "cell_"),prefix + "color%", tank.color + label,
                "<gray>" + prefix + "name%", "<gray>" + prefix + "amount% / " + prefix + "capacity% mB");
        return this;
    }

    MenuLayout tank(String name, int topRow, int column, Tank tank, String storage, String label) {
        return tank(name, topRow, column, tank, storage, 0, label);
    }

    /** Mekanism's energy bar, from {@code topRow} down 3 rows. */
    MenuLayout energy(int topRow, int column) {
        image("frame_power_vertical", topRow, column, 18);
        String prefix = "%zmenu_machineengine_storage_energy_";
        bar("energy", vertical(topRow, column), "energy", cells(prefix + "cell_"),null, "<green>Energy",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% J");
        return this;
    }

    /** A horizontal energy bar, from {@code column} right 3 columns. */
    MenuLayout energyHorizontal(int row, int column) {
        image("frame_power_horizontal", row, column, 54);
        String prefix = "%zmenu_machineengine_storage_energy_";
        bar("energy", horizontal(row, column), "energy_horizontal", cells(prefix + "cell_"),null, "<green>Energy",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% J");
        return this;
    }

    /** A heat buffer, from {@code column} right 3 columns. */
    MenuLayout heat(String name, int row, int column, String storage, String label) {
        image("frame_power_horizontal", row, column, 54);
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        bar(name, horizontal(row, column), "heat", cells(prefix + "cell_"),prefix + "color%", "<gold>" + label,
                "<gray>" + prefix + "amount% / " + prefix + "capacity% J");
        return this;
    }

    /** A chemical or other tinted amount as a horizontal bar, from {@code column} right 3 columns. */
    MenuLayout horizontalTank(String name, int row, int column, String storage, String label) {
        image("frame_power_horizontal", row, column, 54);
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        bar(name, horizontal(row, column), "heat", cells(prefix + "cell_"),prefix + "color%", "<light_purple>" + label,
                "<gray>" + prefix + "name%", "<gray>" + prefix + "amount% / " + prefix + "capacity% mB");
        return this;
    }

    /** A factory lane's down arrow in one slot, following a percent setting ({@code data}). */
    MenuLayout lane(String name, int row, int column, String data, String label) {
        image("frame_lane", row, column, 18);
        bar(name, new int[]{at(row, column)}, "lane", k -> "%zmenu_machineengine_data_" + data + "_cell%", null,
                "<yellow>" + label, "<gray>%zmenu_machineengine_data_" + data + "% %");
        return this;
    }

    /** The process's progress arrow, from {@code column} right 3 columns. */
    MenuLayout progress(int row, int column) {
        image("frame_progress", row, column, 54);
        bar("progress", horizontal(row, column), "progress", cells("%zmenu_machineengine_process_cell_"), null, "<yellow>Progress",
                "<gray>%zmenu_machineengine_process_percent% %");
        return this;
    }

    /** Slots of a vertical bar, bottom first. */
    private static int[] vertical(int topRow, int column) {
        return new int[]{at(topRow + 2, column), at(topRow + 1, column), at(topRow, column)};
    }

    /** Slots of a horizontal bar, left first. */
    private static int[] horizontal(int row, int column) {
        return new int[]{at(row, column), at(row, column + 1), at(row, column + 2)};
    }

    /** The level placeholders of a 3-slot bar: {@code <prefix><k>%}. */
    private static IntFunction<String> cells(String prefix) {
        return k -> prefix + k + "%";
    }

    /**
     * One button per cell, naming its item {@code mekanized:gui/bar/<shape>/<k>/<level>}.
     *
     * @param level the placeholder of cell k's level, e.g. {@code %zmenu_machineengine_process_cell_0%}
     * @param color the color placeholder of a tinted bar, or {@code null}
     */
    private void bar(String name, int[] slots, String shape, IntFunction<String> level, String color, String label, String... lore) {
        for (int k = 0; k < slots.length; k++) {
            StringBuilder button = new StringBuilder("    slot: ").append(slots[k]).append('\n')
                    .append("    item:\n      material: \"craftengine:mekanized:gui/bar/").append(shape).append('/').append(k).append('/')
                    .append(level.apply(k)).append("\"\n");
            if (color != null) {
                button.append("      components:\n        dyed-color: \"").append(color).append("\"\n")
                        .append("      flags: [HIDE_DYE, HIDE_ATTRIBUTES]\n");
            }
            button.append(text(label, lore));
            this.buttons.put(name + "-" + k, button.toString());
        }
    }

    /** Any other button (displays, settings), as {@link MenuYaml} writes them. */
    MenuLayout button(String name, String yaml) {
        this.buttons.put(name, yaml);
        return this;
    }

    /** The upgrades button, as Mekanism's upgrade tab. */
    MenuLayout upgrades() {
        this.buttons.put("upgrades", "    type: machineengine_upgrades\n    slot: " + UPGRADES + "\n    update: true\n"
                + item("craftengine:mekanized:gui/icon/upgrade", "<aqua>Upgrades", "<gray>Installed: %zmenu_machineengine_upgrades%",
                "<dark_gray>Click with an upgrade to install,", "<dark_gray>with an empty hand to take one out"));
        return this;
    }

    /** Mekanism's redstone control, its icon showing the mode. */
    MenuLayout redstone() {
        this.buttons.put("redstone", "    type: machineengine_data\n    data: redstone\n    values: [ignored, high, low]\n    slot: " + REDSTONE
                + "\n    update: true\n" + item("craftengine:mekanized:gui/icon/redstone_%zmenu_machineengine_data_redstone%",
                "<red>Redstone control: <yellow>%zmenu_machineengine_data_redstone%", "<gray>ignored: always works",
                "<gray>high: works while powered", "<gray>low: works while not powered", "<dark_gray>Click to switch"));
        return this;
    }

    /** No side configuration button (a machine without sides). */
    /** A window name other than the machine's (MiniMessage). */
    MenuLayout name(String name) {
        this.name = name;
        return this;
    }

    /** A slot frame with no button of its own, e.g. behind a setting or to mark the open tab. */
    MenuLayout frame(Kind kind, int row, int column) {
        image(kind.image, row, column, 18);
        return this;
    }

    MenuLayout noSides() {
        this.sideSlot = -1;
        return this;
    }

    Menu build() {
        return new Menu(this.machine, this.buttons, this.sideSlot, this.rows * 9, title());
    }

    /**
     * The window: the panel, then each image at its x (the cursor moves by an image's width + 1), then
     * the machine's name at x 8, translated on the client.
     */
    String title() {
        StringBuilder title = new StringBuilder("<white><shift:-8><image:mekanized:gui/panel_").append(this.rows).append('>');
        int cursor = 177;
        List<Glyph> sorted = new ArrayList<>(this.glyphs);
        sorted.sort(Comparator.comparingInt(Glyph::x));
        for (Glyph glyph : sorted) {
            title.append("<shift:").append(glyph.x() - cursor).append("><image:").append(glyph.image()).append('>');
            cursor = glyph.x() + glyph.width() + 1;
        }
        String name = this.name != null ? this.name : "<lang:block.mekanized." + this.machine + ">";
        return title.append("<shift:").append(8 - cursor).append("></white>").append(name).toString();
    }

    /** {@code item:} with a material (quoted: it may hold placeholders), a name and lore. */
    static String item(String material, String name, String... lore) {
        return "    item:\n      material: \"" + material + "\"\n" + text(name, lore);
    }

    private static String text(String name, String... lore) {
        StringBuilder text = new StringBuilder("      name: \"<!i>").append(name).append("\"\n");
        if (lore.length > 0) {
            text.append("      lore:\n");
            for (String line : lore) text.append("        - \"<!i>").append(line).append("\"\n");
        }
        return text.toString();
    }
}
