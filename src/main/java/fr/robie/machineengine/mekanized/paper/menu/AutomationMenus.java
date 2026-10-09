package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.DigitalMinerSettings;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;
import java.util.ArrayList;
import java.util.List;
import static fr.robie.machineengine.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The menus of the automation machines: digital miner, seismic vibrator, teleporter, QIO drive array, importer and exporter. */
final class AutomationMenus {
    private AutomationMenus() {
    }

    /** The radii the miner menu offers: 4 to 32 blocks, up to the configured maximum (which is offered itself). */
    private static List<String> radii() {
        int max = DigitalMinerSettings.maxRadius;
        List<String> radii = new ArrayList<>();
        for (int radius : new int[]{4, 8, 16, 24, 32}) {
            if (radius <= max) radii.add(Integer.toString(radius));
        }
        if (radii.isEmpty() || Integer.parseInt(radii.getLast()) < max) radii.add(Integer.toString(max));
        return radii;
    }

    static void add(List<Menu> menus) {
        // Settings on the top row, filters below, energy and status, then the 27 output slots.
        MenuLayout miner = new MenuLayout("digital_miner", 6)
                .button("info", display(at(0, 0), "DIAMOND_PICKAXE", "<aqua>Digital Miner", "<gray>Mines below it, within its radius, down to its lowest level:",
                        "<gray>the blocks its filters name (put one of the block's item),", "<gray>or every ore without filters. Drops into its output."))
                .button("running", "    type: machineengine_data\n    data: running\n    slot: " + at(0, 2) + "\n    update: true\n"
                        + item("LEVER", "<red>Running: <yellow>%zmenu_machineengine_data_running%", "<dark_gray>Click to start or stop"))
                .button("radius", setting(at(0, 3), "radius", radii(), "COMPASS", "<yellow>Radius: %zmenu_machineengine_data_radius% blocks"))
                .button("min-y", setting(at(0, 4), "min_y", List.of("-64", "-32", "0", "32", "64"), "LADDER",
                        "<yellow>Lowest level: y %zmenu_machineengine_data_min_y%"))
                .upgrades()
                .energyHorizontal(2, 0)
                .energySlot(2, 3)
                .button("status", status(at(2, 5)));
        for (int i = 0; i < 9; i++) miner.slot("filter-" + (i + 1), 1, i, Kind.EXTRA, "filter", i + 1, "Filter: the item of a block to mine");
        for (int i = 0; i < 27; i++) miner.slot("output-" + (i + 1), 3 + i / 9, i % 9, Kind.OUTPUT, "output", i + 1, "Mined");
        menus.add(miner.build());

        menus.add(new MenuLayout("seismic_vibrator", 5)
                .button("info", display(at(0, 4), "BELL", "<gold>Seismic Vibrator", "<gray>While it has energy (50 J/t), right-click it",
                        "<gray>with a seismic reader to see the ground below"))
                .energy(1, 4)
                .build());

        menus.add(new MenuLayout("teleporter", 5)
                .button("info", display(at(0, 4), "ENDER_PEARL", "<light_purple>Teleporter", "<gray>Frame: 3 wide, 4 high, the teleporter at the bottom middle.",
                        "<gray>Walk into it to reach the other teleporter of its frequency.", "<gray>Frequency: look at it, /mekanized frequency <name>"))
                .button("frequency", display(at(2, 2), "NAME_TAG", "<yellow>Frequency: %zmenu_machineengine_data_frequency%"))
                .energy(1, 4)
                .button("state", display(at(2, 6), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_state%"))
                .build());

        MenuLayout array = new MenuLayout("qio_drive_array", 5)
                .button("info", display(at(0, 4), "ENDER_CHEST", "<aqua>QIO Drive Array", "<gray>Its drives set how much it holds.",
                        "<gray>Frequency: %zmenu_machineengine_data_frequency%", "<gray>(look at it, /mekanized frequency <name>)"));
        for (int i = 0; i < 12; i++) array.slot("drive-" + (i + 1), 1 + i / 6, 1 + i % 6, Kind.NORMAL, "drives", i + 1, "QIO drive");
        menus.add(array.build());

        for (String[] kind : new String[][]{{"qio_importer", "QIO Importer", "Takes from the block in front into the network:",
                "<gray>what its filters name, or everything"}, {"qio_exporter", "QIO Exporter", "Puts into the block in front, from the network,",
                "<gray>what its filters name"}}) {
            MenuLayout layout = new MenuLayout(kind[0], 5)
                    .button("info", display(at(0, 4), "HOPPER", "<aqua>" + kind[1], "<gray>" + kind[2], kind[3],
                            "<gray>Frequency: %zmenu_machineengine_data_frequency%"));
            for (int i = 0; i < 9; i++) layout.slot("filter-" + (i + 1), 2, i, Kind.EXTRA, "filter", i + 1, "Filter");
            menus.add(layout.build());
        }
    }
}
