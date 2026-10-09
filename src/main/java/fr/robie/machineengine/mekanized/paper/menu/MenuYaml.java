package fr.robie.machineengine.mekanized.paper.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.SideMenus.SIDE_MENU;

/**
 * The zMenu YAML of a whole menu and of the plain buttons (displays, settings), and the layouts several
 * machine families share (processing, powered, generator, energy cube). The textured widgets (slots,
 * bars, tabs) are {@link MenuLayout}'s.
 */
final class MenuYaml {
    private MenuYaml() {
    }

    /**
     * @param sideSlot where the side configuration button goes, -1 for none
     * @param size     the inventory size (rows of 9)
     * @param title    the window: the images drawing it, then the machine's name ({@link MenuLayout#title()})
     */
    record Menu(String machine, Map<String, String> buttons, int sideSlot, int size, String title) {
    }

    /** A multiblock's status line ({@code status} data). */
    static String status(int slot) {
        return display(slot, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_data_status%");
    }

    /** A button cycling a text setting through {@code values}. */
    static String setting(int slot, String data, List<String> values, String material, String name, String... lore) {
        List<String> all = new ArrayList<>(List.of(lore));
        all.add("<dark_gray>Click to change");
        return "    type: machineengine_data\n    data: " + data + "\n    values: [" + String.join(", ", values) + "]\n    slot: " + slot
                + "\n    update: true\n" + item(material, name, all.toArray(String[]::new));
    }

    /**
     * Mekanism's processing window, 5 rows: input, progress arrow and output(s) across row 2, the energy
     * bar on the right with its slot below, the upgrade and side tabs top right; a chemical tank on the
     * left with its item slot below, if any.
     *
     * @param chemical the label of the chemical tank's item slot, or {@code null} for no tank
     */
    static MenuLayout processing(String machine, boolean secondOutput, String chemical) {
        MenuLayout layout = powered(machine)
                .slot("input", 2, 1, MenuLayout.Kind.INPUT, "input", "Input")
                .progress(2, 2)
                .slot("output", 2, 5, MenuLayout.Kind.OUTPUT, "output", "Output");
        if (secondOutput) layout.slot("output-2", 2, 6, MenuLayout.Kind.OUTPUT_2, "output", 2, "Secondary output");
        if (chemical != null) {
            layout.tank("chemical", 1, 0, MenuLayout.Tank.CHEMICAL, "chemical", "Chemical")
                    .slot("chemical-slot", 4, 0, MenuLayout.Kind.EXTRA, "chemical_slot", chemical);
        }
        return layout;
    }

    /** A powered machine, 5 rows: the energy bar on the right with its slot below, the upgrade and side tabs. */
    static MenuLayout powered(String machine) {
        return new MenuLayout(machine, 5).energy(1, 8).energySlot(4, 8).upgrades();
    }

    /**
     * Generators, 5 rows: the energy bar on the right, the fuel slot (burning generators) and the state
     * across row 2.
     */
    static MenuLayout generator(String machine, boolean fuel) {
        MenuLayout layout = new MenuLayout(machine, 5)
                .energy(1, 8)
                .button("state", display(22, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        if (fuel) layout.slot("fuel", 2, 2, MenuLayout.Kind.INPUT, "fuel", "Fuel");
        return layout;
    }

    /** Energy cubes, 5 rows: a horizontal energy bar across the middle, the charging slot on its right. */
    static MenuLayout energyCube(String machine) {
        return new MenuLayout(machine, 5)
                .energyHorizontal(2, 3)
                .slot("charge", 2, 7, MenuLayout.Kind.POWER, "charge", "Charge an Energy Tablet")
                .button("state", display(4, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
    }

    static String display(int slot, String material, String name, String... lore) {
        return "    slot: " + slot + "\n    update: true\n" + item(material, name, lore);
    }

    static String item(String material, String name, String... lore) {
        StringBuilder item = new StringBuilder("    item:\n      material: ").append(material)
                .append("\n      name: \"<!i>").append(name).append("\"\n");
        if (lore.length > 0) {
            item.append("      lore:\n");
            for (String line : lore) item.append("        - \"<!i>").append(line).append("\"\n");
        }
        return item.toString();
    }

    static String yaml(Menu menu) {
        // The window is drawn by the title's images (Mekanized's gui.yml); fill bars redraw by themselves.
        StringBuilder yaml = new StringBuilder()
                .append("# Mekanized ").append(menu.machine()).append(" menu (zMenu). Edit freely; delete to get the default back.\n")
                .append("# Buttons: machineengine_slot (a storage slot), machineengine_upgrades, machineengine_data (a setting),\n")
                .append("# machineengine_action (the machine's actions and lists, e.g. upgrade / upgrades);\n")
                .append("# placeholders %zmenu_machineengine_<key>%. The title's <image:...> and <shift:...> draw the window\n")
                .append("# (Mekanized's gui.yml, docs/MENU_TEXTURES_PLAN.md).\n")
                .append("size: ").append(menu.size()).append("\n")
                .append("name: \"").append(menu.title()).append("\"\n")
                .append("update-interval: 500\n\nitems:\n");
        menu.buttons().forEach((name, button) -> yaml.append("  ").append(name).append(":\n").append(button));
        if (menu.sideSlot() < 0) return yaml.toString();
        yaml.append("  side-config:\n    type: machineengine_menu\n    menu: ").append(SIDE_MENU)
                .append("\n    groups: [items, energy, chemical, fluid, heat]\n    slot: ").append(menu.sideSlot()).append("\n")
                .append(MenuLayout.item("craftengine:mekanized:gui/icon/configuration", "<gold>Side configuration",
                        "<gray>Inputs, outputs and auto-eject per side"));
        return yaml.toString();
    }
}
