package fr.robie.machineengine.mekanized.paper.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.SideMenus.SIDE_MENU;

/**
 * The zMenu button DSL: the YAML of each kind of button (slots, gauges, settings) and of a whole menu,
 * and the layouts several machine families share (processing, generator, the common machine buttons).
 */
final class MenuYaml {
    private MenuYaml() {
    }

    /**
     * @param sideSlot where the side configuration button goes, -1 for none
     * @param size     the inventory size (rows of 9)
     */
    record Menu(String machine, Map<String, String> buttons, int sideSlot, int size) {
        Menu(String machine, Map<String, String> buttons) {
            this(machine, buttons, 44);
        }

        Menu(String machine, Map<String, String> buttons, int sideSlot) {
            this(machine, buttons, sideSlot, 45);
        }
    }

    /** A heat buffer gauge. */
    static String heat(int slot, String storage, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        return display(slot, "BLAZE_POWDER", "<gold>" + name, "<gray>" + prefix + "amount% / " + prefix + "capacity% J",
                "<gold>" + prefix + "bar%");
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

    /** Progress, energy, energy slot and upgrades: what every powered chemical machine shows. */
    static Map<String, String> machine() {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("progress", display(12, "BLAZE_POWDER", "<yellow>Progress",
                "<gray>%zmenu_machineengine_process_bar%", "<gray>%zmenu_machineengine_process_percent% %"));
        buttons.put("energy", energyDisplay(16));
        buttons.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        buttons.put("upgrades", upgrades());
        return buttons;
    }

    /** A fluid tank gauge at {@code slot}, its bucket slot below it and the bucket output to the right of that. */
    static void buckets(Map<String, String> buttons, String storage, String name, int slot) {
        buttons.put(storage, tank(slot, storage, "WATER_BUCKET", "<aqua>", name));
        buttons.put("bucket-in", slotOf(slot + 9, "bucket_in", 1, "Bucket (fills or empties)"));
        buttons.put("bucket-out", slotOf(slot + 10, "bucket_out", 1, "Buckets out"));
    }

    static String chemical(int slot, String storage, String name) {
        return tank(slot, storage, "GLASS_BOTTLE", "<light_purple>", name);
    }

    static String tank(int slot, String storage, String material, String color, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        return display(slot, material, color + name, "<gray>" + prefix + "name%",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% mB", color + prefix + "bar%");
    }

    static String chemicalSlot(int slot, String storage, int index, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_slot_" + index + "_";
        return display(slot, "GLASS_BOTTLE", "<light_purple>" + name, "<gray>" + prefix + "name%",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% mB");
    }

    static String upgrades() {
        return "    type: machineengine_upgrades\n    slot: 8\n    update: true\n" + item("NETHER_STAR", "<aqua>Upgrades",
                "<gray>Installed: %zmenu_machineengine_upgrades%", "<dark_gray>Click with an upgrade to install,",
                "<dark_gray>with an empty hand to take one out");
    }

    /**
     * Input, progress, output(s), energy, energy slot, upgrades; a chemical tank and its slot if any.
     *
     * @param chemical the label of the chemical tank's item slot, or {@code null} for no tank
     */
    static Menu processing(String machine, boolean secondOutput, String chemical) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("input", slot(10, "input", "Input"));
        buttons.put("progress", display(12, "BLAZE_POWDER", "<yellow>Progress",
                "<gray>%zmenu_machineengine_process_bar%", "<gray>%zmenu_machineengine_process_percent% %"));
        buttons.put("output", slot(14, "output", "Output"));
        if (secondOutput) buttons.put("output-2", slotOf(15, "output", 2, "Secondary output"));
        buttons.put("energy", energyDisplay(16));
        buttons.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        buttons.put("upgrades", upgrades());
        if (chemical != null) {
            buttons.put("chemical-slot", slot(30, "chemical_slot", chemical));
            buttons.put("chemical", display(32, "GLASS_BOTTLE", "<light_purple>%zmenu_machineengine_storage_chemical_name%",
                    "<gray>%zmenu_machineengine_storage_chemical_amount% / %zmenu_machineengine_storage_chemical_capacity% mB",
                    "<light_purple>%zmenu_machineengine_storage_chemical_bar%"));
        }
        return new Menu(machine, buttons);
    }

    /** Generators and energy cubes: the energy buffer, plus a fuel slot for burning generators. */
    static Menu generator(String machine, boolean fuel) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("energy", energyDisplay(13));
        if (fuel) buttons.put("fuel", slot(11, "fuel", "Fuel"));
        buttons.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        return new Menu(machine, buttons);
    }

    /** Mekanism's redstone control: ignored, works while powered, works while not powered. */
    static String redstoneControl(int slot) {
        return "    type: machineengine_data\n    data: redstone\n    values: [ignored, high, low]\n    slot: " + slot + "\n    update: true\n"
                + item("REDSTONE_TORCH", "<red>Redstone control: <yellow>%zmenu_machineengine_data_redstone%",
                "<gray>ignored: always works", "<gray>high: works while powered", "<gray>low: works while not powered",
                "<dark_gray>Click to switch");
    }

    static String energyDisplay(int slot) {
        return display(slot, "REDSTONE", "<red>Energy",
                "<gray>%zmenu_machineengine_storage_energy_amount% / %zmenu_machineengine_storage_energy_capacity% J",
                "<red>%zmenu_machineengine_storage_energy_bar%");
    }

    static String slot(int slot, String storage, String name) {
        return slotOf(slot, storage, 1, name);
    }

    static String slotOf(int slot, String storage, int storageSlot, String name) {
        return "    type: machineengine_slot\n    slot: " + slot + "\n    storage: " + storage + "\n    storage-slot: " + storageSlot
                + "\n    update: true\n" + item("LIGHT_GRAY_STAINED_GLASS_PANE", "<gray>" + name);
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
        StringBuilder yaml = new StringBuilder()
                .append("# Mekanized ").append(menu.machine()).append(" menu (zMenu). Edit freely; delete to get the default back.\n")
                .append("# Buttons: machineengine_slot (a storage slot), machineengine_upgrades, machineengine_data (a setting);\n")
                .append("# placeholders %zmenu_machineengine_<key>%.\n")
                .append("size: ").append(menu.size()).append("\n")
                .append("name: \"<lang:block.mekanized.").append(menu.machine()).append(">\"\n")
                .append("update-interval: 500\n\nitems:\n")
                .append("  background:\n    is-permanent: true\n    item:\n      material: GRAY_STAINED_GLASS_PANE\n      name: \" \"\n")
                .append("    slots:\n      - 0-").append(menu.size() - 1).append("\n");
        menu.buttons().forEach((name, button) -> yaml.append("  ").append(name).append(":\n").append(button));
        if (menu.sideSlot() < 0) return yaml.toString();
        yaml.append("  side-config:\n    type: machineengine_menu\n    menu: ").append(SIDE_MENU)
                .append("\n    groups: [items, energy, chemical, fluid, heat]\n    slot: ").append(menu.sideSlot()).append("\n")
                .append(item("COMPARATOR", "<gold>Side configuration", "<gray>Inputs, outputs and auto-eject per side"));
        return yaml.toString();
    }
}
