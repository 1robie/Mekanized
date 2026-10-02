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
        return menus;
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
        buttons.put("upgrades", "    type: machineengine_upgrades\n    slot: 8\n    update: true\n" + item("NETHER_STAR", "<aqua>Upgrades",
                "<gray>Installed: %zmenu_machineengine_upgrades%", "<dark_gray>Click with a speed or energy upgrade to install,",
                "<dark_gray>with an empty hand to take one out"));
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
                .append("# Buttons: machineengine_slot (a storage slot), machineengine_upgrades; placeholders %zmenu_machineengine_<key>%.\n")
                .append("size: 45\n")
                .append("name: \"<lang:block.mekanized.").append(menu.machine()).append(">\"\n")
                .append("update-interval: 500\n\nitems:\n")
                .append("  background:\n    is-permanent: true\n    item:\n      material: GRAY_STAINED_GLASS_PANE\n      name: \" \"\n")
                .append("    slots:\n      - 0-44\n");
        menu.buttons().forEach((name, button) -> yaml.append("  ").append(name).append(":\n").append(button));
        return yaml.toString();
    }
}
