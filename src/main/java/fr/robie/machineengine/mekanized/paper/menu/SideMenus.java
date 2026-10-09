package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.EditableFiles;
import fr.robie.machineengine.mekanized.paper.menu.MenuLayout.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import static fr.robie.machineengine.mekanized.paper.menu.MenuLayout.at;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/**
 * The side configuration menus, Mekanism style: one per port group, with tabs between the groups a
 * machine has, and the sides as colored squares (none gray, input red, output blue, both purple).
 */
public final class SideMenus {
    /**
     * The side configuration menus, one per port group: {@code mekanized_side_config_<group>}. Machine
     * menus and the configurator open the first group the machine has; tabs switch between groups.
     */
    static final String SIDE_MENU = "mekanized_side_config";
    /** Port groups in tab order: id, icon (mekanized:gui/icon/...), name. */
    static final String[][] GROUPS = {
            {"items", "items", "Items"}, {"energy", "energy", "Energy"}, {"chemical", "chemicals", "Chemicals"},
            {"fluid", "fluids", "Fluids"}, {"heat", "heat", "Heat"}};
    /** The sides laid out like an unfolded cube: left, front, right and back in a row, top above the front, bottom below it. */
    private static final Object[][] CROSS = {{"top", 1, 4}, {"left", 2, 3}, {"front", 2, 4}, {"right", 2, 5}, {"back", 2, 6}, {"bottom", 3, 4}};

    private SideMenus() {
    }

    /** The side configuration menu to open for a machine with these port groups, or {@code null}. */
    public static String sideMenuFor(Set<String> groups) {
        for (String[] group : GROUPS) {
            if (groups.contains(group[0])) return SIDE_MENU + "_" + group[0];
        }
        return null;
    }

    /**
     * Back on the top left, the group tabs (the open one on a yellow frame; a machine shows only the
     * groups it has), auto-eject on the top right, the sides in the middle.
     */
    static MenuYaml.Menu sideMenu(int index) {
        String group = GROUPS[index][0];
        MenuLayout layout = new MenuLayout("side_config_" + group, 5)
                .name("Side configuration: " + GROUPS[index][2])
                .noSides()
                .button("back", "    type: machineengine_menu\n    slot: 0\n"
                        + MenuLayout.item("craftengine:mekanized:gui/icon/back", "<gray>Back to the machine"))
                .frame(Kind.EXTRA, 0, 2 + index);
        for (int i = 0; i < GROUPS.length; i++) {
            String tab = GROUPS[i][0];
            layout.button("tab-" + tab, "    type: machineengine_menu\n    menu: " + SIDE_MENU + "_" + tab + "\n    group: " + tab
                    + "\n    slot: " + (2 + i) + "\n" + MenuLayout.item("craftengine:mekanized:gui/icon/" + GROUPS[i][1],
                    (i == index ? "<white>▶ " : "<gray>") + GROUPS[i][2], i == index ? "<gray>Shown" : "<dark_gray>Click to show"));
        }
        layout.button("eject", "    type: machineengine_data\n    data: eject\n    slot: 8\n    update: true\n"
                + MenuLayout.item("craftengine:mekanized:gui/icon/auto_eject", "<gold>Auto-eject: %zmenu_machineengine_data_eject%",
                "<gray>Pushes outputs out of their output sides", "<dark_gray>Click to switch"));
        for (Object[] entry : CROSS) {
            String side = (String) entry[0];
            int row = (Integer) entry[1];
            int column = (Integer) entry[2];
            String label = Character.toUpperCase(side.charAt(0)) + side.substring(1);
            String state = "%zmenu_machineengine_side_" + group + "_" + side;
            layout.frame(Kind.NORMAL, row, column)
                    .button("side-" + side, "    type: machineengine_side\n    side: " + side + "\n    group: " + group
                            + "\n    slot: " + at(row, column) + "\n"
                            + MenuLayout.item("craftengine:mekanized:gui/side/" + state + "_mode%", "<white>" + label + ": <yellow>" + state + "%",
                            "<dark_gray>Click: input, output, both, none"));
        }
        layout.button("legend", display(at(4, 8), "PAPER", "<gray>Sides of the machine", "<dark_gray>Front: the face you placed it facing you",
                "<dark_gray>Left and right as seen from the front", "<red>■ <gray>input  <blue>■ <gray>output  <light_purple>■ <gray>both  <dark_gray>■ none"));
        return layout.build();
    }

    /** Writes the side menus into {@code folder}; returns how many were installed. */
    static int install(Plugin plugin, EditableFiles files, Path folder) {
        int installed = 0;
        for (int i = 0; i < GROUPS.length; i++) {
            try {
                if (files.install(folder.resolve(SIDE_MENU + "_" + GROUPS[i][0] + ".yml"), yaml(sideMenu(i)).getBytes(StandardCharsets.UTF_8))) installed++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not install the side configuration menu: " + e.getMessage());
            }
        }
        return installed;
    }
}
