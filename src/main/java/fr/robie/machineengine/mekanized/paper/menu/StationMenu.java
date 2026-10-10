package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.machine.ModificationStation;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import fr.robie.machineengine.mekanized.paper.menu.MenuYaml.Menu;

import java.util.List;

import static fr.robie.machineengine.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.display;

/**
 * The modification station's menu (D40): the module slot above the install arrow and the gear slot, the
 * gear's modules in the middle (one button per row of the station's {@code modules} list), energy on the right.
 */
final class StationMenu {
    /** Module rows: 3 rows of 5, more than any piece takes. */
    static final int ROWS = 15;
    static final int FIRST_ROW = 1;
    static final int FIRST_COLUMN = 3;
    static final int COLUMNS = 5;

    private StationMenu() {
    }

    static void add(List<Menu> menus) {
        menus.add(menu());
    }

    static Menu menu() {
        MenuLayout layout = new MenuLayout("modification_station", 5)
                .slot("module", 1, 1, Kind.INPUT, "module", "Module to install")
                .lane("progress", 2, 1, "progress", "Installing")
                .slot("gear", 3, 1, Kind.NORMAL, "gear", "MekaSuit piece or Meka-Tool")
                .button("status", display(at(4, 1), "REDSTONE_TORCH", "<yellow>%zmenu_machineengine_data_status%"))
                .energy(1, 8)
                .energySlot(4, 8);
        for (int i = 0; i < ROWS; i++) {
            int row = FIRST_ROW + i / COLUMNS;
            int column = FIRST_COLUMN + i % COLUMNS;
            String field = "%zmenu_machineengine_list_" + ModificationStation.LIST + "_" + i + "_";
            layout.frame(Kind.NORMAL, row, column)
                    .button("module-" + i, "    type: machineengine_action\n    action: " + ModificationStation.ACTION
                            + "\n    list: " + ModificationStation.LIST + "\n    index: " + i + "\n    slot: " + at(row, column) + "\n"
                            + MenuLayout.item("craftengine:mekanized:module_" + field + "name%",
                            "<white><lang:item.mekanized.module_" + field + "name%> <gray>" + field + "level%/" + field + "max% "
                                    + field + "state%",
                            "<dark_gray>Click: take one level out", "<dark_gray>Shift-click: take it all out",
                            "<dark_gray>Right-click: switch on or off")
                            + MenuLayout.empty("craftengine:mekanized:gui/ghost_module", "<gray>No module",
                            "<dark_gray>Put gear and a module in on the left", "<dark_gray>to install it"));
        }
        return layout.build();
    }
}
