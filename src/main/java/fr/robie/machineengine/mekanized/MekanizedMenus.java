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
    /**
     * The side configuration menus, one per port group: {@code mekanized_side_config_<group>}. Machine
     * menus and the configurator open the first group the machine has; tabs switch between groups.
     */
    static final String SIDE_MENU = "mekanized_side_config";
    /** Port groups in tab order: id, item, name. */
    static final String[][] GROUPS = {
            {"items", "HOPPER", "<yellow>Items"}, {"energy", "REDSTONE", "<red>Energy"},
            {"chemical", "GLASS_BOTTLE", "<light_purple>Chemicals"}, {"fluid", "WATER_BUCKET", "<aqua>Fluids"}};
    /** The sides laid out like an unfolded cube: left, front, right and back in a row, top above the front, bottom below it. */
    private static final Object[][] CROSS = {{"top", 12}, {"left", 20}, {"front", 21}, {"right", 22}, {"back", 23}, {"bottom", 30}};

    private MekanizedMenus() {
    }

    /** The side configuration menu to open for a machine with these port groups, or {@code null}. */
    static String sideMenuFor(java.util.Set<String> groups) {
        for (String[] group : GROUPS) {
            if (groups.contains(group[0])) return SIDE_MENU + "_" + group[0];
        }
        return null;
    }

    private static String sideMenu(int index) {
        String group = GROUPS[index][0];
        StringBuilder yaml = new StringBuilder()
                .append("# Mekanized side configuration of ").append(group).append(" (zMenu). Edit freely; delete to get the default back.\n")
                .append("# machineengine_side: what a side does for a port group (input, output, both, none).\n")
                .append("size: 45\n")
                .append("name: \"<gray>Side configuration: ").append(GROUPS[index][2].replaceAll("<[^>]+>", "")).append("\"\n")
                .append("update-interval: 500\n\nitems:\n")
                .append("  background:\n    is-permanent: true\n    item:\n      material: GRAY_STAINED_GLASS_PANE\n      name: \" \"\n")
                .append("    slots:\n      - 0-44\n");
        yaml.append("  back:\n    type: machineengine_menu\n    slot: 0\n").append(item("ARROW", "<gray>Back to the machine"));
        // Tabs: the groups this machine has; the open one is marked.
        for (int i = 0; i < GROUPS.length; i++) {
            String tab = GROUPS[i][0];
            yaml.append("  tab-").append(tab).append(":\n    type: machineengine_menu\n    menu: ").append(SIDE_MENU).append("_").append(tab)
                    .append("\n    group: ").append(tab).append("\n    slot: ").append(2 + i).append("\n")
                    .append(item(GROUPS[i][1], (i == index ? "<white>▶ " : "") + GROUPS[i][2], i == index ? "<gray>Shown" : "<dark_gray>Click to show"));
        }
        yaml.append("  eject:\n    type: machineengine_data\n    data: eject\n    slot: 8\n    update: true\n")
                .append(item("DISPENSER", "<gold>Auto-eject: %zmenu_machineengine_data_eject%",
                        "<gray>Pushes outputs out of their output sides", "<dark_gray>Click to switch"));
        for (Object[] entry : CROSS) {
            String side = (String) entry[0];
            String label = Character.toUpperCase(side.charAt(0)) + side.substring(1);
            yaml.append("  side-").append(side).append(":\n    type: machineengine_side\n    side: ").append(side)
                    .append("\n    group: ").append(group).append("\n    slot: ").append(entry[1]).append("\n    update: true\n")
                    .append(item(GROUPS[index][1], "<white>" + label + ": <yellow>%zmenu_machineengine_side_" + group + "_" + side + "%",
                            "<dark_gray>Click: input, output, both, none"));
        }
        yaml.append("  legend:\n").append(display(40, "PAPER", "<gray>Sides of the machine", "<dark_gray>Front: the face you placed it facing you",
                "<dark_gray>Left and right as seen from the front"));
        return yaml.toString();
    }

    /** @param sideSlot where the side configuration button goes */
    private record Menu(String machine, Map<String, String> buttons, int sideSlot) {
        Menu(String machine, Map<String, String> buttons) {
            this(machine, buttons, 44);
        }
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
        for (int i = 0; i < GROUPS.length; i++) {
            try {
                if (files.install(folder.resolve(SIDE_MENU + "_" + GROUPS[i][0] + ".yml"), sideMenu(i).getBytes(StandardCharsets.UTF_8))) installed++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not install the side configuration menu: " + e.getMessage());
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
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "creative")) {
            Menu cube = generator(tier + "_energy_cube", false);
            cube.buttons().put("charge", slot(31, "charge", "Charge an Energy Tablet"));
            menus.add(cube);
        }
        chemistryMenus(menus);
        logisticsMenus(menus);
        multiblockMenus(menus);
        return menus;
    }

    // -- Phase 5a --------------------------------------------------------------------------------

    /** The structures' menus, opened from any member: the anchor's storage. */
    private static void multiblockMenus(List<Menu> menus) {
        Map<String, String> tank = new LinkedHashMap<>();
        tank.put("info", display(4, "BUCKET", "<aqua>Dynamic Tank", "<gray>Holds one fluid or one chemical,",
                "<gray>350,000 mB of fluid per block inside"));
        tank.put("chemical", chemical(15, "chemical", "Chemical"));
        buckets(tank, "fluid", "Fluid", 11);
        menus.add(new Menu("dynamic_tank", tank));

        Map<String, String> matrix = new LinkedHashMap<>();
        matrix.put("info", display(4, "BEACON", "<aqua>Induction Matrix", "<gray>Cells store energy, providers set the output rate.",
                "<gray>Configurator on a port: input or output"));
        matrix.put("energy", energyDisplay(22));
        menus.add(new Menu("induction_casing", matrix));

        Map<String, String> tower = new LinkedHashMap<>();
        tower.put("info", display(4, "SUNFLOWER", "<gold>Thermal Evaporation Plant", "<gray>Water to brine, brine to liquid lithium.",
                "<gray>Faster when taller, by day, and when dry"));
        buckets(tower, "input", "Input", 10);
        tower.put("output", tank(16, "output", "WATER_BUCKET", "<aqua>", "Output"));
        tower.put("output-bucket", slotOf(25, "output_bucket", 1, "Empty bucket to fill from the output"));
        tower.put("state", display(13, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("thermal_evaporation_block", tower));
    }

    // -- Phase 4 ---------------------------------------------------------------------------------

    private static void logisticsMenus(List<Menu> menus) {
        Map<String, String> sorter = new LinkedHashMap<>();
        sorter.put("info", display(4, "HOPPER", "<yellow>Logistical sorter", "<gray>Pulls from the block behind it,",
                "<gray>pushes out of its front.", "<gray>Only items like the filters pass (none: all)."));
        for (int i = 1; i <= 9; i++) sorter.put("filter-" + i, slotOf(8 + i, "filter", i, "Filter " + i));
        sorter.put("buffer", slot(22, "buffer", "In transit"));
        menus.add(new Menu("logistical_sorter", sorter));

        for (String tier : List.of("basic", "advanced", "elite", "ultimate")) {
            int lanes = fr.robie.machineengine.mekanized.machine.LogisticsMachines.LANES_BY_TIER.get(tier);
            for (var kind : fr.robie.machineengine.mekanized.machine.LogisticsMachines.KINDS) {
                menus.add(factory(tier, lanes, kind));
            }
        }
    }

    /** Lanes in columns: input, progress, output (and the sawmill's second outputs); energy and extras on top. */
    private static Menu factory(String tier, int lanes, fr.robie.machineengine.mekanized.machine.LogisticsMachines.Kind kind) {
        Map<String, String> buttons = new LinkedHashMap<>();
        buttons.put("energy-slot", slot(0, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        buttons.put("energy", energyDisplay(1));
        if (kind.chemicalTank() > 0) {
            buttons.put("chemical-slot", slot(3, "chemical_slot", kind.chemicalIsInput() ? "Infuse source" : "Chemical source"));
            buttons.put("chemical", chemical(4, "chemical", kind.chemicalIsInput() ? "Infuse type" : "Chemical"));
        }
        if (kind.extraSlot()) buttons.put("extra", slot(6, "extra", "Second input (every lane)"));
        buttons.put("upgrades", upgrades());
        int first = (9 - lanes) / 2;
        for (int lane = 0; lane < lanes; lane++) {
            int column = first + lane;
            buttons.put("input-" + (lane + 1), slot(9 + column, fr.robie.machineengine.mekanized.machine.FactoryBehavior.laneStorage(lane),
                    "Lane " + (lane + 1)));
            buttons.put("progress-" + (lane + 1), display(18 + column, "BLAZE_POWDER", "<yellow>Lane " + (lane + 1),
                    "<gray>%zmenu_machineengine_data_lane_" + (lane + 1) + "% %"));
            buttons.put("output-" + (lane + 1), slotOf(27 + column, "output", lane + 1, "Output"));
            if (kind.outputsPerLane() > 1) {
                buttons.put("output-" + (lanes + lane + 1), slotOf(36 + column, "output", lanes + lane + 1, "Secondary output"));
            }
        }
        return new Menu(tier + "_" + kind.name() + "_factory", buttons, kind.outputsPerLane() > 1 ? 7 : 44);
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
        return display(slot, material, color + name, "<gray>" + prefix + "name%",
                "<gray>" + prefix + "amount% / " + prefix + "capacity% mB", color + prefix + "bar%");
    }

    private static String chemicalSlot(int slot, String storage, int index, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_slot_" + index + "_";
        return display(slot, "GLASS_BOTTLE", "<light_purple>" + name, "<gray>" + prefix + "name%",
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
            buttons.put("chemical", display(32, "GLASS_BOTTLE", "<light_purple>%zmenu_machineengine_storage_chemical_name%",
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
        yaml.append("  side-config:\n    type: machineengine_menu\n    menu: ").append(SIDE_MENU)
                .append("\n    groups: [items, energy, chemical, fluid]\n    slot: ").append(menu.sideSlot()).append("\n")
                .append(item("COMPARATOR", "<gold>Side configuration", "<gray>Inputs, outputs and auto-eject per side"));
        return yaml.toString();
    }
}
