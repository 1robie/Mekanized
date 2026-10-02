package fr.robie.machineengine.mekanized;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * zMenu menus of the Mekanized machines, written to {@code plugins/MachineEngine/menus/} as
 * {@code mekanized_<machine>.yml} (MachineEngine picks the menu by that name). Server owners edit them
 * like any zMenu inventory; edited menus are never overwritten. Titles are translated on each
 * player's client.
 */
final class MekanizedMenus {
    private MekanizedMenus() {
    }

    private record Menu(String machine, Map<String, String> buttons) {
    }

    static void install(Plugin plugin, EditableFiles files) {
        Path folder = plugin.getDataFolder().toPath().getParent().resolve("MachineEngine/menus");
        int installed = 0;
        for (Menu menu : menus()) {
            Path file = folder.resolve("mekanized_" + menu.machine() + ".yml");
            try {
                if (files.install(file, yaml(menu).getBytes(StandardCharsets.UTF_8))) installed++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not install the menu " + file.getFileName() + ": " + e.getMessage());
            }
        }
        if (installed > 0) plugin.getLogger().info("Installed " + installed + " Mekanized machine menus in " + folder);
    }

    private static List<Menu> menus() {
        List<Menu> menus = new ArrayList<>();
        for (String machine : List.of("enrichment_chamber", "crusher", "energized_smelter")) menus.add(processing(machine, false, null));
        menus.add(processing("precision_sawmill", true, null));
        Menu combiner = processing("combiner", false, null);
        combiner.buttons().put("extra", slot(28, "extra", "Second input"));
        menus.add(combiner);
        menus.add(processing("osmium_compressor", false, "Osmium source (osmium ingots)"));
        menus.add(processing("purification_chamber", false, "Oxygen source"));
        menus.add(processing("metallurgic_infuser", false, "Infuse source (redstone, coal, diamond dust...)"));
        menus.add(generator("heat_generator", true));
        menus.add(generator("bio_generator", true));
        menus.add(generator("solar_generator", false));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) menus.add(generator(tier + "_energy_cube", false));
        chemistryMenus(menus);
        return menus;
    }

    // -- Phase 3 ---------------------------------------------------------------------------------

    private static void chemistryMenus(List<Menu> menus) {
        Map<String, String> oxidizer = machine();
        oxidizer.put("input", slot(10, "input", "Input"));
        oxidizer.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("chemical_oxidizer", oxidizer));

        Map<String, String> infuser = machine();
        infuser.put("left", chemical(10, "left", "Left input (left side)"));
        infuser.put("right", chemical(11, "right", "Right input (right side)"));
        infuser.put("output", chemical(14, "output", "Output (front)"));
        menus.add(new Menu("chemical_infuser", infuser));

        menus.add(processing("chemical_injection_chamber", false, "Chemical source"));

        Map<String, String> dissolution = machine();
        dissolution.put("input", slot(10, "input", "Input"));
        dissolution.put("chemical", chemical(28, "chemical", "Sulfuric acid"));
        dissolution.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("chemical_dissolution_chamber", dissolution));

        Map<String, String> washer = machine();
        washer.put("input", chemical(10, "input", "Dirty slurry"));
        washer.put("output", chemical(14, "output", "Clean slurry"));
        buckets(washer, "fluid", "Water", 28);
        menus.add(new Menu("chemical_washer", washer));

        Map<String, String> crystallizer = machine();
        crystallizer.put("input", chemical(10, "input", "Input"));
        crystallizer.put("output", slot(14, "output", "Output"));
        menus.add(new Menu("chemical_crystallizer", crystallizer));

        Map<String, String> separator = machine();
        buckets(separator, "input", "Input fluid", 10);
        separator.put("output-1", chemicalSlot(14, "output", 1, "Output"));
        separator.put("output-2", chemicalSlot(15, "output", 2, "Output"));
        menus.add(new Menu("electrolytic_separator", separator));

        Map<String, String> rotary = machine();
        rotary.put("chemical", chemical(10, "chemical", "Chemical (left side)"));
        buckets(rotary, "fluid", "Fluid (right side)", 14);
        rotary.put("mode", "    type: machineengine_data\n    data: mode\n    values:\n      - condensing\n      - decondensing\n"
                + "    slot: 22\n    update: true\n" + item("COMPARATOR", "<yellow>Mode: %zmenu_machineengine_data_mode%",
                "<gray>condensing: chemical to fluid", "<gray>decondensing: fluid to chemical", "<dark_gray>Click to switch"));
        menus.add(new Menu("rotary_condensentrator", rotary));

        Map<String, String> pump = new LinkedHashMap<>();
        buckets(pump, "fluid", "Pumped fluid", 10);
        pump.put("state", display(12, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%",
                "<gray>Pumps water or lava source blocks below it"));
        pump.put("energy", energyDisplay(16));
        pump.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        pump.put("upgrades", upgrades());
        menus.add(new Menu("electric_pump", pump));

        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) {
            Map<String, String> chemicalTank = new LinkedHashMap<>();
            chemicalTank.put("chemical", chemical(13, "chemical", "Stored"));
            chemicalTank.put("chemical-slot", slot(11, "chemical_slot", "Chemical source"));
            chemicalTank.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: front"));
            menus.add(new Menu(tier + "_chemical_tank", chemicalTank));
            Map<String, String> fluidTank = new LinkedHashMap<>();
            buckets(fluidTank, "fluid", "Stored", 13);
            fluidTank.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%", "<gray>Output: bottom"));
            menus.add(new Menu(tier + "_fluid_tank", fluidTank));
        }
    }

    /** Progress, energy, energy slot and upgrades: what every powered chemical machine shows. */
    private static Map<String, String> machine() {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("progress", display(12, "BLAZE_POWDER", "<yellow>Progress",
                "<gray>%zmenu_machineengine_process_bar%", "<gray>%zmenu_machineengine_process_percent% %"));
        buttons.put("energy", energyDisplay(16));
        buttons.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        buttons.put("upgrades", upgrades());
        return buttons;
    }

    /** A fluid tank gauge at {@code slot}, its bucket slot below it and the bucket output to the right of that. */
    private static void buckets(Map<String, String> buttons, String storage, String name, int slot) {
        buttons.put(storage, tank(slot, storage, "WATER_BUCKET", "<aqua>", name));
        buttons.put("bucket-in", slotOf(slot + 9, "bucket_in", 1, "Bucket (fills or empties)"));
        buttons.put("bucket-out", slotOf(slot + 10, "bucket_out", 1, "Buckets out"));
    }

    private static String chemical(int slot, String storage, String name) {
        return tank(slot, storage, "GLASS_BOTTLE", "<light_purple>", name);
    }

    private static String tank(int slot, String storage, String material, String color, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        return display(slot, material, color + name, "<gray>" + prefix + "resource%",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% mB", color + prefix + "bar%");
    }

    private static String chemicalSlot(int slot, String storage, int index, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_slot_" + index + "_";
        return display(slot, "GLASS_BOTTLE", "<light_purple>" + name, "<gray>" + prefix + "resource%",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% mB");
    }

    private static String upgrades() {
        return "    type: machineengine_upgrades\n    slot: 8\n    update: true\n" + item("NETHER_STAR", "<aqua>Upgrades",
                "<gray>Installed: %zmenu_machineengine_upgrades%", "<dark_gray>Click with a speed or energy upgrade to install,",
                "<dark_gray>with an empty hand to take one out");
    }

    /** Input, progress, output(s), energy, energy slot, upgrades; a chemical tank and its slot if any. */
    /** @param chemical the label of the chemical tank's item slot, or {@code null} for no tank */
    private static Menu processing(String machine, boolean secondOutput, String chemical) {
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
            buttons.put("chemical", display(32, "GLASS_BOTTLE", "<light_purple>%zmenu_machineengine_storage_chemical_resource%",
                    "<gray>%zmenu_machineengine_storage_chemical_amount% / %zmenu_machineengine_storage_chemical_capacity% mB",
                    "<light_purple>%zmenu_machineengine_storage_chemical_bar%"));
        }
        return new Menu(machine, buttons);
    }

    /** Generators and energy cubes: the energy buffer, plus a fuel slot for burning generators. */
    private static Menu generator(String machine, boolean fuel) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("energy", energyDisplay(13));
        if (fuel) buttons.put("fuel", slot(11, "fuel", "Fuel"));
        buttons.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        return new Menu(machine, buttons);
    }

    private static String energyDisplay(int slot) {
        return display(slot, "REDSTONE", "<red>Energy",
                "<gray>%zmenu_machineengine_storage_energy_amount% / %zmenu_machineengine_storage_energy_capacity% J",
                "<red>%zmenu_machineengine_storage_energy_bar%");
    }

    private static String slot(int slot, String storage, String name) {
        return slotOf(slot, storage, 1, name);
    }

    private static String slotOf(int slot, String storage, int storageSlot, String name) {
        return "    type: machineengine_slot\n    slot: " + slot + "\n    storage: " + storage + "\n    storage-slot: " + storageSlot
                + "\n    update: true\n" + item("LIGHT_GRAY_STAINED_GLASS_PANE", "<gray>" + name);
    }

    private static String display(int slot, String material, String name, String... lore) {
        return "    slot: " + slot + "\n    update: true\n" + item(material, name, lore);
    }

    private static String item(String material, String name, String... lore) {
        StringBuilder item = new StringBuilder("    item:\n      material: ").append(material)
                .append("\n      name: \"<!i>").append(name).append("\"\n");
        if (lore.length > 0) {
            item.append("      lore:\n");
            for (String line : lore) item.append("        - \"<!i>").append(line).append("\"\n");
        }
        return item.toString();
    }

    private static String yaml(Menu menu) {
        StringBuilder yaml = new StringBuilder()
                .append("# Mekanized ").append(menu.machine()).append(" menu (zMenu). Edit freely; delete to get the default back.\n")
                .append("# Buttons: machineengine_slot (a storage slot), machineengine_upgrades, machineengine_data (a setting);\n")
                .append("# placeholders %zmenu_machineengine_<key>%.\n")
                .append("size: 45\n")
                .append("name: \"<lang:block.mekanized.").append(menu.machine()).append(">\"\n")
                .append("update-interval: 500\n\nitems:\n")
                .append("  background:\n    is-permanent: true\n    item:\n      material: GRAY_STAINED_GLASS_PANE\n      name: \" \"\n")
                .append("    slots:\n      - 0-44\n");
        menu.buttons().forEach((name, button) -> yaml.append("  ").append(name).append(":\n").append(button));
        return yaml.toString();
    }
}
