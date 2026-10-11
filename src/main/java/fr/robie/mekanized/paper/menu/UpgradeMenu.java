package fr.robie.mekanized.paper.menu;

import fr.robie.mekanized.machine.MekanizedRegistry;
import fr.robie.mekanized.paper.menu.MenuLayout.Kind;

import static fr.robie.mekanized.paper.menu.MenuLayout.at;

/**
 * The upgrades menu, Mekanism's upgrade screen (D40): one menu for every machine, opened from the upgrade
 * tab. Its rows are the machine's {@code upgrades} list (what it accepts, then anything installed it no
 * longer accepts), so each machine shows its own; rows past the end show an empty upgrade.
 */
final class UpgradeMenu {
    /** The menu file name ({@code mekanized_upgrades.yml}), which the upgrade tabs open. */
    static final String NAME = "mekanized_upgrades";
    /** Row positions: every registered upgrade could show (a machine shows 3 or 4, the rest show an empty upgrade). */
    static final int ROWS = MekanizedRegistry.UPGRADES.size();
    private static final int ROW = 2;
    private static final int FIRST_COLUMN = 1;

    private UpgradeMenu() {
    }

    /**
     * Back on the top left, the upgrade tab marked on the top right, the upgrades across row 2 (their own
     * item, name and count), and the input slot below them.
     */
    static MenuYaml.Menu menu() {
        MenuLayout layout = new MenuLayout("upgrades", 5)
                .name("Upgrades")
                .noSides()
                .button("back", "    type: machineengine_menu\n    slot: 0\n"
                        + MenuLayout.item("craftengine:mekanized:gui/icon/back", "<gray>Back to the machine"))
                .frame(Kind.EXTRA, 0, MenuLayout.UPGRADES)
                .button("tab", "    slot: " + MenuLayout.UPGRADES + "\n" + MenuLayout.item("craftengine:mekanized:gui/icon/upgrade",
                        "<white>▶ Upgrades", "<gray>Installed: %zmenu_machineengine_upgrades%"));
        for (int i = 0; i < ROWS; i++) {
            String row = "%zmenu_machineengine_list_upgrades_" + i + "_";
            layout.frame(Kind.NORMAL, ROW, FIRST_COLUMN + i)
                    .button("upgrade-" + i, "    type: machineengine_action\n    action: upgrade\n    list: upgrades\n    index: " + i
                            + "\n    slot: " + at(ROW, FIRST_COLUMN + i) + "\n"
                            + MenuLayout.item("craftengine:" + row + "item%",
                            "<white><lang:item." + row + "namespace%." + row + "value%> <gray>" + row + "count%/" + row + "max%",
                            "<dark_gray>Click: take one out", "<dark_gray>Shift-click: take them all out",
                            "<dark_gray>Click with upgrades: install them")
                            + MenuLayout.empty("craftengine:mekanized:gui/ghost_upgrade", "<gray>No upgrade",
                            "<dark_gray>This machine takes no more kinds"));
        }
        layout.frame(Kind.INPUT, 4, 4)
                // No list row 99: an empty-handed click takes nothing out.
                .button("input", "    type: machineengine_action\n    action: upgrade\n    input: true\n    index: 99\n    slot: " + at(4, 4)
                        + "\n" + MenuLayout.item("craftengine:mekanized:gui/empty", "<gray>Put upgrades here",
                        "<dark_gray>Click with upgrades (right-click: one),", "<dark_gray>or shift-click them in your inventory"));
        return layout.build();
    }
}
