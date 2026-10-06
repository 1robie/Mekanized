package fr.robie.machineengine.mekanized.paper.menu;

import fr.robie.machineengine.mekanized.EditableFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.bukkit.plugin.Plugin;
import static fr.robie.machineengine.mekanized.paper.menu.MenuYaml.*;

/** The side configuration menus: one per port group, with tabs between the groups a machine has. */
public final class SideMenus {
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

    private SideMenus() {
    }

    /** The side configuration menu to open for a machine with these port groups, or {@code null}. */
    public static String sideMenuFor(Set<String> groups) {
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

    /** Writes the side menus into {@code folder}; returns how many were installed. */
    static int install(Plugin plugin, EditableFiles files, Path folder) {
        int installed = 0;
        for (int i = 0; i < GROUPS.length; i++) {
            try {
                if (files.install(folder.resolve(SIDE_MENU + "_" + GROUPS[i][0] + ".yml"), sideMenu(i).getBytes(StandardCharsets.UTF_8))) installed++;
            } catch (IOException e) {
                plugin.getLogger().warning("Could not install the side configuration menu: " + e.getMessage());
            }
        }
        return installed;
    }
}
