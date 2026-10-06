package fr.robie.machineengine.mekanized;

import fr.robie.machineengine.mekanized.machine.ChunkLoaders;
import fr.robie.machineengine.mekanized.machine.FactoryBehavior;
import fr.robie.machineengine.mekanized.machine.Liquifier;
import fr.robie.machineengine.mekanized.machine.LogisticsMachines;
import fr.robie.machineengine.mekanized.machine.Radiation;
import fr.robie.machineengine.mekanized.machine.UtilityMachines;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            {"chemical", "GLASS_BOTTLE", "<light_purple>Chemicals"}, {"fluid", "WATER_BUCKET", "<aqua>Fluids"},
            {"heat", "BLAZE_POWDER", "<gold>Heat"}};
    /** The sides laid out like an unfolded cube: left, front, right and back in a row, top above the front, bottom below it. */
    private static final Object[][] CROSS = {{"top", 12}, {"left", 20}, {"front", 21}, {"right", 22}, {"back", 23}, {"bottom", 30}};

    private MekanizedMenus() {
    }

    /** The side configuration menu to open for a machine with these port groups, or {@code null}. */
    static String sideMenuFor(Set<String> groups) {
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

    /**
     * @param sideSlot where the side configuration button goes, -1 for none
     * @param size     the inventory size (rows of 9)
     */
    private record Menu(String machine, Map<String, String> buttons, int sideSlot, int size) {
        Menu(String machine, Map<String, String> buttons) {
            this(machine, buttons, 44);
        }

        Menu(String machine, Map<String, String> buttons, int sideSlot) {
            this(machine, buttons, sideSlot, 45);
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
        endgameMenus(menus);
        utilityMenus(menus);
        reactorMenus(menus);
        automationMenus(menus);
        extrasMenus(menus);
        return menus;
    }

    // -- Phase 6 ---------------------------------------------------------------------------------

    private static void automationMenus(List<Menu> menus) {
        Map<String, String> miner = new LinkedHashMap<>();
        miner.put("info", display(4, "DIAMOND_PICKAXE", "<aqua>Digital Miner", "<gray>Mines below it, within its radius, down to its lowest level:",
                "<gray>the blocks its filters name (put one of the block's item),", "<gray>or every ore without filters. Drops into its output."));
        miner.put("energy-slot", slot(1, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        miner.put("energy", energyDisplay(2));
        miner.put("running", "    type: machineengine_data\n    data: running\n    slot: 5\n    update: true\n"
                + item("LEVER", "<red>Running: <yellow>%zmenu_machineengine_data_running%", "<dark_gray>Click to start or stop"));
        miner.put("radius", setting(6, "radius", List.of("4", "8", "16", "24", "32"), "COMPASS",
                "<yellow>Radius: %zmenu_machineengine_data_radius% blocks"));
        miner.put("min-y", setting(7, "min_y", List.of("-64", "-32", "0", "32", "64"), "LADDER",
                "<yellow>Lowest level: y %zmenu_machineengine_data_min_y%"));
        miner.put("upgrades", upgrades());
        for (int i = 0; i < 9; i++) miner.put("filter-" + (i + 1), slotOf(9 + i, "filter", i + 1, "Filter: the item of a block to mine"));
        miner.put("status", status(22));
        for (int i = 0; i < 27; i++) miner.put("output-" + (i + 1), slotOf(27 + i, "output", i + 1, "Mined"));
        menus.add(new Menu("digital_miner", miner, 26, 54));

        Map<String, String> vibrator = new LinkedHashMap<>();
        vibrator.put("info", display(4, "BELL", "<gold>Seismic Vibrator", "<gray>While it has energy (50 J/t), right-click it",
                "<gray>with a seismic reader to see the ground below"));
        vibrator.put("energy", energyDisplay(13));
        menus.add(new Menu("seismic_vibrator", vibrator));

        Map<String, String> teleporter = new LinkedHashMap<>();
        teleporter.put("info", display(4, "ENDER_PEARL", "<light_purple>Teleporter", "<gray>Frame: 3 wide, 4 high, the teleporter at the bottom middle.",
                "<gray>Walk into it to reach the other teleporter of its frequency.", "<gray>Frequency: look at it, /mekanized frequency <name>"));
        teleporter.put("frequency", display(11, "NAME_TAG", "<yellow>Frequency: %zmenu_machineengine_data_frequency%"));
        teleporter.put("energy", energyDisplay(13));
        teleporter.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("teleporter", teleporter));

        Map<String, String> array = new LinkedHashMap<>();
        array.put("info", display(4, "ENDER_CHEST", "<aqua>QIO Drive Array", "<gray>Its drives set how much it holds.",
                "<gray>Frequency: %zmenu_machineengine_data_frequency%", "<gray>(look at it, /mekanized frequency <name>)"));
        for (int i = 0; i < 12; i++) array.put("drive-" + (i + 1), slotOf((i < 6 ? 10 : 13) + i, "drives", i + 1, "QIO drive"));
        menus.add(new Menu("qio_drive_array", array));

        for (String[] kind : new String[][]{{"qio_importer", "QIO Importer", "Takes from the block in front into the network:",
                "<gray>what its filters name, or everything"}, {"qio_exporter", "QIO Exporter", "Puts into the block in front, from the network,",
                "<gray>what its filters name"}}) {
            Map<String, String> buttons = new LinkedHashMap<>();
            buttons.put("info", display(4, "HOPPER", "<aqua>" + kind[1], "<gray>" + kind[2], kind[3],
                    "<gray>Frequency: %zmenu_machineengine_data_frequency%"));
            for (int i = 0; i < 9; i++) buttons.put("filter-" + (i + 1), slotOf(18 + i, "filter", i + 1, "Filter"));
            menus.add(new Menu(kind[0], buttons));
        }
    }

    private static void reactorMenus(List<Menu> menus) {
        Map<String, String> resistive = new LinkedHashMap<>();
        resistive.put("info", display(4, "MAGMA_BLOCK", "<gold>Resistive Heater", "<gray>Turns energy into heat, given to the blocks",
                "<gray>around it (a boiler valve, conductors)"));
        resistive.put("energy", energyDisplay(11));
        resistive.put("heat", heat(13, "heat", "Heat"));
        resistive.put("rate", setting(15, "heater_rate", List.of("100", "1000", "10000", "100000"), "BLAZE_POWDER",
                "<yellow>Energy use: %zmenu_machineengine_data_heater_rate% J/t"));
        menus.add(new Menu("resistive_heater", resistive));
        Map<String, String> fuelwood = new LinkedHashMap<>();
        fuelwood.put("info", display(4, "CAMPFIRE", "<gold>Fuelwood Heater", "<gray>Burns furnace fuels for heat (400 J/t)"));
        fuelwood.put("fuel", slot(11, "fuel", "Fuel"));
        fuelwood.put("heat", heat(13, "heat", "Heat"));
        fuelwood.put("state", display(15, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("fuelwood_heater", fuelwood));

        Map<String, String> boiler = new LinkedHashMap<>();
        boiler.put("info", display(4, "CAULDRON", "<aqua>Thermoelectric Boiler", "<gray>Heat boils water into steam: heaters on a valve,",
                "<gray>or superheated sodium from a fission reactor.", "<gray>Inside: a layer of pressure dispersers, superheating elements",
                "<gray>Configurator on a valve: input, steam out, sodium out"));
        boiler.put("water", tank(10, "water", "WATER_BUCKET", "<aqua>", "Water"));
        boiler.put("heat", heat(12, "heat", "Heat"));
        boiler.put("steam", chemical(14, "steam", "Steam"));
        boiler.put("coolant", chemical(28, "coolant", "Superheated sodium (in)"));
        boiler.put("cooled", chemical(30, "cooled", "Sodium (out)"));
        boiler.put("status", status(22));
        menus.add(new Menu("boiler_casing", boiler));

        Map<String, String> turbine = new LinkedHashMap<>();
        turbine.put("info", display(4, "IRON_BARS", "<aqua>Industrial Turbine", "<gray>Steam turns the rotors (blades: right-click a rotor),",
                "<gray>coils make energy, vents let steam through,", "<gray>condensers give water back. Valves give energy and water"));
        turbine.put("steam", chemical(11, "steam", "Steam"));
        turbine.put("energy", energyDisplay(13));
        turbine.put("water", tank(15, "water", "WATER_BUCKET", "<aqua>", "Condensed water"));
        turbine.put("status", status(22));
        menus.add(new Menu("turbine_casing", turbine));

        Map<String, String> fission = new LinkedHashMap<>();
        fission.put("info", display(4, "BEACON", "<green>Fission Reactor", "<gray>Burns fissile fuel; water or sodium cools it.",
                "<gray>Too hot: damage, then a SCRAM (it stops).", "<gray>Configurator on a port: input, coolant out, waste out"));
        fission.put("fuel", chemical(10, "fuel", "Fissile fuel"));
        fission.put("waste", chemical(11, "waste", "Nuclear waste"));
        fission.put("water", tank(19, "water", "WATER_BUCKET", "<aqua>", "Water (coolant)"));
        fission.put("steam", chemical(20, "steam", "Steam"));
        fission.put("sodium", chemical(28, "sodium", "Sodium (coolant)"));
        fission.put("hot", chemical(29, "hot", "Superheated sodium"));
        fission.put("running", "    type: machineengine_data\n    data: running\n    slot: 15\n    update: true\n"
                + item("LEVER", "<red>Running: <yellow>%zmenu_machineengine_data_running%", "<dark_gray>Click to start or stop"));
        fission.put("rate", setting(16, "burn_rate", List.of("0.1", "1", "5", "10", "50", "100"), "BLAZE_POWDER",
                "<yellow>Burn rate: %zmenu_machineengine_data_burn_rate% mB/t", "<gray>At most 1 mB/t per fuel assembly"));
        fission.put("status", status(24));
        menus.add(new Menu("fission_reactor_casing", fission));

        Map<String, String> fusion = new LinkedHashMap<>();
        fusion.put("info", display(4, "END_CRYSTAL", "<light_purple>Fusion Reactor", "<gray>Ignite it with lasers into a laser focus matrix",
                "<gray>(400 MJ), with deuterium and tritium inside.", "<gray>Configurator on a port: input, energy out"));
        fusion.put("deuterium", chemical(10, "deuterium", "Deuterium"));
        fusion.put("tritium", chemical(11, "tritium", "Tritium"));
        fusion.put("energy", energyDisplay(13));
        fusion.put("ignition", display(14, "GLOWSTONE_DUST", "<gold>Laser energy",
                "<gray>%zmenu_machineengine_storage_ignition_amount% / %zmenu_machineengine_storage_ignition_capacity% J"));
        fusion.put("rate", setting(16, "injection_rate", List.of("2", "10", "20", "50", "98"), "BLAZE_POWDER",
                "<yellow>Injection rate: %zmenu_machineengine_data_injection_rate% mB/t"));
        fusion.put("status", status(22));
        menus.add(new Menu("fusion_reactor_frame", fusion));

        Map<String, String> sps = new LinkedHashMap<>();
        sps.put("info", display(4, "NETHER_STAR", "<light_purple>Supercritical Phase Shifter", "<gray>Energy turns polonium into antimatter:",
                "<gray>1,000 mB and 1 GJ per mB.", "<gray>Configurator on a port: input, antimatter out"));
        sps.put("polonium", chemical(10, "polonium", "Polonium"));
        sps.put("energy", energyDisplay(12));
        sps.put("antimatter", chemical(14, "antimatter", "Antimatter"));
        sps.put("status", status(22));
        menus.add(new Menu("sps_casing", sps));
    }

    /** A heat buffer gauge. */
    private static void extrasMenus(List<Menu> menus) {
        Map<String, String> stabilizer = new LinkedHashMap<>();
        stabilizer.put("info", display(4, "END_CRYSTAL", "<light_purple>Dimensional Stabilizer", "<gray>Keeps the chunks around it loaded",
                "<gray>while it has energy (" + ChunkLoaders.STABILIZER_ENERGY_PER_CHUNK
                        + " J/t per chunk)."));
        stabilizer.put("radius", setting(11, "radius", List.of("0", "1", "2"), "COMPASS",
                "<yellow>Chunks around: %zmenu_machineengine_data_radius%", "<gray>0: its chunk, 1: 3 x 3, 2: 5 x 5"));
        stabilizer.put("energy", energyDisplay(13));
        stabilizer.put("status", status(15));
        stabilizer.put("energy-slot", slot(31, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        menus.add(new Menu("dimensional_stabilizer", stabilizer));

        Map<String, String> entangloporter = new LinkedHashMap<>();
        entangloporter.put("info", display(4, "ENDER_EYE", "<light_purple>Quantum Entangloporter",
                "<gray>Every entangloporter of a frequency shares one buffer.", "<gray>Sending ones fill it from pipes, cables and hoppers;",
                "<gray>receiving ones empty it out of every side.", "<gray>Frequency: look at it, /mekanized frequency <name>"));
        entangloporter.put("frequency", display(10, "NAME_TAG", "<yellow>Frequency: %zmenu_machineengine_data_frequency%"));
        entangloporter.put("mode", setting(12, "entangloporter_mode", List.of("send", "receive"), "COMPARATOR",
                "<yellow>Mode: %zmenu_machineengine_data_entangloporter_mode%"));
        entangloporter.put("energy", energyDisplay(14));
        entangloporter.put("fluid", tank(15, "fluid", "WATER_BUCKET", "<aqua>", "Fluid"));
        entangloporter.put("chemical", chemical(16, "chemical", "Chemical"));
        entangloporter.put("items", slot(22, "items", "Items"));
        menus.add(new Menu("quantum_entangloporter", entangloporter, -1));

        Map<String, String> barrel = new LinkedHashMap<>();
        barrel.put("info", display(4, "BARREL", "<green>Radioactive Waste Barrel", "<gray>Holds radioactive chemicals safely",
                "<gray>while they slowly decay (" + Radiation.BARREL_DECAY + " mB a second)."));
        barrel.put("waste", chemical(13, "waste", "Waste"));
        menus.add(new Menu("radioactive_waste_barrel", barrel, -1));

        Map<String, String> liquifier = new LinkedHashMap<>();
        liquifier.put("info", display(4, "BREAD", "<gold>Nutritional Liquifier", "<gray>Food into nutritional paste: "
                + Liquifier.PASTE_PER_FOOD + " mB per hunger point.",
                "<gray>Right-click it with a canteen to fill it."));
        liquifier.put("input", slot(10, "input", "Food"));
        liquifier.put("paste", tank(14, "paste", "HONEY_BOTTLE", "<gold>", "Nutritional paste"));
        liquifier.put("energy", energyDisplay(16));
        liquifier.put("energy-slot", slot(34, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        liquifier.put("upgrades", upgrades());
        menus.add(new Menu("nutritional_liquifier", liquifier));
    }

    private static String heat(int slot, String storage, String name) {
        String prefix = "%zmenu_machineengine_storage_" + storage + "_";
        return display(slot, "BLAZE_POWDER", "<gold>" + name, "<gray>" + prefix + "amount% / " + prefix + "capacity% J",
                "<gold>" + prefix + "bar%");
    }

    /** A multiblock's status line ({@code status} data). */
    private static String status(int slot) {
        return display(slot, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_data_status%");
    }

    /** A button cycling a text setting through {@code values}. */
    private static String setting(int slot, String data, List<String> values, String material, String name, String... lore) {
        List<String> all = new ArrayList<>(List.of(lore));
        all.add("<dark_gray>Click to change");
        return "    type: machineengine_data\n    data: " + data + "\n    values: [" + String.join(", ", values) + "]\n    slot: " + slot
                + "\n    update: true\n" + item(material, name, all.toArray(String[]::new));
    }

    private static void utilityMenus(List<Menu> menus) {
        // Personal storage: a double chest's worth, no sides (nothing pipes in or out).
        for (String name : List.of("personal_chest", "personal_barrel")) {
            Map<String, String> buttons = new LinkedHashMap<>();
            for (int i = 0; i < 54; i++) buttons.put("slot-" + (i + 1), slotOf(i, "items", i + 1, ""));
            menus.add(new Menu(name, buttons, -1, 54));
        }
        for (String tier : UtilityMachines.BIN_TIERS) {
            Map<String, String> bin = new LinkedHashMap<>();
            bin.put("info", display(4, "BARREL", "<yellow>Bin", "<gray>One kind of item, many of it.",
                    "<gray>Right-click the block with items to store them,", "<gray>left-click to take a stack (sneak: one)"));
            bin.put("items", slot(22, "items", "Stored"));
            bin.put("amount", display(31, "PAPER", "<gray>%zmenu_machineengine_storage_items_amount% / %zmenu_machineengine_storage_items_capacity%"));
            menus.add(new Menu(tier + "_bin", bin));
        }
        Map<String, String> chargepad = new LinkedHashMap<>();
        chargepad.put("info", display(4, "LIGHT_WEIGHTED_PRESSURE_PLATE", "<yellow>Chargepad", "<gray>Charges the energy items",
                "<gray>of players standing on it"));
        chargepad.put("energy", energyDisplay(22));
        menus.add(new Menu("chargepad", chargepad));

        Map<String, String> plenisher = new LinkedHashMap<>();
        plenisher.put("info", display(4, "WATER_BUCKET", "<aqua>Fluidic Plenisher", "<gray>Places water or lava below it,",
                "<gray>filling the space down there"));
        plenisher.putAll(machine());
        plenisher.remove("progress");
        buckets(plenisher, "fluid", "Fluid", 11);
        plenisher.put("state", display(13, "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("fluidic_plenisher", plenisher));

        Map<String, String> assemblicator = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            assemblicator.put("pattern-" + (i + 1), slotOf(i / 3 * 9 + i % 3, "pattern", i + 1, "Recipe (one item each, like a crafting table)"));
        }
        for (int i = 0; i < 18; i++) assemblicator.put("input-" + (i + 1), slotOf(27 + i % 9 + (i / 9) * 9, "input", i + 1, "Ingredients"));
        for (int i = 0; i < 3; i++) assemblicator.put("output-" + (i + 1), slotOf(14 + i, "output", i + 1, "Output"));
        assemblicator.put("energy", energyDisplay(5));
        assemblicator.put("energy-slot", slot(6, "energy_slot", "Energy item (redstone: 10 kJ each)"));
        assemblicator.put("upgrades", upgrades());
        assemblicator.put("state", display(23, "CRAFTING_TABLE", "<yellow>%zmenu_machineengine_state%"));
        menus.add(new Menu("formulaic_assemblicator", assemblicator, 26, 54));

        Map<String, String> laser = new LinkedHashMap<>();
        laser.put("info", display(4, "END_ROD", "<red>Laser", "<gray>Fires out of its front (5 kJ/t): breaks blocks,",
                "<gray>or feeds a laser amplifier or tractor beam"));
        laser.put("energy", energyDisplay(22));
        laser.put("redstone", redstoneControl(40));
        menus.add(new Menu("laser", laser));
        Map<String, String> amplifier = new LinkedHashMap<>();
        amplifier.put("info", display(4, "END_CRYSTAL", "<red>Laser Amplifier", "<gray>Gathers laser energy and fires it",
                "<gray>out of its front while not powered by redstone"));
        amplifier.put("energy", energyDisplay(22));
        amplifier.put("redstone", redstoneControl(40));
        menus.add(new Menu("laser_amplifier", amplifier));
        Map<String, String> tractor = new LinkedHashMap<>();
        tractor.put("info", display(4, "END_CRYSTAL", "<red>Laser Tractor Beam", "<gray>An amplifier that keeps",
                "<gray>the drops of the blocks it breaks"));
        tractor.put("energy", energyDisplay(0));
        tractor.put("redstone", redstoneControl(40));
        for (int i = 0; i < 27; i++) tractor.put("items-" + (i + 1), slotOf(9 + i, "items", i + 1, "Collected"));
        menus.add(new Menu("laser_tractor_beam", tractor));

        Map<String, String> oredict = new LinkedHashMap<>();
        oredict.put("info", display(4, "NAME_TAG", "<yellow>Oredictionificator", "<gray>Turns ores, ingots, dusts... of other plugins",
                "<gray>into Mekanized's (by their c: tags)"));
        oredict.put("input", slot(20, "input", "Input"));
        oredict.put("output", slot(24, "output", "Output"));
        menus.add(new Menu("oredictionificator", oredict));
    }

    private static void endgameMenus(List<Menu> menus) {
        Map<String, String> reaction = machine();
        reaction.put("input", slot(10, "input", "Item input"));
        reaction.put("chemical", chemical(19, "chemical", "Chemical input"));
        buckets(reaction, "fluid", "Fluid input", 28);
        reaction.put("output", slot(14, "output", "Item output"));
        reaction.put("chemical-output", chemical(23, "chemical_output", "Chemical output"));
        menus.add(new Menu("pressurized_reaction_chamber", reaction));

        Map<String, String> centrifuge = machine();
        centrifuge.put("input", chemical(10, "input", "Input"));
        centrifuge.put("output", chemical(14, "output", "Output"));
        menus.add(new Menu("isotopic_centrifuge", centrifuge));

        Map<String, String> activator = new LinkedHashMap<>();
        activator.put("input", chemical(11, "input", "Input"));
        activator.put("output", chemical(15, "output", "Output"));
        activator.put("state", display(13, "SUNFLOWER", "<yellow>%zmenu_machineengine_state%", "<gray>Works in daylight under open sky"));
        menus.add(new Menu("solar_neutron_activator", activator));

        for (String[] machine : new String[][]{{"antiprotonic_nucleosynthesizer", "Antimatter"}, {"painting_machine", "Pigment"}}) {
            Map<String, String> buttons = machine();
            buttons.put("input", slot(10, "input", "Input"));
            buttons.put("chemical", chemical(19, "chemical", machine[1]));
            buttons.put("output", slot(14, "output", "Output"));
            menus.add(new Menu(machine[0], buttons));
        }

        Map<String, String> extractor = machine();
        extractor.put("input", slot(10, "input", "Input"));
        extractor.put("output", chemical(14, "output", "Pigment"));
        menus.add(new Menu("pigment_extractor", extractor));

        Map<String, String> mixer = machine();
        mixer.put("left", chemical(10, "left", "Left pigment (left side)"));
        mixer.put("right", chemical(11, "right", "Right pigment (right side)"));
        mixer.put("output", chemical(14, "output", "Output (front)"));
        menus.add(new Menu("pigment_mixer", mixer));

        Menu burner = generator("gas_burning_generator", false);
        burner.buttons().put("fuel", chemical(11, "fuel", "Fuel (hydrogen, ethene)"));
        menus.add(burner);
        menus.add(generator("wind_generator", false));
        menus.add(generator("advanced_solar_generator", false));
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
            int lanes = LogisticsMachines.LANES_BY_TIER.get(tier);
            for (var kind : LogisticsMachines.KINDS) {
                menus.add(factory(tier, lanes, kind));
            }
        }
    }

    /** Lanes in columns: input, progress, output (and the sawmill's second outputs); energy and extras on top. */
    private static Menu factory(String tier, int lanes, LogisticsMachines.Kind kind) {
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
            buttons.put("input-" + (lane + 1), slot(9 + column, FactoryBehavior.laneStorage(lane),
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
                "<gray>Installed: %zmenu_machineengine_upgrades%", "<dark_gray>Click with an upgrade to install,",
                "<dark_gray>with an empty hand to take one out");
    }

    /**
     * Input, progress, output(s), energy, energy slot, upgrades; a chemical tank and its slot if any.
     *
     * @param chemical the label of the chemical tank's item slot, or {@code null} for no tank
     */
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

    /** Mekanism's redstone control: ignored, works while powered, works while not powered. */
    private static String redstoneControl(int slot) {
        return "    type: machineengine_data\n    data: redstone\n    values: [ignored, high, low]\n    slot: " + slot + "\n    update: true\n"
                + item("REDSTONE_TORCH", "<red>Redstone control: <yellow>%zmenu_machineengine_data_redstone%",
                "<gray>ignored: always works", "<gray>high: works while powered", "<gray>low: works while not powered",
                "<dark_gray>Click to switch");
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
