package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the automation machines: digital miner, seismic vibrator, teleporter, QIO drive array, importer and exporter. */
final class AutomationMenus {
    private AutomationMenus() {
    }

    static void add(List<Menu> menus) {
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
}
